"""
trainer_merged.py

Merges two independent datasets into one training set:
  - HDF5 (Carlos2019b): Pothole / Speed_bump / Metal_bumps / Ditch / Manhole_cover
    -> mapped to 3 classes: pothole (0), speed_bump (1), uneven_road (2)
  - Kaggle continuous drive logs: pothole timestamps + everything else
    -> pothole windows added to class 0, "not pothole" windows become a NEW
       class: normal (3) -- genuine smooth/ordinary driving, which the HDF5
       dataset never contained at all.

This directly targets the generalization failure found in
test_on_kaggle_dataset.py: the model over-flagged ordinary driving as
"pothole" partly because it had never been shown an actual example of
ordinary driving during training.

Evaluation uses GroupKFold cross-validation, same as trainer.py, so no
event/trip's samples ever appear on both sides of a fold. Kaggle windows are
grouped by trip (5 trips = 5 groups), HDF5 windows by their original event id.
Because of this, filtering the out-of-fold predictions down to
source == "kaggle" gives a LEAK-FREE re-check of generalization on exactly
the same kind of data test_on_kaggle_dataset.py used -- but now some of it
also informed training, and we can see whether that helped.

Usage:
    python trainer_merged.py
"""

import glob
import json
import os

import h5py
import joblib
import numpy as np
import pandas as pd

from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report, confusion_matrix
from sklearn.model_selection import GroupKFold, cross_val_predict, cross_val_score

from trainer import extract_features  # exact same feature extraction, no drift


# ============================================================
# CONFIG
# ============================================================

HDF5_PATH = "data/raw/pothole_depth.hdf5"
KAGGLE_DIR = "data/raw/Pothole/"
MODEL_DIR = "models_merged"

N_FOLDS = 5
RANDOM_STATE = 42
EXCLUDE_JERK_FEATURES = False  # tested: excluding jerk didn't help cross-domain generalization, only cost accuracy elsewhere

HALF_WINDOW_SECONDS = 1.0
NEGATIVE_STRIDE_SECONDS = 2.0
NEGATIVE_BUFFER_SECONDS = 1.5

HDF5_LABEL_MAP = {
    "Pothole": 0,
    "Speed_bump": 1,
    "Metal_bumps": 2,
    "Ditch": 2,
    "Manhole_cover": 2,
}

CLASS_NAMES = ["pothole", "speed_bump", "uneven_road", "normal"]


# ============================================================
# LOAD HDF5 PORTION (same logic as trainer.py, label map only differs
# in that it now sits inside a 4-class scheme instead of 3)
# ============================================================

def load_hdf5_portion():
    rows = []

    with h5py.File(HDF5_PATH, "r") as h5:
        samples_group = h5["samples"]
        print(f"[hdf5] Found {len(samples_group)} samples")

        for sample_id in samples_group:
            sample = samples_group[sample_id]

            sample_type = sample.attrs["type"]
            event_id = sample.attrs["event"]

            if isinstance(sample_type, bytes):
                sample_type = sample_type.decode()
            if isinstance(event_id, bytes):
                event_id = event_id.decode()

            if sample_type not in HDF5_LABEL_MAP:
                continue

            if "acc_x" not in sample or "acc_y" not in sample or "acc_z" not in sample:
                continue

            feats = extract_features(
                sample["acc_x"][:],
                sample["acc_y"][:],
                sample["acc_z"][:],
            )
            if not feats:
                continue

            feats["sample_id"] = f"hdf5_{sample_id}"
            feats["event"] = f"hdf5_{event_id}"
            feats["type"] = sample_type
            feats["label"] = HDF5_LABEL_MAP[sample_type]
            feats["source"] = "hdf5"

            rows.append(feats)

    df = pd.DataFrame(rows)
    print(f"[hdf5] {len(df)} samples loaded")
    return df


# ============================================================
# LOAD KAGGLE PORTION (windowing, same approach as
# test_on_kaggle_dataset.py, but now producing TRAINING rows)
# ============================================================

def extract_window_features(sensors_df, center_time, half_window):
    mask = (
        (sensors_df["timestamp"] >= center_time - half_window)
        & (sensors_df["timestamp"] <= center_time + half_window)
    )
    window = sensors_df[mask]

    if len(window) < 3:
        return None

    return extract_features(
        window["accelerometerX"].values,
        window["accelerometerY"].values,
        window["accelerometerZ"].values,
    )


def build_kaggle_windows(trip_name, sensors_path, potholes_path):
    sensors = pd.read_csv(sensors_path)
    potholes = pd.read_csv(potholes_path)

    rows = []
    event_id = f"kaggle_{trip_name}"  # whole trip = one group, to prevent leakage

    for i, t in enumerate(potholes["timestamp"]):
        feats = extract_window_features(sensors, t, HALF_WINDOW_SECONDS)
        if feats is None:
            continue
        feats["sample_id"] = f"{event_id}_pothole_{i}"
        feats["event"] = event_id
        feats["type"] = "Pothole"
        feats["label"] = 0
        feats["source"] = "kaggle"
        rows.append(feats)

    pothole_times = potholes["timestamp"].values
    t_start = sensors["timestamp"].min()
    t_end = sensors["timestamp"].max()

    t = t_start
    i = 0
    while t <= t_end:
        near_pothole = np.any(np.abs(pothole_times - t) < NEGATIVE_BUFFER_SECONDS)
        if not near_pothole:
            feats = extract_window_features(sensors, t, HALF_WINDOW_SECONDS)
            if feats is not None:
                feats["sample_id"] = f"{event_id}_normal_{i}"
                feats["event"] = event_id
                feats["type"] = "Normal"
                feats["label"] = 3
                feats["source"] = "kaggle"
                rows.append(feats)
        t += NEGATIVE_STRIDE_SECONDS
        i += 1

    return rows


def load_kaggle_portion():
    sensor_files = sorted(glob.glob(os.path.join(KAGGLE_DIR, "trip*_sensors.csv")))

    if not sensor_files:
        raise FileNotFoundError(
            f"No 'trip*_sensors.csv' files found under KAGGLE_DIR='{KAGGLE_DIR}' "
            f"(resolved to '{os.path.abspath(KAGGLE_DIR)}'). "
            f"Update KAGGLE_DIR at the top of this script to point to the folder "
            f"containing trip1_sensors.csv, trip1_potholes.csv, etc."
        )

    all_rows = []
    for sensors_path in sensor_files:
        trip_name = os.path.basename(sensors_path).replace("_sensors.csv", "")
        potholes_path = os.path.join(KAGGLE_DIR, f"{trip_name}_potholes.csv")
        rows = build_kaggle_windows(trip_name, sensors_path, potholes_path)
        all_rows.extend(rows)

    df = pd.DataFrame(all_rows)
    print(f"[kaggle] {len(df)} windows loaded "
          f"({(df['label'] == 0).sum()} pothole, {(df['label'] == 3).sum()} normal)")
    return df


# ============================================================
# MAIN
# ============================================================

def main():
    print("=" * 60)
    print("LOADING BOTH DATASETS")
    print("=" * 60)

    df_hdf5 = load_hdf5_portion()
    df_kaggle = load_kaggle_portion()

    df = pd.concat([df_hdf5, df_kaggle], ignore_index=True, sort=False)
    df = df.sort_values("sample_id").reset_index(drop=True)

    print(f"\nCombined dataset: {len(df)} rows")
    print("\nClass counts:")
    print(df["label"].map(dict(enumerate(CLASS_NAMES))).value_counts())
    print("\nBy source:")
    print(df["source"].value_counts())

    excluded_columns = ["sample_id", "event", "type", "label", "source"]

    if EXCLUDE_JERK_FEATURES:
        excluded_columns += [c for c in df.columns if c.startswith("jerk_")]
        print("\n[experiment] Excluding jerk_* features (testing sample-rate hypothesis)")

    feature_cols = [c for c in df.columns if c not in excluded_columns]
    print(f"\nNumber of features: {len(feature_cols)}")

    X = df[feature_cols].replace([np.inf, -np.inf], np.nan).fillna(0)
    y = df["label"]
    groups = df["event"]

    print("\n" + "=" * 60)
    print(f"{N_FOLDS}-FOLD GROUPED CROSS-VALIDATION (hdf5 events + kaggle trips)")
    print("=" * 60)

    model = RandomForestClassifier(
        n_estimators=300,
        class_weight="balanced",
        random_state=RANDOM_STATE,
        n_jobs=-1,
    )

    cv = GroupKFold(n_splits=N_FOLDS)

    fold_scores = cross_val_score(model, X, y, groups=groups, cv=cv, scoring="accuracy", n_jobs=1)
    print("\nPer-fold accuracy:", [round(s, 3) for s in fold_scores])
    print(f"Mean accuracy: {fold_scores.mean():.3f} (+/- {fold_scores.std():.3f})")

    oof_preds = cross_val_predict(model, X, y, groups=groups, cv=cv, n_jobs=1)

    print("\n=== Classification report (out-of-fold, ALL samples) ===")
    print(classification_report(y, oof_preds, labels=[0, 1, 2, 3],
                                 target_names=CLASS_NAMES, zero_division=0))

    print("=== Confusion matrix (out-of-fold, ALL samples) ===")
    cm = confusion_matrix(y, oof_preds, labels=[0, 1, 2, 3])
    print(pd.DataFrame(cm, index=[f"Actual {c}" for c in CLASS_NAMES],
                        columns=[f"Pred {c}" for c in CLASS_NAMES]))

    # --------------------------------------------------------
    # The actual generalization re-check: same computation, but
    # filtered to ONLY the Kaggle-origin rows. Since these predictions
    # are out-of-fold, no Kaggle trip's own data informed the model
    # that predicted it -- this is a fair comparison to the
    # standalone test_on_kaggle_dataset.py numbers from before merging.
    # --------------------------------------------------------

    df_with_preds = df.copy()
    df_with_preds["pred"] = oof_preds

    kaggle_mask = df_with_preds["source"] == "kaggle"
    y_kaggle = df_with_preds.loc[kaggle_mask, "label"]
    preds_kaggle = df_with_preds.loc[kaggle_mask, "pred"]

    print("\n" + "=" * 60)
    print("GENERALIZATION RE-CHECK: Kaggle-origin rows only (out-of-fold)")
    print("=" * 60)
    print(classification_report(y_kaggle, preds_kaggle, labels=[0, 1, 2, 3],
                                 target_names=CLASS_NAMES, zero_division=0))

    pothole_mask = y_kaggle == 0
    normal_mask = y_kaggle == 3

    recall = (preds_kaggle[pothole_mask] == 0).mean()
    false_positive_rate = (preds_kaggle[normal_mask] == 0).mean()

    print(f"Pothole recall on Kaggle data: {recall:.2%}")
    print(f"False positive rate on Kaggle 'normal' windows: {false_positive_rate:.2%}")
    print("(compare directly against the pre-merge numbers: 85.42% recall, 37.47% FP rate)")

    # --------------------------------------------------------
    # Final model on all data + save artifacts
    # --------------------------------------------------------

    model.fit(X, y)

    os.makedirs(MODEL_DIR, exist_ok=True)
    joblib.dump(model, os.path.join(MODEL_DIR, "model.joblib"))
    with open(os.path.join(MODEL_DIR, "feature_cols.json"), "w") as f:
        json.dump(feature_cols, f, indent=2)
    with open(os.path.join(MODEL_DIR, "label_map.json"), "w") as f:
        json.dump({"label_to_name": dict(enumerate(CLASS_NAMES))}, f, indent=2)

    print(f"\nSaved merged model artifacts -> {MODEL_DIR}/")


if __name__ == "__main__":
    main()
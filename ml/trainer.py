"""
trainer.py

Raw HDF5 -> feature extraction -> 3-class Random Forest, evaluated with
grouped cross-validation.

Classes:
    Pothole       -> 0
    Speed_bump    -> 1
    Everything else -> 2 (Uneven Road)

The dataset is split by EVENT (via GroupKFold) to prevent data leakage:
samples from the same recorded event never appear in both the training
and evaluation side of any fold.

Sample order is sorted deterministically after loading, and cross-validation
(rather than one train/test split) is used for evaluation, so the reported
numbers are stable and reproducible across machines/runs instead of
depending on incidental row order or a single lucky/unlucky split.

Usage:
    python trainer.py
"""

import os
import json
import h5py
import numpy as np
import pandas as pd
import joblib

from sklearn.model_selection import GroupKFold, cross_val_predict, cross_val_score
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report, confusion_matrix


# ============================================================
# CONFIG
# ============================================================

HDF5_PATH = "data/raw/pothole_depth.hdf5"
MODEL_DIR = "models"

N_FOLDS = 5
RANDOM_STATE = 42


# ============================================================
# LABEL MAPPING
# ============================================================

LABEL_MAP = {
    "Pothole": 0,
    "Speed_bump": 1,
    "Metal_bumps": 2,
    "Ditch": 2,
    "Manhole_cover": 2
}

CLASS_NAMES = [
    "pothole",
    "speed_bump",
    "uneven_road"
]


# ============================================================
# FEATURE EXTRACTION
# ============================================================

def extract_statistics(values, prefix):

    values = np.asarray(values, dtype=float)

    values = values[np.isfinite(values)]

    if len(values) == 0:
        return {}

    mean = np.mean(values)
    std = np.std(values)
    minimum = np.min(values)
    maximum = np.max(values)
    median = np.median(values)

    q1 = np.percentile(values, 25)
    q3 = np.percentile(values, 75)

    rms = np.sqrt(np.mean(values ** 2))

    features = {
        f"{prefix}_mean": mean,
        f"{prefix}_std": std,
        f"{prefix}_min": minimum,
        f"{prefix}_max": maximum,
        f"{prefix}_median": median,
        f"{prefix}_ptp": maximum - minimum,
        f"{prefix}_iqr": q3 - q1,
        f"{prefix}_rms": rms,
        f"{prefix}_energy": np.sum(values ** 2)
    }

    # Skewness
    if std > 0:
        features[f"{prefix}_skew"] = np.mean(
            ((values - mean) / std) ** 3
        )

        features[f"{prefix}_kurtosis"] = np.mean(
            ((values - mean) / std) ** 4
        ) - 3

    else:
        features[f"{prefix}_skew"] = 0
        features[f"{prefix}_kurtosis"] = 0

    return features


def extract_features(acc_x, acc_y, acc_z):

    acc_x = np.asarray(acc_x, dtype=float)
    acc_y = np.asarray(acc_y, dtype=float)
    acc_z = np.asarray(acc_z, dtype=float)

    # Make sure all axes have the same length
    n = min(
        len(acc_x),
        len(acc_y),
        len(acc_z)
    )

    acc_x = acc_x[:n]
    acc_y = acc_y[:n]
    acc_z = acc_z[:n]

    # Remove invalid rows
    valid = (
        np.isfinite(acc_x)
        & np.isfinite(acc_y)
        & np.isfinite(acc_z)
    )

    acc_x = acc_x[valid]
    acc_y = acc_y[valid]
    acc_z = acc_z[valid]

    if len(acc_x) == 0:
        return {}

    features = {}

    # --------------------------------------------------------
    # X / Y / Z statistics
    # --------------------------------------------------------

    features.update(
        extract_statistics(
            acc_x,
            "acc_x"
        )
    )

    features.update(
        extract_statistics(
            acc_y,
            "acc_y"
        )
    )

    features.update(
        extract_statistics(
            acc_z,
            "acc_z"
        )
    )

    # --------------------------------------------------------
    # Acceleration magnitude
    # --------------------------------------------------------

    magnitude = np.sqrt(
        acc_x ** 2
        + acc_y ** 2
        + acc_z ** 2
    )

    features.update(
        extract_statistics(
            magnitude,
            "acc_mag"
        )
    )

    # --------------------------------------------------------
    # Jerk
    #
    # We use sample-to-sample acceleration difference.
    # Since exact sampling frequency is not consistently
    # available, this is "sample jerk", not physical m/s^3.
    # --------------------------------------------------------

    jerk_x = np.diff(acc_x)
    jerk_y = np.diff(acc_y)
    jerk_z = np.diff(acc_z)

    jerk_mag = np.sqrt(
        jerk_x ** 2
        + jerk_y ** 2
        + jerk_z ** 2
    )

    features.update(
        extract_statistics(
            jerk_x,
            "jerk_x"
        )
    )

    features.update(
        extract_statistics(
            jerk_y,
            "jerk_y"
        )
    )

    features.update(
        extract_statistics(
            jerk_z,
            "jerk_z"
        )
    )

    features.update(
        extract_statistics(
            jerk_mag,
            "jerk_mag"
        )
    )

    # --------------------------------------------------------
    # Peak features
    #
    # No scipy dependency yet.
    # We use a simple threshold-based peak count.
    # --------------------------------------------------------

    magnitude_mean = np.mean(magnitude)
    magnitude_std = np.std(magnitude)

    peak_threshold = (
        magnitude_mean
        + 2 * magnitude_std
    )

    peak_count = np.sum(
        magnitude > peak_threshold
    )

    features["peak_count"] = peak_count

    if len(magnitude) > 0:

        features["max_abs_magnitude"] = np.max(
            np.abs(magnitude)
        )

    else:

        features["max_abs_magnitude"] = 0


    # --------------------------------------------------------
    # Zero crossing / sign-change features
    # --------------------------------------------------------

    def sign_changes(values):

        if len(values) < 2:
            return 0

        signs = np.sign(values)

        return np.sum(
            signs[1:] != signs[:-1]
        )

    features["acc_x_sign_changes"] = sign_changes(
        acc_x - np.mean(acc_x)
    )

    features["acc_y_sign_changes"] = sign_changes(
        acc_y - np.mean(acc_y)
    )

    features["acc_z_sign_changes"] = sign_changes(
        acc_z - np.mean(acc_z)
    )


    return features


# ============================================================
# LOAD HDF5 DATASET
# ============================================================

def load_dataset():

    rows = []

    print("=" * 60)
    print("LOADING RAW HDF5 DATASET")
    print("=" * 60)

    with h5py.File(HDF5_PATH, "r") as h5:

        samples_group = h5["samples"]

        print(
            f"Found {len(samples_group)} samples"
        )

        for sample_id in samples_group:

            sample = samples_group[sample_id]

            # ------------------------------------------------
            # Read attributes
            # ------------------------------------------------

            sample_type = sample.attrs["type"]
            event_id = sample.attrs["event"]

            # Convert bytes -> string when necessary
            if isinstance(sample_type, bytes):
                sample_type = sample_type.decode()

            if isinstance(event_id, bytes):
                event_id = event_id.decode()

            # ------------------------------------------------
            # Ignore anything outside our label map
            # ------------------------------------------------

            if sample_type not in LABEL_MAP:
                continue

            # ------------------------------------------------
            # Read acceleration
            # ------------------------------------------------

            if "acc_x" not in sample:
                continue

            if "acc_y" not in sample:
                continue

            if "acc_z" not in sample:
                continue

            acc_x = sample["acc_x"][:]
            acc_y = sample["acc_y"][:]
            acc_z = sample["acc_z"][:]

            # ------------------------------------------------
            # Extract features
            # ------------------------------------------------

            features = extract_features(
                acc_x,
                acc_y,
                acc_z
            )

            if not features:
                continue

            # ------------------------------------------------
            # Add metadata
            # ------------------------------------------------

            features["sample_id"] = sample_id
            features["event"] = event_id
            features["type"] = sample_type
            features["label"] = LABEL_MAP[sample_type]

            rows.append(features)

    df = pd.DataFrame(rows)

    # ----------------------------------------------------------
    # Sort deterministically.
    #
    # h5py's iteration order over samples_group is not guaranteed
    # to be identical across machines/versions. RandomForestClassifier's
    # bootstrap sampling is positional (by row index), so a different
    # row order produces different trees even with a fixed
    # random_state. Sorting by sample_id fixes the row order so the
    # same script gives the same numbers everywhere.
    # ----------------------------------------------------------

    df = df.sort_values("sample_id").reset_index(drop=True)

    return df


# ============================================================
# MAIN
# ============================================================

def main():

    # --------------------------------------------------------
    # STEP 1 — Load and extract features
    # --------------------------------------------------------

    df = load_dataset()

    print(
        f"\nCreated feature dataset with "
        f"{len(df)} samples"
    )

    print("\nOriginal class counts:")

    print(
        df["type"].value_counts()
    )

    print("\n3-class counts:")

    print(
        df["label"]
        .map({
            0: "Pothole",
            1: "Speed Bump",
            2: "Uneven Road"
        })
        .value_counts()
    )


    # --------------------------------------------------------
    # STEP 2 — Select features
    # --------------------------------------------------------

    excluded_columns = [
        "sample_id",
        "event",
        "type",
        "label"
    ]

    feature_cols = [
        c for c in df.columns
        if c not in excluded_columns
    ]

    print(
        f"\nNumber of features: "
        f"{len(feature_cols)}"
    )


    # --------------------------------------------------------
    # STEP 3 — Prepare X / y
    # --------------------------------------------------------

    X = (
        df[feature_cols]
        .replace(
            [np.inf, -np.inf],
            np.nan
        )
        .fillna(0)
    )

    y = df["label"]
    groups = df["event"]


    # --------------------------------------------------------
    # STEP 4 — Grouped cross-validation
    #
    # GroupKFold guarantees every sample from the same event
    # stays entirely within one fold, so events never leak
    # between the "train" and "held-out" side of any fold —
    # same leakage protection as the earlier single train/test
    # split, but averaged over N_FOLDS different splits instead
    # of relying on one split that could be unusually easy or
    # unusually hard.
    # --------------------------------------------------------

    print("\n" + "=" * 60)
    print(f"{N_FOLDS}-FOLD GROUPED CROSS-VALIDATION")
    print("=" * 60)

    model = RandomForestClassifier(
        n_estimators=300,
        class_weight="balanced",
        random_state=RANDOM_STATE,
        n_jobs=-1
    )

    cv = GroupKFold(n_splits=N_FOLDS)

    # Per-fold accuracy, to see how much the score actually
    # varies fold to fold (this is the "is it stable?" check).
    fold_scores = cross_val_score(
        model,
        X,
        y,
        groups=groups,
        cv=cv,
        scoring="accuracy",
        n_jobs=1  # model itself already uses n_jobs=-1
    )

    print("\nPer-fold accuracy:")
    print(
        [round(s, 3) for s in fold_scores]
    )

    print(
        f"\nMean accuracy: {fold_scores.mean():.3f} "
        f"(+/- {fold_scores.std():.3f})"
    )


    # --------------------------------------------------------
    # STEP 5 — Out-of-fold predictions
    #
    # Every sample gets predicted exactly once, by a model that
    # never saw its event during training. Aggregating these
    # across all folds gives a classification report / confusion
    # matrix computed over the FULL dataset, which is a more
    # reliable summary than any single fold or single split.
    # --------------------------------------------------------

    oof_preds = cross_val_predict(
        model,
        X,
        y,
        groups=groups,
        cv=cv,
        n_jobs=1
    )

    print(
        "\n=== Classification report (out-of-fold, all samples) ==="
    )

    print(
        classification_report(
            y,
            oof_preds,
            labels=[0, 1, 2],
            target_names=CLASS_NAMES,
            zero_division=0
        )
    )


    # --------------------------------------------------------
    # STEP 6 — Confusion matrix (out-of-fold)
    # --------------------------------------------------------

    print("=== Confusion matrix (out-of-fold) ===")

    cm = confusion_matrix(
        y,
        oof_preds,
        labels=[0, 1, 2]
    )

    print(
        pd.DataFrame(
            cm,
            index=[
                "Actual Pothole",
                "Actual Speed Bump",
                "Actual Uneven Road"
            ],
            columns=[
                "Pred Pothole",
                "Pred Speed Bump",
                "Pred Uneven Road"
            ]
        )
    )


    # --------------------------------------------------------
    # STEP 7 — Misclassification analysis (out-of-fold)
    # --------------------------------------------------------

    df_with_preds = df.copy()
    df_with_preds["pred"] = oof_preds

    errors = df_with_preds[
        df_with_preds["label"] != df_with_preds["pred"]
    ]

    print(
        "\n=== Misclassifications (out-of-fold) ==="
    )

    if len(errors) > 0:

        print(
            errors[
                ["type", "label", "pred"]
            ].value_counts()
        )

    else:

        print("No misclassifications!")


    # --------------------------------------------------------
    # STEP 8 — Final model + feature importance
    #
    # Trained on ALL data (no held-out split) since cross-
    # validation above already gave us an honest performance
    # estimate. This final model is what you'd actually export
    # to TFLite for the app.
    # --------------------------------------------------------

    model.fit(X, y)

    importances = pd.Series(
        model.feature_importances_,
        index=feature_cols
    )

    print(
        "\n=== Top 20 most important features (final model, trained on all data) ==="
    )

    print(
        importances
        .sort_values(ascending=False)
        .head(20)
    )


    # --------------------------------------------------------
    # STEP 9 — Save artifacts
    #
    # This is what makes today's work reusable once you have
    # more/your own data:
    #   - model.joblib: the trained Random Forest itself
    #   - feature_cols.json: the EXACT feature names + order the
    #     model expects. When you add your own driving data later,
    #     extract_features() must produce this same set, in this
    #     same order, or the model's predictions become meaningless.
    #   - label_map.json: how numeric labels map back to class
    #     names, so anything loading this model later doesn't have
    #     to guess or hardcode it again.
    # --------------------------------------------------------

    os.makedirs(MODEL_DIR, exist_ok=True)

    model_path = os.path.join(MODEL_DIR, "model.joblib")
    joblib.dump(model, model_path)

    feature_cols_path = os.path.join(MODEL_DIR, "feature_cols.json")
    with open(feature_cols_path, "w") as f:
        json.dump(feature_cols, f, indent=2)

    label_map_path = os.path.join(MODEL_DIR, "label_map.json")
    with open(label_map_path, "w") as f:
        json.dump(
            {
                "label_to_name": {0: "pothole", 1: "speed_bump", 2: "uneven_road"},
                "original_type_to_label": LABEL_MAP,
            },
            f,
            indent=2,
        )

    print(f"\nSaved model      -> {model_path}")
    print(f"Saved feature list -> {feature_cols_path}")
    print(f"Saved label map   -> {label_map_path}")


if __name__ == "__main__":
    main()
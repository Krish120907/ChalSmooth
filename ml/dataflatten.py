"""
flatten_dataset.py

Flattens the Carlos 2019b pothole_depth.hdf5 file into a single flat table:
one row per sample, with acc/gyro summary-stat features as columns plus the
sample's metadata (type, depth, event, vehicle, placement, speed).

Usage:
    python flatten_dataset.py

Output:
    data/processed/pothole_dataset.csv
"""

import os
import h5py
import numpy as np
import pandas as pd

HDF5_PATH = "pothole_depth.hdf5"   # <-- change this to wherever you saved the file
OUTPUT_CSV = "data/processed/pothole_dataset.csv"


def stats(arr):
    """Summary stats for one axis of one sample. Returns NaNs if the axis is missing."""
    if arr is None or len(arr) == 0:
        return {"mean": np.nan, "std": np.nan, "min": np.nan,
                "max": np.nan, "ptp": np.nan, "rms": np.nan}
    return {
        "mean": arr.mean(),
        "std": arr.std(),
        "min": arr.min(),
        "max": arr.max(),
        "ptp": arr.max() - arr.min(),
        "rms": np.sqrt(np.mean(arr ** 2)),
    }


def extract_features(sample_group):
    """Build one feature dict from a single samples/<uuid> HDF5 group."""
    feats = {}

    # Accelerometer is always present
    for axis in ["acc_x", "acc_y", "acc_z"]:
        arr = sample_group[axis][:] if axis in sample_group else None
        for stat_name, val in stats(arr).items():
            feats[f"{axis}_{stat_name}"] = val

    # Gyroscope is only present in ~10% of samples
    has_gyro = "gyr_x" in sample_group
    feats["has_gyro"] = int(has_gyro)
    for axis in ["gyr_x", "gyr_y", "gyr_z"]:
        arr = sample_group[axis][:] if axis in sample_group else None
        for stat_name, val in stats(arr).items():
            feats[f"{axis}_{stat_name}"] = val

    feats["n_readings"] = sample_group["acc_x"].shape[0]
    return feats


def build_dataset(hdf5_path):
    rows = []
    with h5py.File(hdf5_path, "r") as f:
        samples = f["samples"]
        print(f"Loading {len(samples)} samples...")

        for sample_id in samples:
            sg = samples[sample_id]
            attrs = dict(sg.attrs)

            feats = extract_features(sg)
            feats["sample_id"] = sample_id
            feats["event"] = attrs.get("event")
            feats["type"] = attrs.get("type")
            feats["depth"] = attrs.get("depth", np.nan)
            feats["condition"] = attrs.get("condition", None)
            feats["speed"] = attrs.get("speed", np.nan)
            feats["vehicle"] = attrs.get("vehicle")
            feats["placement"] = attrs.get("placement")

            rows.append(feats)

    return pd.DataFrame(rows)


def main():
    df = build_dataset(HDF5_PATH)

    os.makedirs(os.path.dirname(OUTPUT_CSV), exist_ok=True)
    df.to_csv(OUTPUT_CSV, index=False)

    print(f"\nSaved flattened dataset -> {OUTPUT_CSV}")
    print(f"Shape: {df.shape[0]} rows x {df.shape[1]} columns")
    print("\nType breakdown:")
    print(df["type"].value_counts())
    print("\nSample of the flattened table:")
    print(df.head())


if __name__ == "__main__":
    main()

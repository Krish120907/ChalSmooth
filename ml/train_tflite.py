"""
train_tflite.py

Trains a small TFLite-compatible MLP on REAL extracted features
(same extract_features() as trainer.py / merged.py) and exports
a float32 .tflite that takes the raw 93 features in feature_cols.json
order and outputs 4 softmax probabilities.

Classes: 0 pothole, 1 speed_bump, 2 uneven_road, 3 normal.

Normalization is baked in as the first Keras layer, so Android only
needs to compute the 93 raw features -- no scaler asset required.

Usage:
    venv/bin/python ml/train_tflite.py
    (run from repo root ChalSmooth/)
"""

import json
import os
import sys

import numpy as np
import pandas as pd

sys.path.insert(0, os.path.join(os.path.dirname(__file__)))

from merged import load_hdf5_portion, load_kaggle_portion, CLASS_NAMES  # noqa: E402

MODEL_DIR = os.path.join(os.path.dirname(__file__), "models")
OUT_TFLITE = os.path.join(MODEL_DIR, "road_model.tflite")
OUT_KERAS = os.path.join(MODEL_DIR, "road_model.keras")

EPOCHS = 60
BATCH = 256
SEED = 42


def main():
    import tensorflow as tf
    from sklearn.model_selection import train_test_split
    from sklearn.utils.class_weight import compute_class_weight

    np.random.seed(SEED)
    tf.random.set_seed(SEED)

    print("Loading datasets (same pipeline as merged.py)...")
    df_hdf5 = load_hdf5_portion()
    df_kaggle = load_kaggle_portion()
    df = pd.concat([df_hdf5, df_kaggle], ignore_index=True, sort=False)
    df = df.sort_values("sample_id").reset_index(drop=True)
    print(f"Combined: {len(df)} rows")
    print(df["label"].map(dict(enumerate(CLASS_NAMES))).value_counts())

    with open(os.path.join(MODEL_DIR, "feature_cols.json")) as f:
        feature_cols = json.load(f)
    print(f"Features: {len(feature_cols)}")

    X = df[feature_cols].replace([np.inf, -np.inf], np.nan).fillna(0).to_numpy(dtype=np.float32)
    y = df["label"].to_numpy(dtype=np.int64)
    assert X.shape[1] == 93, X.shape

    X_tr, X_va, y_tr, y_va = train_test_split(
        X, y, test_size=0.15, random_state=SEED, stratify=y
    )

    classes = np.unique(y_tr)
    cw = compute_class_weight("balanced", classes=classes, y=y_tr)
    class_weight = {int(c): float(w) for c, w in zip(classes, cw)}
    print("class_weight:", class_weight)

    norm = tf.keras.layers.Normalization(axis=-1)
    norm.adapt(X_tr)

    model = tf.keras.Sequential([
        norm,
        tf.keras.layers.Dense(128, activation="relu"),
        tf.keras.layers.Dropout(0.25),
        tf.keras.layers.Dense(64, activation="relu"),
        tf.keras.layers.Dropout(0.2),
        tf.keras.layers.Dense(32, activation="relu"),
        tf.keras.layers.Dense(4, activation="softmax"),
    ])
    model.compile(
        optimizer=tf.keras.optimizers.Adam(1e-3),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )
    cb = [
        tf.keras.callbacks.EarlyStopping(monitor="val_accuracy", patience=12,
                                         restore_best_weights=True),
        tf.keras.callbacks.ReduceLROnPlateau(monitor="val_loss", factor=0.5,
                                             patience=5, min_lr=1e-5),
    ]
    model.fit(X_tr, y_tr, validation_data=(X_va, y_va), epochs=EPOCHS,
              batch_size=BATCH, class_weight=class_weight, callbacks=cb, verbose=2)

    print("\n=== validation ===")
    loss, acc = model.evaluate(X_va, y_va, verbose=0)
    print(f"val loss {loss:.4f} acc {acc:.4f}")
    pred = np.argmax(model.predict(X_va, verbose=0), axis=1)
    for c, name in enumerate(CLASS_NAMES):
        m = y_va == c
        print(f"  {name:12s} recall {(pred[m] == c).mean():.3f} (n={m.sum()})")

    model.save(OUT_KERAS)
    print("saved", OUT_KERAS)

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_bytes = converter.convert()
    with open(OUT_TFLITE, "wb") as f:
        f.write(tflite_bytes)
    print(f"saved {OUT_TFLITE} ({len(tflite_bytes)} bytes)")

    # Roundtrip check through the actual TFLite interpreter.
    interp = tf.lite.Interpreter(model_content=tflite_bytes)
    interp.allocate_tensors()
    inp, out = interp.get_input_details()[0], interp.get_output_details()[0]
    print("tflite in:", inp["shape"], inp["dtype"], "out:", out["shape"], out["dtype"])
    sample = X_va[:8]
    interp.set_tensor(inp["index"], sample)
    interp.invoke()
    lite_pred = interp.get_tensor(out["index"])
    keras_pred = model.predict(sample, verbose=0)
    print("max |tflite - keras| =", float(np.max(np.abs(lite_pred - keras_pred))))
    print("tflite preds:", np.argmax(lite_pred, axis=1).tolist())
    print("keras  preds:", np.argmax(keras_pred, axis=1).tolist())


if __name__ == "__main__":
    main()

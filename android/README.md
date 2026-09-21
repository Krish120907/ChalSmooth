# Road Classifier — Android app

Simple Kotlin app that classifies road surface from the phone's motion sensors
using the on-device model in `app/src/main/assets/model.tflite`.

## What it does

- Collects **accelerometer** at `SENSOR_DELAY_GAME` (~50 Hz) into a 128-sample
  sliding window; **gyroscope** is shown live in the UI (display-only — the
  model uses accelerometer features).
- Computes the same 93 features as `ml/trainer.py::extract_features`
  (population std, linear-interpolation percentiles, raw sample jerk,
  threshold peak count, mean-centered sign changes — see `FeatureExtractor.kt`).
- Runs inference on-device (`[1,93]` float32 in → `[1,4]` softmax out) and shows:
  **Pothole detected / Speed bump detected / Uneven road / Normal road**
  with confidence + per-class bars.

## Model

- `app/src/main/assets/model.tflite` is a copy of `ml/models/road_model.tflite`:
  small MLP (Normalization baked in → 128 → 64 → 32 → 4 softmax), trained on
  real HDF5 + Kaggle windows with the exact training feature pipeline.
  Validation accuracy ≈ 0.85 (normal recall ≈ 0.97).
- To retrain + refresh the asset:
  ```
  cd ml && ./venv/bin/python train_tflite.py
  cp models/road_model.tflite ../android/app/src/main/assets/model.tflite
  ```

## Build

Open `android/` in Android Studio (it will generate the Gradle wrapper and
download the SDK). Then **Run ▶** on a physical phone — sensors don't work
well on the emulator. Tap **Start Detection**, drive / move the phone over
bumps; tap **Stop Detection** to pause.

No special permissions needed (plain accelerometer + gyroscope, foreground
activity only).

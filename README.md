# ChalSmooth — Comfort-First Navigation for Indian Roads

ChalSmooth is an end-to-end system that detects road surface anomalies (potholes, speed bumps, uneven pavement) from smartphone IMU data, crowdsources them into a routable road graph, and exposes **comfort-prioritised routing** — letting drivers trade a few extra minutes for a dramatically smoother ride.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Machine Learning Pipeline](#machine-learning-pipeline)
- [Android Applications](#android-applications)
- [Frontend & Server](#frontend--server)
- [Routing & Backend Infrastructure](#routing--backend-infrastructure)
- [Development Setup](#development-setup)
- [Usage](#usage)
- [Data & Models](#data--models)
- [References](#references)

---

## Architecture Overview

Five independently testable layers:

| Layer | Responsibility | Key Technology |
|-------|----------------|----------------|
| **Sensing** | Foreground service sampling accelerometer + gyroscope + GPS at 50 Hz | Android `SensorManager`, `SENSOR_DELAY_GAME` |
| **Inference** | On-device TFLite MLP (93 features → 4 classes) | TensorFlow Lite, `FeatureExtractor.kt` mirrors `trainer.py` exactly |
| **Local Store + Sync** | Room DB of detections, batched Wi-Fi upload via WorkManager | Room, WorkManager, Retrofit |
| **Road-Graph Annotation** | Map-match detections → OSM segments → per-segment roughness scores | OSRM/Valhalla map-matching, HMM, decayed aggregation |
| **Routing + UI** | λ-sweep constrained shortest path, comfort/time trade-off slider | Custom `LagrangianRoutingEngine`, Mappls/OSRM, Jetpack Compose |

### Comfort-Prioritised Routing (Core Innovation)

For each directed edge *e*:
```
time(e)     = length(e) / speed(e)
comfort(e)  = length(e) × normalisedRoughness(e)    // 0 = smooth, 1 = worst
cost(e, λ)  = time(e) × (1 + λ × normalisedRoughness(e))
```

The UI slider sets a **hard time budget *T***. We binary-search λ over ~8–12 Dijkstra iterations to find the largest λ whose route satisfies `time ≤ T`. This is optimal on the Pareto frontier and runs in milliseconds on a metro-scale graph with contraction hierarchies.

**Slider behaviour:**
- Minimum (T_min) → λ = 0 → fastest route
- Maximum → λ unbounded → smoothest route
- Continuous mapping feels responsive

Always show the delta: `"+6 min, 71% fewer rough segments"`. A comfort score without a baseline means nothing to users.

---

## Machine Learning Pipeline

### Data Sources

1. **HDF5 (Carlos2019b / PotholeDepth)** — labeled events: `Pothole`, `Speed_bump`, `Metal_bumps`, `Ditch`, `Manhole_cover` (mapped to 3 classes: pothole, speed_bump, uneven_road)
2. **Kaggle Continuous Drives** — 5 trips with timestamped pothole annotations + full accelerometer streams. Windowed into:
   - Positive windows centered on pothole timestamps (class 0)
   - Negative "normal driving" windows sampled between potholes (class 3 — **new class not in HDF5**)

This directly fixes the generalization failure where models trained only on event snippets over-flagged ordinary driving as "pothole".

### Feature Extraction (`ml/trainer.py::extract_features`)

**93 features per 128-sample window (2.56 s @ 50 Hz):**

| Group | Axes | Statistics (11 each) |
|-------|------|---------------------|
| Acceleration | X, Y, Z, Mag | mean, std (population), min, max, median, ptp, IQR, RMS, energy, skew, kurtosis |
| Jerk (sample-to-sample diff) | X, Y, Z, Mag | same 11 statistics |
| Peak / Crossing | Mag | `peak_count` (mag > mean + 2σ), `max_abs_magnitude` |
| Sign changes | X, Y, Z (mean-centered) | `acc_{x,y,z}_sign_changes` (np.sign semantics: 0 is own state) |

**Critical implementation details (must match exactly on device):**
- Population std (ddof=0), NOT sample std
- Linear-interpolation percentiles (np.percentile default)
- Skew = mean(((x-μ)/σ)³), Kurtosis = mean(((x-μ)/σ)⁴) - 3
- Jerk = raw `np.diff`, NO sample-rate scaling
- Peak threshold = mean + 2×std (population)
- Sign changes on mean-centered values with np.sign (sign(0)=0)

### Training (`ml/trainer.py`)

- **Model:** RandomForest (300 trees, class_weight=balanced)
- **Validation:** 5-fold GroupKFold by **event/trip** — never leaks samples from same event across train/test
- **Outputs:** `model.joblib`, `feature_cols.json` (exact 93-name order), `label_map.json`

### TFLite Export (`ml/train_tflite.py`)

- **Architecture:** Normalization (adapted on train) → Dense(128, ReLU, Dropout 0.25) → Dense(64, ReLU, Dropout 0.2) → Dense(32, ReLU) → Dense(4, Softmax)
- **Classes:** 0 pothole, 1 speed_bump, 2 uneven_road, 3 normal
- **Normalization baked in** — Android only computes raw 93 features, no scaler asset needed
- **Output:** `ml/models/road_model.tflite` → copied to `android/app/src/main/assets/model.tflite`

### Retraining

```bash
cd ml
source venv/bin/activate
python train_tflite.py
cp models/road_model.tflite ../android/app/src/main/assets/model.tflite
```

---

## Android Applications

The repo contains **two distinct app modules** under `android/app/src/main/java/`:

### 1. Road Classifier Demo (`com.chalsmooth.roadclassifier2.*`)

A minimal standalone activity demonstrating the TFLite classifier.

**Files:**
- `MainActivity.kt` — SensorEventListener @ 50 Hz, 128-sample sliding window, inference every 500 ms
- `FeatureExtractor.kt` — **Line-for-line port** of `trainer.py::extract_features` (93 features, exact math)
- `TFLiteClassifier.kt` — Loads `assets/model.tflite`, runs `[1,93] float32 → [1,4] softmax`
- `OsrmRoutingService.kt` — OSRM API client for alternative routes

**UI:** Text classification + confidence + per-class probability bars. Gyroscope shown for display only.

**Build & Run:**
```bash
# Open android/ in Android Studio (generates Gradle wrapper, downloads SDK)
# Run on PHYSICAL PHONE — sensors don't work well on emulator
# Tap "Start Detection", drive over bumps, tap "Stop Detection"
```

### 2. Full ChalSmooth App (`com.chalsmooth.app.*`)

Production-grade Compose-based app with all five layers.

**Key Components:**

| Module | Purpose |
|--------|---------|
| `sensor/ImuSensingService.kt` | Foreground service @ 50 Hz, computes vertical jerk, emits `ImuSample` via StateFlow, persistent notification |
| `data/model/Pothole.kt` | Room entity: id, lat, lon, depth_cm, severity (Low/Medium/High/Critical), confidence, timestamp, device_id, vehicle_type |
| `data/repository/PotholeRepository.kt` | Flow-backed DAO, inserts, queries by bbox/severity |
| `routing/LagrangianRoutingEngine.kt` | λ-sweep interpolation between preset fastest/smoothest Pune routes (demo data) |
| `ui/map/MapScreen.kt` | Compose map placeholder with hazard list, severity filters, search |
| `ui/navigation/NavigationScreen.kt` | Turn-by-turn with comfort score, time delta chip, rough-segment warning |
| `ui/hud/HudScreen.kt` | Live telemetry: vertical jerk gauge, anomaly detection, speed, recording status |
| `ui/dashboard/DashboardScreen.kt` | Stats cards, recent detections, contribution toggle |
| `ui/studio/SensorStudioScreen.kt` | Calibration & data-collection tool for ground truth |

**Dependencies (from `build.gradle.kts`):**
- Mappls SDK (MapmyIndia) via BoM 2.0.4
- TensorFlow Lite 2.16.1
- Retrofit + Moshi + OkHttp for OSRM/Mappls APIs
- Coroutines, Lifecycle, Material3, Serialization

**Manifest notes:**
- Foreground service type `location` + `sensor`
- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE`
- Mappls API key in `local.properties` (not committed)

---

## Frontend & Server

### `server.py` — Lightweight HTTP Server

Serves the `frontend/` directory (not present in repo, built by `build.py`) at `http://localhost:3000` with CORS headers and no-cache.

```bash
python3 server.py
# → ChalSmooth Frontend running at http://localhost:3000
```

### `build.py` — Frontend Bundler

Combines `frontend/css/*.css` into `dist/chalsmooth.min.css`, inlines into `dist/index.html`, copies `js/` assets. Output in `dist/`.

---

## Routing & Backend Infrastructure

### Valhalla Routing Engine (`routing/`)

Custom Valhalla build for dynamic per-edge costing (roughness as edge penalty).

**Files (stubs — need implementation):**
- `Dockerfile` — Valhalla + OSM extract build
- `valhalla.json` — Costing options with `road_class_penalty` table
- `scripts/fetch_osm.sh` — Download Pune/India OSM extract (Geofabrik)
- `scripts/build_tiles.sh` — Build Valhalla tiles with custom profile

**Valhalla costing concept:**
```json
{
  "costing": "auto",
  "costing_options": {
    "auto": {
      "edge_cost": [
        {"road_class": "residential", "penalty": 0.0},
        {"road_class": "tertiary", "penalty": 0.1},
        {"road_class": "secondary", "penalty": 0.2}
      ],
      "dynamic_edge_penalty_table": "roughness_penalty"
    }
  }
}
```

### Backend (`backend/`)

Placeholder for FastAPI/Flask service:
- `POST /detections` — batch ingest from Android
- `GET /segments?bbox=` — roughness vector tiles for heatmap
- `POST /route` — λ-sweep routing (origin, dest, max_time) → {polyline, eta, comfortScore, segmentRoughness[]}

---

## Development Setup

### Prerequisites

| Tool | Version |
|------|---------|
| Python | 3.11+ |
| Android Studio | Ladybug (2024.2+) |
| JDK | 17 |
| Docker | 24+ (for Valhalla) |
| Node.js | 18+ (if frontend rebuild needed) |

### Python Environment (ML)

```bash
cd ml
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt  # numpy, pandas, h5py, scikit-learn, tensorflow, joblib
```

### Android

1. Open `android/` in Android Studio
2. Add `MAP_KEY=your_mappls_key` to `local.properties`
3. Connect physical device (USB debugging)
4. Run `roadclassifier2` or `app` module

### Valhalla Routing (Optional — for full routing)

```bash
cd routing
chmod +x scripts/*.sh
./scripts/fetch_osm.sh        # downloads pune-india.osm.pbf
./scripts/build_tiles.sh      # builds valhalla tiles (takes 10–30 min)
docker build -t chalsmooth-valhalla .
docker run -d -p 8002:8002 chalsmooth-valhalla
```

---

## Usage

### Road Classifier Demo (Quick Test)

1. Install `roadclassifier2` on phone
2. Mount phone in car cradle (orientation fixed)
3. Tap **Start Detection**
4. Drive — UI shows real-time classification:
   - 🔴 **Pothole detected** (red)
   - 🟠 **Speed bump detected** (orange)
   - 🔵 **Uneven road** (blue)
   - 🟢 **Normal road** (green)
5. Tap **Stop Detection**

### Full App Flow (Compose App)

1. **Onboarding** — Consent screen (DPDP Act 2023 compliant)
2. **Dashboard** — Stats, recent hazards, contribution toggle
3. **Map** — Search, severity filter, hazard list (map placeholder)
4. **Navigation** — Enter source/dest → slider appears → drag to trade time for comfort
5. **HUD** — Live jerk gauge while driving, auto-recording
6. **Sensor Studio** — Calibrate, record labelled sessions for retraining

### Server + Frontend

```bash
python3 build.py   # builds dist/
python3 server.py  # serves at localhost:3000
```

---

## Data & Models

### Raw Data (`ml/data/raw/`)

```
ml/data/raw/
├── pothole_depth.hdf5          # Carlos2019b dataset (events with acc_x/y/z)
└── Pothole/
    ├── trip1_sensors.csv       # Kaggle trip 1: timestamp, accelerometerX/Y/Z
    ├── trip1_potholes.csv      # Kaggle trip 1: pothole timestamps
    ├── trip2_sensors.csv
    ├── trip2_potholes.csv
    ├── trip3_sensors.csv
    ├── trip3_potholes.csv
    ├── trip4_sensors.csv
    ├── trip4_potholes.csv
    ├── trip5_sensors.csv
    └── trip5_potholes.csv
```

### Model Artifacts (`ml/models/`)

| File | Description |
|------|-------------|
| `road_model.tflite` | TFLite MLP (93→128→64→32→4), normalization baked in |
| `road_model.keras` | Keras source model |
| `feature_cols.json` | **Exact 93 feature names in order** — FeatureExtractor.kt must match |
| `label_map.json` | `{0:"pothole", 1:"speed_bump", 2:"uneven_road", 3:"normal"}` |
| `model.joblib` | RandomForest (trainer.py) |

### Android Asset

`android/app/src/main/assets/model.tflite` — copy of `ml/models/road_model.tflite`. Update after retraining.

---

## Key Implementation Invariants

1. **Feature parity** — `FeatureExtractor.kt` **must** produce identical 93 floats in identical order as `trainer.py::extract_features`. Unit test with saved fixture window.
2. **GroupKFold by event/trip** — Never random-split windows; overlapping windows leak.
3. **Normalization in model** — TFLite has `Normalization` layer adapted on train data; Android sends raw features.
4. **Foreground service** — Required for 50 Hz sensing while screen off; persistent notification is mandatory.
5. **Map-matching before aggregation** — Raw GPS is 5–15 m off; use OSRM/Valhalla `/match` to snap to OSM ways.
6. **Decay & confidence floor** — Roughness = Σ(severity×confidence) / (length × distinct_passes); half-life ~90 days; <3 passes → "unrated".

---

## Project Structure

```
ChalSmooth/
├── README.md                    # This file
├── server.py                    # Frontend dev server (port 3000)
├── build.py                     # Frontend bundler → dist/
├── .gitignore
├── android/                     # Android Studio project
│   ├── app/
│   │   ├── build.gradle.kts
│   │   └── src/main/
│   │       ├── assets/model.tflite
│   │       ├── java/
│   │       │   ├── com/chalsmooth/roadclassifier2/   # Demo classifier
│   │       │   └── com/chalsmooth/app/               # Full Compose app
│   │       └── res/
│   └── README.md
├── ml/                          # Machine Learning pipeline
│   ├── trainer.py               # RandomForest + GroupKFold (3-class)
│   ├── train_tflite.py          # MLP → TFLite (4-class, norm baked in)
│   ├── merged.py                # HDF5 + Kaggle merge + GroupKFold (4-class)
│   ├── data/raw/                # HDF5 + Kaggle CSVs
│   ├── models/                  # .tflite, .keras, .joblib, feature_cols.json
│   └── venv/                    # Python virtual env (gitignored)
├── routing/                     # Valhalla custom routing
│   ├── Dockerfile
│   ├── valhalla.json
│   └── scripts/
├── backend/                     # Backend API (stub)
│   └── Dockerfile
├── frontend/                    # Web frontend source (not in repo)
├── dist/                        # Bundled frontend (gitignored)
├── docs/                        # Architecture, dev-plan, licensing (empty stubs)
└── tools/                       # Misc scripts
```

---

## References

| # | Citation | Key Insight |
|---|----------|-------------|
| 1 | **Selvam, A.** *Potholes — Realtime pothole detection on Android IMU* (github.com/aswathselvam/Potholes) | SVM in C++ via JNI, 50 Hz accel+gyro, MATLAB Coder export, **orientation calibration listed as open TODO**. No license file → treat as reference only. |
| 2 | **Silvister et al.** *Deep Learning Approach to Detect Potholes*, MIT-WPU Pune | RMS of 10 readings around pothole instant → ANN (ReLU, BCE). Closest published analogue. |
| 3 | **Pawar, Jagtap & Bhoir** (2020). *Efficient pothole detection using smartphone sensors*. ITM Web of Conferences 32, 03013. | NN on accel+gyro: **94.78% accuracy, 0.71 precision, 0.81 recall** — realistic benchmark. |
| 4 | **Mednis et al.** (2011). *Real Time Pothole Detection Using Android Smartphones*. DCOSS. | Foundational citation for smartphone-accelerometer pothole detection. |
| 5 | **Ozoglu & Gökgöz** (2023). *Detection of Road Potholes by Applying CNN on Vibration Data*. Sensors 23(22), 9023. | CNN on raw smartphone IMU, 93.24% val accuracy — alternative to hand-crafted features. |
| 6 | **Egaji et al.** (2021). *Real-time ML-based approach for pothole detection*. Expert Systems with Applications. | Two-app methodology (record + label), preprocessing pipeline template. |
| 7 | **OSRM** (BSD-2) & **Valhalla** (MIT) | Open-source routing engines with custom edge costing & map-matching. |

---

## License

- **ML code & Android app:** MIT (add LICENSE file)
- **Selvam/Potholes repo:** No license published → **do not ship**, use as architectural reference only
- **LIBSVM** (under androidlibsvm): BSD-3-Clause
- **MATLAB Coder output:** MathWorks license conditions apply
- **OSRM:** BSD-2-Clause
- **Valhalla:** MIT
- **Mappls SDK:** Proprietary — requires API key

---

## Contributing

1. Fork & create feature branch
2. Keep `FeatureExtractor.kt` and `trainer.py::extract_features` in sync (add unit test)
3. Run `python ml/trainer.py` and `python ml/train_tflite.py` before committing model changes
4. Test on physical device — emulator sensors are unreliable
5. Update `CHANGELOG.md` (to be created)

---

## Roadmap (from `docs/dev-plan.md` — to be fleshed out)

- [ ] Phase 0: Groundwork — Android project, Maps key, OSRM/Valhalla local, licensing decision
- [ ] Phase 1: Data collection app — ForegroundService @ 50 Hz, CSV logging, "pothole now" button, dashcam sync
- [ ] Phase 2: Model training — Feature parity test, compare Linear/RBF SVM, 1D-CNN, GBM; session-split CV
- [ ] Phase 3: On-device inference — Kotlin feature port + unit test, TFLite integration, debouncing, Room + WorkManager
- [ ] Phase 4: Backend + road graph — POST /detections, map-matching job, roughness tiles, Valhalla custom profile
- [ ] Phase 5: Routing engine — λ-sweep binary search, caching, Directions API fallback flag
- [ ] Phase 6: UI — Gradient polyline, heatmap layer, time slider with debounce, ETA + comfort delta
- [ ] Phase 7: Evaluation — Held-out video ground truth, Pareto curves for 20 OD pairs, blind rider study (n=8–10)

---

**ChalSmooth** — Smoother rides, smarter routing. 🛣️
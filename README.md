Architecture at a glance

Five layers, each independently testable:

Sensing service — foreground service sampling accelerometer + gyroscope + GPS
Inference engine — native SVM (JNI) or TFLite interpreter, running on windowed IMU features
Local store + sync — Room DB of detections, batched upload to a backend
Road-graph annotation — map-matched pothole events aggregated into per-segment roughness scores
Routing + UI — candidate route generation, comfort scoring, map with time slider
Part A — Detection model integration
Choosing the model

The most directly reusable open-source starting point is aswathselvam/Potholes. It performs realtime pothole detection on Android IMU data with an SVM in C++ integrated through the Java NDK, sampling accelerometer and gyroscope at 50 Hz (20 ms refresh), with the data format being timestamp, Accel X/Y/Z, Gyro X/Y/Z. The trained model is exported to C code with MATLAB Coder, the C-generation repo lives at github.com/aswathselvam/pothole_MATLAB, and Android accesses it through JNI via the svmPredict() function. 
GitHub

Two things to know before committing to it:

Orientation calibration is listed as an open TODO — the repo notes calibration should supply a rotation matrix transforming between the orientation used during training data collection and the phone's orientation at detection time. You will have to build this yourself; it is the single biggest source of false positives in IMU pothole detection. 
GitHub
The repository publishes no LICENSE file. Under GitHub's terms that means default copyright — all rights reserved. Treat it as reference material, not as something you can ship, unless you email the author and get written permission or an added license. The bundled androidlibsvm wrapper is LIBSVM underneath (BSD-3-Clause, fine to use), and MATLAB Coder-generated C carries MathWorks licensing conditions of its own.

Given that, my recommendation is: use Selvam's repo as the architectural template, but train your own model on your own collected data. That sidesteps the licensing question entirely, matches your own vehicles and phone mounts, and is a stronger research contribution for the poster.

For the model family itself, two published reference points:

The MIT-WPU (Pune) team's approach: they recorded the spikes in accelerometer and gyroscope readings as a vehicle passes over a pothole, took the root mean square of 10 readings for both sensors around the instant of the pothole, stored those in a database, and used those values to train an artificial neural network with ReLU activation and binary cross-entropy loss. Their system maps potholes on the user's route, with accelerometer and gyroscope readings continuously assessed by a deep feed-forward network. This is the closest published analogue to what you are building. 
github
mitwpu
Pawar, Jagtap & Bhoir (ITM Web of Conferences, 2020): a neural network trained on smartphone accelerometer and gyroscope data reaching 94.78% classification accuracy, with 0.71 precision and 0.81 recall — a reasonable trade-off given the heavy class imbalance of the problem. Those precision/recall figures are the realistic bar to benchmark against; treat any claim of ~100% accuracy in this literature with suspicion, since it usually reflects a tiny single-vehicle dataset. 
itm-conferences
Preprocessing pipeline

Run this identically at training time and on-device, or the model will silently degrade:

Sample accelerometer and gyroscope via SensorManager at SENSOR_DELAY_GAME and resample to a fixed 50 Hz grid. Android delivers samples at irregular intervals, so interpolate onto a uniform clock using the event.timestamp nanosecond field, not wall-clock time.
Reorient. Use TYPE_ROTATION_VECTOR → SensorManager.getRotationMatrixFromVector() to rotate raw device-frame readings into a vehicle/world frame (Z aligned with gravity). This makes the model invariant to how the phone sits in the cradle and solves the calibration TODO above.
Gravity removal. Either use TYPE_LINEAR_ACCELERATION, or high-pass filter Z at ~0.5 Hz. Keep a low-pass gravity estimate for the reorientation step.
Window. Sliding window of 128 samples (2.56 s) with 50% overlap. Short enough to localise a pothole to a few metres at urban speeds, long enough to capture the full impact-rebound signature.
Features per window, per axis: mean, standard deviation, RMS, min, max, peak-to-peak, zero-crossing rate, signal magnitude area, plus a few FFT band energies (0–5, 5–15, 15–25 Hz). Add speed from GPS as a feature — the same pothole produces wildly different accelerations at 20 km/h vs 60 km/h, and this is the fix that most student projects miss.
Normalise with the mean/variance vector saved at training time. Ship those constants as an asset; never recompute them on-device.
Running inference

Option 1 — TFLite (recommended). Train in Python (scikit-learn or Keras), convert to .tflite, drop into app/src/main/assets/, call via the Interpreter API or LiteRT. Simpler build, no NDK toolchain, quantise to int8 for negligible battery cost. For an SVM specifically you can either reimplement the decision function in Kotlin (it is a dot product against support vectors — trivial for a linear kernel, a few lines for RBF) or wrap it as a tiny dense network.

Option 2 — JNI/NDK, mirroring Selvam's design: put the generated C in app/src/main/cpp/, write a CMakeLists.txt, expose Java_com_yourapp_PotholeDetector_svmPredict(JNIEnv*, jobject, jfloatArray), and load with System.loadLibrary(). Choose this only if you are reusing MATLAB Coder output directly.

Either way, run inference on a background HandlerThread, never the sensor callback thread.

From inference to a usable signal
Model emits a class plus a confidence/decision-function margin per window.
Severity = normalised peak vertical jerk within the window, bucketed low/medium/high, cross-checked against confidence. Report severity, not just a binary flag — routing needs a weight, not a boolean.
Debounce: suppress detections within ~3 s or 15 m of a prior one, so one pothole is not counted five times.
Tag each detection with {lat, lon, speed, heading, severity, confidence, timestamp, deviceModel, vehicleType} from a fused FusedLocationProviderClient reading interpolated to the window's centre timestamp.
Persist to Room, then upload in batches over Wi-Fi via WorkManager. Never block the drive on network.
Part B — Turning detections into a routable graph

This is the step that connects detection to navigation, and it deserves more attention than most project plans give it.

Map-match each detection to a road segment. Use OSRM's or Valhalla's map-matching endpoint (both open source, both support hidden-Markov-model matching of noisy GPS traces), or Mapbox Map Matching. Raw GPS is 5–15 m off in cities; without matching you will attribute potholes to the wrong road.
Aggregate per directed segment (OSM way, split at intersections): roughness(s) = Σ(severity_i × confidence_i) / (length_s × distinct_passes_s) Dividing by distinct passes is what makes this crowdsourced rather than popularity-weighted — otherwise a busy arterial always looks worse than a quiet lane.
Confidence floor: a segment with fewer than N passes (start with N=3) falls back to a neutral prior, and the UI shows it as "unrated" rather than "smooth". Be honest about coverage.
Decay old observations with a half-life of ~90 days so repairs are reflected.
Part C — Comfort-prioritised routing
Cost function

For each directed edge e:

time(e)    = length(e) / speed(e)
comfort(e) = length(e) × normalisedRoughness(e)     // 0 = smooth, 1 = worst
cost(e, λ) = time(e) × (1 + λ × normalisedRoughness(e))

λ is a detour-tolerance parameter, not a user-facing control.

The slider ↔ λ relationship

The slider sets a hard time budget T. This is a constrained shortest path problem (CSP): minimise total discomfort subject to total time ≤ T. It is NP-hard in general, but three practical approaches work at city scale:

Approach 1 — Lagrangian relaxation / λ-sweep (recommended).
Run Dijkstra repeatedly on cost(e, λ) while binary-searching λ:

λ = 0 → fastest route, time T_min
λ → large → smoothest route, time T_max
Binary search λ over ~8–12 iterations to find the largest λ whose resulting route has time ≤ T

Each iteration is one Dijkstra run; on a metro-scale graph with contraction hierarchies this is milliseconds. The result is optimal on the Pareto frontier and near-optimal for the constrained problem. Slider at maximum → λ unbounded → smoothest route; slider at minimum (= T_min) → λ = 0 → fastest route. The mapping is continuous and feels responsive.

Approach 2 — full Pareto set. Multi-objective Dijkstra (Martins' algorithm) labelling each node with non-dominated (time, discomfort) pairs, then pick the minimum-discomfort label with time ≤ T. Exact, but label sets blow up on large graphs. Fine for a city-district demo, and it lets you draw the actual trade-off curve on the poster.

Approach 3 — API post-filter (fastest to ship). Call Google Directions or Mapbox Directions with alternatives=true, get 2–3 candidate polylines, snap each to your segment graph, score each for time and comfort, and present the best-comfort candidate under T.

Approach 3's limitation is real and you should state it in the writeup: commercial APIs return only a handful of alternatives, all optimised for time, so a genuinely smoother back-route may simply never appear as a candidate. It is a good sprint-1 fallback and a poor final answer. Build on your own routing graph (OSRM or Valhalla self-hosted, OSM extract of Pune) so you control edge weights directly — that is what makes Approach 1 possible at all, and it is the difference between a project that demonstrates comfort routing and one that only re-ranks Google's suggestions.

Presenting the trade-off

Always compute both the λ=0 route and the selected route, and show the delta: "+6 min, 71% fewer rough segments." A comfort score with nothing to compare against means nothing to the user.

Part D — UI design

Main navigation screen (single MapFragment, bottom sheet for controls):

Map — Google Maps SDK or MapLibre, current location puck, destination search bar at top.
Route polyline coloured by roughness — green → amber → red gradient along the line itself. This is the single most legible way to show comfort, far better than a separate number. Use PolylineOptions split into per-segment spans, or a MapLibre line-gradient expression.
Pothole layer — individual markers when zoomed in past ~z16, a heatmap tile layer when zoomed out. Toggleable.
Time slider (bottom sheet, horizontal): range from T_min (computed fastest, shown as the left anchor label) to T_min × 1.5, rounded to minutes. Debounce the onProgressChanged callback ~300 ms before recomputing so dragging is smooth.
Two large metrics side by side above the slider: ETA (e.g. "24 min") and Comfort (e.g. "82 / 100", with a small delta chip "+14 vs fastest").
Start / Alternatives buttons.

Recording state — a persistent notification while the sensing service runs, with an explicit stop. Required for foreground-service compliance and, more importantly, for user trust.

Onboarding / consent — location and motion data are sensitive. A first-run screen explaining what is collected, that traces are uploaded, and offering an opt-out of contribution while still consuming the map. Non-negotiable; also a requirement under India's DPDP Act, 2023 for personal data processing.

Part E — Step-by-step development plan

Phase 0 — Groundwork (week 1)

Create the Android Studio project: Kotlin, min SDK 26, Jetpack Compose or Views, MVVM with Hilt.
Obtain Maps SDK key; enable Maps SDK for Android and Directions API in Google Cloud Console.
Decide licensing posture: email the Selvam repo author for a license grant, or commit to training your own model. Record the decision.
Pull an OSM extract for Pune (Geofabrik) and stand up OSRM or Valhalla locally in Docker.

Phase 1 — Data collection app (weeks 2–3)

Build a ForegroundService sampling accelerometer, gyroscope, rotation vector at 50 Hz plus GPS at 1 Hz.
Write to CSV in the format timestamp, ax, ay, az, gx, gy, gz, lat, lon, speed — matching the timestamp/accel-XYZ/gyro-XYZ format used in the reference project plus location. 
GitHub
Add a big "pothole now" button for ground-truth labelling by a passenger, plus post-hoc labelling from a synchronised dashcam video.
Collect ≥ 5 hours across ≥ 2 vehicles (car and two-wheeler), ≥ 2 phones, mixed road types. Include smooth-road negatives, speed bumps, manhole covers, and rough-but-not-pothole surfaces — speed bumps are the hardest confounder and you need them in the training set.

Phase 2 — Model training (weeks 3–4)

Build the feature extraction pipeline in Python; keep it in a single module you will later port to Kotlin line-for-line.
Train and compare: linear SVM, RBF SVM, small 1D-CNN over raw windows, gradient boosting.
Split by drive session, not by window — random window splits leak overlapping data and produce the inflated accuracies seen in parts of this literature.
Report precision/recall/F1 and a confusion matrix; benchmark against the 94.78% accuracy, 0.71 precision, 0.81 recall figures reported by Pawar et al. 
itm-conferences
Export: TFLite conversion, or MATLAB Coder → C if following the JNI path.

Phase 3 — On-device inference (week 5)

Port feature extraction to Kotlin; write a unit test asserting Kotlin and Python features match to 1e-5 on a saved fixture window. Do not skip this — silent preprocessing drift is the most common cause of "worked in the notebook, useless on the phone".
Integrate the interpreter (TFLite Interpreter, or CMakeLists.txt + JNI bridge to svmPredict()).
Add debouncing, severity computation, GPS tagging.
Room entities: Detection, DriveSession. WorkManager batch upload.
Measure battery drain over a 30-minute drive; target < 8%.

Phase 4 — Backend and road graph (weeks 5–6)

API: POST /detections (batch), GET /segments?bbox= (roughness tiles), POST /route.
Map-matching job (OSRM /match or Valhalla Meili) → segment aggregation → roughness table.
Serve roughness as vector tiles for the heatmap layer.
Injecting roughness into routing: custom OSRM profile with a per-edge penalty table, or Valhalla's costing_options — Valhalla is the easier of the two for dynamic per-edge weights.

Phase 5 — Routing engine (weeks 6–7)

Implement the λ-sweep: binary search over λ, one route request per iteration, cache results per (origin, destination) pair.
Cap iterations at 10 and add a 500 ms budget; fall back to the last good λ.
Expose route(origin, dest, maxTime) → {polyline, eta, comfortScore, segmentRoughness[]}.
Ship the Directions-API post-filter as a feature-flagged fallback for areas with no coverage.

Phase 6 — UI (weeks 7–8)

Map screen, gradient polyline, pothole/heatmap layers, time slider with debounce, ETA + comfort metrics, delta chip.
Consent and onboarding flow.
Recording notification and controls.

Phase 7 — Evaluation (weeks 9–10)

Detection: drive a held-out route with video ground truth; report precision/recall per severity class and per vehicle type.
Routing: for ~20 origin-destination pairs, plot the (time, discomfort) Pareto curve; report mean discomfort reduction for a 10% time budget increase. This chart is your headline poster result.
Subjective: 8–10 riders rate comfort 1–5 on fastest vs comfort route, blind to which is which. Correlate with the computed score.
Usability: whether users understand the slider without explanation.
References
Selvam, A. Potholes — Realtime pothole detection on Android phone's IMU data. github.com/aswathselvam/Potholes (companion MATLAB repo: github.com/aswathselvam/pothole_MATLAB). SVM in C++ via Java NDK, 50 Hz accel + gyro, MATLAB Coder C export, JNI svmPredict(), orientation-calibration listed as open work. No license file published — verify permissions before reuse. 
GitHub
Silvister, S., Komandur, D., Kokate, S., Khochare, A., More, U., et al. Deep Learning Approach to Detect Potholes. MIT World Peace University, Pune. RMS of 10 accelerometer and gyroscope readings around the pothole instant, used to train an ANN with ReLU and binary cross-entropy. 
github
Pawar, K., Jagtap, S. & Bhoir, S. (2020). Efficient pothole detection using smartphone sensors. ITM Web of Conferences, 32, 03013. Neural network on accel + gyro data, 94.78% accuracy, 0.71 precision, 0.81 recall. Open access. 
itm-conferences
Mednis, A., Strazdins, G., Zviedris, R., Kanonirs, G. & Selavo, L. (2011). Real Time Pothole Detection Using Android Smartphones with Accelerometers. DCOSS 2011, pp. 1–6. The foundational citation for smartphone-accelerometer pothole detection. 
arXiv
Ozoglu, F. & Gökgöz, T. (2023). Detection of Road Potholes by Applying Convolutional Neural Network Method Based on Road Vibration Data. Sensors, 23(22), 9023. CNN on smartphone accelerometer and gyroscope vibration data, 93.24% validation accuracy. Useful if you prefer a raw-signal CNN over hand-crafted features. 
nih
Egaji, O. A. et al. (2021). Real-time machine learning-based approach for pothole detection. Expert Systems with Applications. Two Android apps — one recording accelerometer, gyroscope and GPS, one for labelling — with raw data pre-processed, merged, cleansed and split before feature extraction. A good template for your Phase 1 collection methodology. 
ScienceDirect
OSRM (BSD-2) and Valhalla (MIT) — open-source routing engines supporting custom edge costing and map matching.

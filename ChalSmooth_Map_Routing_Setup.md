# ChalSmooth — Maps & Routing Setup Guide

## 1. Objective

This document covers **only the map, geocoding, routing, and route-visualization layer** for the ChalSmooth Android Native application.

The existing ChalSmooth application structure can remain unchanged. The goal is to replace a Google Maps dependency with an open/free-oriented mapping stack:

```text
Android Native / Kotlin
        |
        +---- MapLibre Native
        |         |
        |         +---- Map rendering
        |         +---- Markers
        |         +---- Polylines
        |         +---- Camera
        |
        +---- OpenStreetMap-derived map data
        |
        +---- Geocoding provider
        |         |
        |         +---- Destination search
        |
        +---- OSRM
                  |
                  +---- Route calculation
                  +---- Distance
                  +---- Duration
                  +---- Route geometry
```

The important design principle is:

> **OSRM generates candidate routes; ChalSmooth evaluates those routes for comfort.**

Do not build a custom routing engine in the first version.

---

# 2. Recommended Technology Stack

| Requirement | Recommended technology |
|---|---|
| Android | Kotlin |
| Map SDK | MapLibre Native for Android |
| Map data | OpenStreetMap-derived data |
| Route engine | OSRM |
| Destination search | Geocoding service |
| Route visualization | MapLibre polyline |
| Current location | Android location APIs |
| Pothole markers | MapLibre annotations/layers |
| Road-quality visualization | MapLibre line layers |
| Google Maps API key | Not required for this stack |

Official resources:

- MapLibre Android: https://maplibre.org/maplibre-native/android/
- OpenStreetMap: https://www.openstreetmap.org/
- OSRM: https://project-osrm.org/
- Nominatim policy: https://operations.osmfoundation.org/policies/nominatim/

---

# 3. Architecture

The map subsystem should be isolated from the rest of the application.

```text
                         CHALSMOOTH
                             |
              +--------------+--------------+
              |                             |
          Existing App                Map Module
                                            |
                           +----------------+----------------+
                           |                |                |
                           v                v                v
                      MapLibre         Location          Routing
                           |                |                |
                           v                v                v
                    Map rendering      GPS position       OSRM
                           |                                 |
                           +----------------+----------------+
                                            |
                                            v
                                    Route candidates
                                            |
                                            v
                                  Comfort Score Engine
                                            |
                                            v
                                    Recommended Route
```

A suggested package structure is:

```text
com.chalsmooth
|
+-- map/
|   +-- MapScreen.kt
|   +-- MapController.kt
|   +-- MapStyleProvider.kt
|   +-- MapMarkerManager.kt
|   +-- RouteRenderer.kt
|
+-- location/
|   +-- LocationManager.kt
|   +-- LocationState.kt
|
+-- routing/
|   +-- RoutingService.kt
|   +-- OsrmRoutingService.kt
|   +-- Route.kt
|   +-- RouteLeg.kt
|
+-- geocoding/
|   +-- GeocodingService.kt
|   +-- GeocodingResult.kt
|
+-- comfort/
|   +-- ComfortScorer.kt
|   +-- RoadCondition.kt
|
+-- model/
|   +-- RoadEvent.kt
```

You do **not** have to create these exact packages if your existing structure differs. Keep your current architecture and introduce equivalent responsibilities.

---

# 4. MapLibre

## 4.1 Why MapLibre?

MapLibre provides the Android map rendering layer.

It handles:

- Map display
- Zoom
- Pan
- Camera movement
- Markers
- Lines
- Polygons
- Custom map styles
- Map interactions

It is an alternative to Google Maps SDK for the map-rendering portion.

Official documentation:

https://maplibre.org/maplibre-native/android/

---

# 5. Important: MapLibre Is Not the Map Data

MapLibre is a renderer.

It does not automatically mean:

```text
MapLibre = OpenStreetMap server
```

Instead:

```text
MapLibre
   |
   +---- Style
   |
   +---- Tiles / vector data
   |
   +---- Rendering
```

You need a suitable map style/tile source.

For development, use an OSM-derived tile/style provider that permits your intended usage.

Avoid treating the public OpenStreetMap tile servers as an unlimited production tile API.

OpenStreetMap tile policy:

https://operations.osmfoundation.org/policies/tiles/

---

# 6. OpenStreetMap

OpenStreetMap supplies the underlying geographic/road data ecosystem.

Website:

https://www.openstreetmap.org/

For ChalSmooth, this gives you the road network that routing services can work with.

Conceptually:

```text
OpenStreetMap data
       |
       v
Road network
       |
       v
Routing engine
       |
       v
Candidate routes
```

---

# 7. Routing — OSRM

## 7.1 What OSRM Does

OSRM is a routing engine.

Website:

https://project-osrm.org/

Use it to calculate normal driving routes between two coordinates.

Example:

```text
Current location
    |
    v
18.5204, 73.8567
    |
    v
Destination
    |
    v
18.5312, 73.8440
    |
    v
OSRM
    |
    +---- distance
    +---- duration
    +---- route geometry
```

---

# 8. OSRM Request

The general route endpoint has the form:

```text
/route/v1/driving/{longitude},{latitude};{longitude},{latitude}
```

For example:

```text
/route/v1/driving/73.8567,18.5204;73.8440,18.5312
```

Useful parameters include:

```text
overview=full
geometries=geojson
steps=true
alternatives=true
```

A practical request can therefore look conceptually like:

```text
/route/v1/driving/
73.8567,18.5204;
73.8440,18.5312
?overview=full
&geometries=geojson
&steps=true
&alternatives=true
```

Do not hard-code this exact URL into production without checking the routing server/provider you are using.

---

# 9. OSRM Response

The response contains route information such as:

```json
{
  "routes": [
    {
      "distance": 7200,
      "duration": 1260,
      "geometry": {
        "coordinates": [
          [73.8567, 18.5204],
          [73.8569, 18.5207]
        ]
      }
    }
  ]
}
```

The exact response depends on the parameters used.

Important fields:

```text
routes[]
    |
    +-- distance
    +-- duration
    +-- geometry
    +-- legs
    +-- steps
```

For ChalSmooth, the most important fields are:

```text
distance
duration
geometry
```

---

# 10. Do Not Let OSRM Decide the Comfortable Route

This is the core design decision.

Normal navigation:

```text
Start
  |
  v
OSRM
  |
  v
Shortest/fastest route
```

ChalSmooth:

```text
Start
  |
  v
OSRM
  |
  +-------- Route A
  |
  +-------- Route B
  |
  +-------- Route C
             |
             v
      ChalSmooth analysis
             |
      +------+------+
      |             |
   Potholes      Roughness
      |             |
      +------+------+
             |
             v
       Comfort Score
             |
             v
     Recommended Route
```

OSRM should generate the candidates.

**ChalSmooth chooses the route.**

---

# 11. Multiple Routes

Request alternative routes where supported by the routing setup.

Conceptually:

```text
                  Destination
                      |
              +-------+-------+
              |       |       |
              v       v       v
            Route A Route B Route C
              |       |       |
              v       v       v
           Analyze Analyze Analyze
              |       |       |
              +-------+-------+
                      |
                      v
               Comfort Score
                      |
                      v
                Best Route
```

Example:

| Route | Distance | Time | Potholes | Comfort |
|---|---:|---:|---:|---:|
| A | 7.2 km | 21 min | 12 | 52 |
| B | 8.1 km | 23 min | 3 | **87** |
| C | 6.8 km | 19 min | 19 | 41 |

A traditional navigator might select Route C.

ChalSmooth should select Route B if the user's comfort setting prioritizes road quality.

---

# 12. Geocoding

Geocoding converts a text destination into coordinates.

Example:

```text
User enters:
"Phoenix Mall Pune"

        |
        v

Geocoder

        |
        v

latitude: 18.xxxxx
longitude: 73.xxxxx
```

Reverse geocoding does the opposite:

```text
18.xxxxx, 73.xxxxx
        |
        v
"Road / Area / City"
```

---

# 13. Nominatim

Nominatim is commonly associated with OpenStreetMap search/geocoding.

However, do not build a production Android app that sends unrestricted/high-volume requests directly to the public Nominatim service.

Read the usage policy first:

https://operations.osmfoundation.org/policies/nominatim/

For a prototype, evaluate the policy and limits carefully.

For a production application, consider:

```text
Android App
    |
    v
Your Backend
    |
    v
Geocoding Provider
```

or use a provider with an appropriate developer/free tier.

---

# 14. Destination Search Flow

The Android flow should be:

```text
User opens search
       |
       v
Types destination
       |
       v
Debounce input
       |
       v
Geocoding request
       |
       v
Search results
       |
       v
User selects place
       |
       v
Save latitude/longitude
       |
       v
Move map camera
       |
       v
Request route
```

Do not send a geocoding request on every keystroke.

Use a debounce mechanism.

Example:

```text
P
Pu
Pun
Pune
Pune R
Pune Ra
Pune Rai
```

Instead of making seven requests, wait briefly after the user stops typing and then make one request.

---

# 15. Current Location

Use Android's location APIs for the user's location.

The flow:

```text
GPS / Location Provider
        |
        v
Latitude + Longitude
        |
        v
Location State
        |
        +----> Map camera
        |
        +----> User marker
        |
        +----> Routing origin
```

Required permissions normally include:

```xml
<uses-permission
    android:name="android.permission.ACCESS_FINE_LOCATION" />

<uses-permission
    android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

Request runtime permission correctly.

Do not assume permission is automatically granted.

---

# 16. Map Camera

The map should initially:

```text
1. Obtain current location
2. Center map on user
3. Show location marker
4. Allow user to move/zoom
```

When the destination is selected:

```text
Destination selected
        |
        v
Calculate route
        |
        v
Get route geometry
        |
        v
Fit camera to route bounds
```

The camera should ideally show the complete route rather than simply jumping to the destination.

---

# 17. Route Rendering

OSRM gives you route geometry.

The basic pipeline:

```text
OSRM
 |
 v
Geometry
 |
 v
Decode coordinates
 |
 v
Convert to MapLibre coordinates
 |
 v
Create line layer/source
 |
 v
Display route
```

Important:

OSRM coordinates are commonly represented as:

```text
[longitude, latitude]
```

Do not accidentally reverse them.

Map coordinates must be handled consistently as:

```text
longitude
latitude
```

rather than:

```text
latitude
longitude
```

A latitude/longitude swap can put the route in a completely wrong location.

---

# 18. Route Colors

Use route colors to communicate road quality.

Suggested convention:

```text
Green  = recommended / comfortable
Blue   = normal route
Yellow = moderate roughness
Orange = rough
Red    = severe / avoid
```

For example:

```text
Route A  ───────────── Blue

Route B  ───────────── Green
                      ↑
                  Recommended

Route C  ───────────── Red
                      ↑
                    Avoid
```

Do not rely only on color; add labels/icons/text for accessibility.

---

# 19. Pothole Markers

When your ML system detects a pothole:

```text
ML Detection
      |
      v
latitude
longitude
severity
confidence
      |
      v
RoadEvent
      |
      v
Map marker
```

Example model:

```kotlin
data class RoadEvent(
    val latitude: Double,
    val longitude: Double,
    val type: String,
    val severity: Float,
    val confidence: Float
)
```

Example:

```text
type       = POTHOLE
severity   = 0.87
confidence = 0.94
```

Display it on the map:

```text
             🔴
          Pothole
          87% severe
```

---

# 20. Road Condition Visualization

Instead of showing thousands of individual pothole icons, eventually use road-quality segments.

Conceptually:

```text
Road segment 1 → GOOD
Road segment 2 → MODERATE
Road segment 3 → POOR
Road segment 4 → GOOD
```

Display:

```text
🟢────────🟢
          \
           🟡──────🔴
                   \
                    🟢
```

This is more scalable than placing a marker for every event.

---

# 21. ChalSmooth Road Event Model

A useful model is:

```kotlin
data class RoadEvent(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val type: RoadEventType,
    val severity: Float,
    val confidence: Float,
    val timestamp: Long
)
```

Enum:

```kotlin
enum class RoadEventType {
    POTHOLE,
    ROUGH_ROAD,
    SPEED_BUMP,
    HARD_BRAKING,
    NORMAL
}
```

Keep the event model independent of MapLibre.

The map should only visualize it.

---

# 22. Route Model

Create an internal route model rather than passing raw OSRM JSON throughout the application.

Example:

```kotlin
data class Route(
    val id: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val coordinates: List<Pair<Double, Double>>,
    val potholeCount: Int = 0,
    val roughnessScore: Double = 0.0,
    val comfortScore: Double = 0.0
)
```

The raw OSRM response should be converted into this model.

Architecture:

```text
OSRM JSON
   |
   v
OSRM DTO
   |
   v
Route mapper
   |
   v
ChalSmooth Route
   |
   +---- Map
   +---- UI
   +---- Comfort Engine
```

This prevents the entire app from becoming dependent on OSRM's response format.

---

# 23. Comfort Score

The map layer should not calculate the final comfort score itself.

Use a separate component:

```text
RoutingService
      |
      v
Candidate Routes
      |
      v
ComfortScorer
      |
      v
Ranked Routes
      |
      v
MapRenderer
```

Example:

```kotlin
data class ComfortWeights(
    val timeWeight: Double,
    val distanceWeight: Double,
    val roadQualityWeight: Double,
    val safetyWeight: Double
)
```

---

# 24. Comfort Slider

Your UI can expose:

```text
FAST ------------------------- COMFORT
             ●
```

The slider modifies route weights.

Example:

### Fast

```text
Time:          50%
Distance:      30%
Comfort:       20%
```

### Balanced

```text
Time:          25%
Distance:      20%
Comfort:       55%
```

### Comfort

```text
Time:          10%
Distance:      10%
Comfort:       80%
```

The routing API does not need to know about the slider.

The slider belongs to ChalSmooth's route-ranking layer.

---

# 25. Example Comfort Algorithm

A simple first version:

```text
Comfort Score =
    0.50 × Road Quality
  + 0.25 × Safety
  + 0.15 × Smoothness
  + 0.10 × Time Efficiency
```

Normalize each metric to 0–100 before combining them.

Example:

```text
Route A

Road Quality      = 60
Safety            = 70
Smoothness        = 55
Time Efficiency   = 90

Score =
0.50(60)
+ 0.25(70)
+ 0.15(55)
+ 0.10(90)

= 64.25
```

The exact weights should be treated as tunable parameters.

---

# 26. Pothole Penalty

A simple first implementation can calculate a penalty:

```text
Pothole Penalty =
    number of potholes
    × average severity
```

For example:

```text
Route A:
12 potholes × 0.65 = 7.8 penalty

Route B:
3 potholes × 0.40 = 1.2 penalty
```

Then incorporate the penalty into road quality.

Do not permanently hard-code these values. Later, tune them using actual ride data.

---

# 27. Important: Distance Matching

A pothole should affect a route only if it is actually near that route.

Do not do this:

```text
Route B
+
ALL potholes in Pune
=
Route B score
```

Instead:

```text
Route geometry
      |
      v
Find nearby road events
      |
      v
Events within route corridor
      |
      v
Calculate route penalty
```

Use a spatial threshold, for example a configurable distance around the route geometry.

The exact threshold should be tested with your GPS accuracy and road geometry.

---

# 28. Complete Route Pipeline

The complete ChalSmooth map flow should be:

```text
                    USER
                     |
                     v
              Select destination
                     |
                     v
                Geocoding
                     |
                     v
             Destination GPS
                     |
                     v
            Current GPS location
                     |
                     v
                  OSRM
                     |
          +----------+----------+
          |          |          |
          v          v          v
       Route A    Route B    Route C
          |          |          |
          +----------+----------+
                     |
                     v
            Road event lookup
                     |
                     v
          Pothole / roughness data
                     |
                     v
              Comfort Scorer
                     |
                     v
              Ranked routes
                     |
                     v
            Recommended route
                     |
                     v
                 MapLibre
                     |
                     v
             User sees route
```

---

# 29. What Should Be Local vs Backend?

For the first prototype:

```text
Android
 |
 +-- MapLibre
 +-- GPS
 +-- Sensor data
 +-- ML inference
 +-- Basic road-event storage
 |
 +-- Routing API
```

Later:

```text
Android
 |
 v
Backend
 |
 +-- Road event database
 +-- Aggregation
 +-- Geocoding
 +-- Routing
 +-- Analytics
```

Do not add a backend just to display a map.

---

# 30. Recommended MVP

The first map milestone should contain only:

```text
[✓] MapLibre map
[✓] OSM-derived map style/tiles
[✓] Current location
[✓] Destination search
[✓] Destination marker
[✓] OSRM route
[✓] Route polyline
[✓] Distance
[✓] ETA
[✓] Recalculate route
```

Only after this works add:

```text
[ ] Multiple routes
[ ] Pothole markers
[ ] Road-quality segments
[ ] Comfort score
[ ] Comfort slider
[ ] Route ranking
```

---

# 31. Suggested Android Components

Keep the responsibilities separated.

## MapController

Responsible for:

```text
Map initialization
Camera
Map style
Map interaction
```

## LocationManager

Responsible for:

```text
Permissions
GPS updates
Current location
```

## GeocodingService

Responsible for:

```text
Text → coordinates
Coordinates → address
```

## RoutingService

Responsible for:

```text
Origin
Destination
OSRM request
OSRM parsing
Route model
```

## RouteRenderer

Responsible for:

```text
Route → MapLibre line
Route color
Route visibility
Route selection
```

## RoadEventManager

Responsible for:

```text
Pothole events
Markers
Road-quality segments
```

## ComfortScorer

Responsible for:

```text
Route ranking
Pothole penalties
Road quality
User preference
```

This separation will make it much easier to replace OSRM later if necessary.

---

# 32. Do Not Hard-Code API URLs Everywhere

Avoid:

```kotlin
val url = "https://router.../route..."
```

inside UI code.

Instead:

```text
UI
 |
 v
RoutingService
 |
 v
RoutingClient
 |
 v
OSRM
```

Keep endpoints in configuration.

This allows you to change:

```text
Public OSRM
       ↓
Self-hosted OSRM
       ↓
Valhalla
       ↓
Another routing provider
```

without rewriting the app.

---

# 33. Public Server vs Self-Hosted

For a student prototype:

```text
Android
   |
   v
Suitable hosted routing endpoint
   |
   v
OSRM
```

For a real production system:

```text
Android
   |
   v
Your backend
   |
   v
Self-hosted routing engine
```

Benefits of self-hosting:

- Better control
- No dependency on a public demo server
- Custom configuration
- Predictable capacity
- Easier integration with your road-quality database

But self-hosting requires infrastructure and map-data management.

---

# 34. Legal / Usage Considerations

Before deployment, check the current terms of every service you use.

Important services have separate policies:

OpenStreetMap:

https://www.openstreetmap.org/

OSM tile policy:

https://operations.osmfoundation.org/policies/tiles/

Nominatim:

https://operations.osmfoundation.org/policies/nominatim/

OSRM:

https://project-osrm.org/

MapLibre:

https://maplibre.org/

Do not assume that because software is open source, every public server can be used without limits.

---

# 35. Final Recommended Stack

For the first ChalSmooth prototype:

```text
                  CHALSMOOTH MAP STACK

Android Native / Kotlin
          |
          +--------------------------+
          |                          |
          v                          v
      MapLibre                    Android GPS
          |                          |
          v                          |
  OSM-derived map data              |
          |                          |
          +------------+-------------+
                       |
                       v
                 RoutingService
                       |
                       v
                     OSRM
                       |
                       v
                Candidate Routes
                       |
                       v
                ComfortScorer
                       |
          +------------+------------+
          |            |            |
          v            v            v
      Potholes     Roughness      Time
          |            |            |
          +------------+------------+
                       |
                       v
                 Best Route
                       |
                       v
                  MapLibre
                       |
                       v
                User Interface
```

---

# 36. Implementation Order

Implement the map subsystem in exactly this order:

### Step 1

Install/configure MapLibre.

### Step 2

Display an OSM-derived map style.

### Step 3

Get Android location permission.

### Step 4

Display current location.

### Step 5

Add destination search/geocoding.

### Step 6

Convert the selected destination into coordinates.

### Step 7

Call OSRM.

### Step 8

Parse the route response.

### Step 9

Draw the route on MapLibre.

### Step 10

Display distance and ETA.

### Step 11

Add alternative routes.

### Step 12

Create the `RoadEvent` model.

### Step 13

Display pothole/rough-road events.

### Step 14

Associate nearby road events with each candidate route.

### Step 15

Calculate comfort scores.

### Step 16

Rank routes.

### Step 17

Highlight the recommended route.

### Step 18

Connect the comfort slider.

---

# 37. Target Result

The final map experience should look conceptually like:

```text
┌─────────────────────────────────────────┐
│ ChalSmooth                         ⚙️   │
│                                         │
│  ┌───────────────────────────────────┐  │
│  │ Search destination...             │  │
│  └───────────────────────────────────┘  │
│                                         │
│             🟢─────────────             │
│            /               \            │
│       🟡──                   ──🟢       │
│      /                                  │
│     /       🔴 Pothole                 │
│    /                                    │
│   📍 YOU                                │
│                                         │
│  Comfort ─────────●────────── Fast      │
│                                         │
│  🟢 Recommended                         │
│  8.1 km • 23 min                        │
│  3 rough events                         │
│                                         │
│             [ START RIDE ]              │
└─────────────────────────────────────────┘
```

The key product behavior is:

```text
Google-style navigation:
"Which route gets me there fastest?"

ChalSmooth:
"Which route gets me there comfortably,
while considering road conditions?"
```

That distinction should remain at the center of the map/routing implementation.

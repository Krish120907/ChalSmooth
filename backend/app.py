import os
import math
import sqlite3
import time
from flask import Flask, request, jsonify

app = Flask(__name__)

DB_PATH = os.path.join(os.path.dirname(__file__), 'potholes.db')

def init_db():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS potholes (
            id TEXT PRIMARY KEY,
            latitude REAL NOT NULL,
            longitude REAL NOT NULL,
            label TEXT NOT NULL,
            confidence REAL NOT NULL,
            timestamp INTEGER NOT NULL,
            image_path TEXT
        )
    ''')
    conn.commit()
    conn.close()

init_db()

def haversine_distance(lat1, lon1, lat2, lon2):
    # Radius of earth in kilometers
    R = 6371.0
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = (math.sin(dlat / 2) ** 2 +
         math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) *
         math.sin(dlon / 2) ** 2)
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return R * c

@app.route('/api/health', methods=['GET'])
def health():
    return jsonify({"status": "ok", "message": "ChalSmooth Pothole Server is running"})

@app.route('/api/potholes', methods=['POST'])
def add_pothole():
    data = request.get_json(force=True, silent=True) or {}
    pothole_id = data.get('id')
    lat = data.get('latitude')
    lng = data.get('longitude')
    label = data.get('label', 'Pothole')
    confidence = data.get('confidence', 1.0)
    timestamp = data.get('timestamp', int(time.time() * 1000))

    if lat is None or lng is None or not pothole_id:
        return jsonify({"error": "Missing required fields: id, latitude, longitude"}), 400

    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    try:
        cursor.execute('''
            INSERT OR REPLACE INTO potholes (id, latitude, longitude, label, confidence, timestamp)
            VALUES (?, ?, ?, ?, ?, ?)
        ''', (pothole_id, float(lat), float(lng), label, float(confidence), int(timestamp)))
        conn.commit()
        return jsonify({"status": "success", "id": pothole_id}), 201
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()

@app.route('/api/potholes', methods=['GET'])
def get_potholes():
    user_lat = request.args.get('lat', type=float)
    user_lng = request.args.get('lng', type=float)
    radius_km = request.args.get('radius_km', default=10.0, type=float)

    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    cursor.execute('SELECT id, latitude, longitude, label, confidence, timestamp FROM potholes ORDER BY timestamp DESC')
    rows = cursor.fetchall()
    conn.close()

    results = []
    for row in rows:
        item = {
            "id": row["id"],
            "latitude": row["latitude"],
            "longitude": row["longitude"],
            "label": row["label"],
            "confidence": row["confidence"],
            "timestamp": row["timestamp"]
        }
        if user_lat is not None and user_lng is not None:
            dist = haversine_distance(user_lat, user_lng, item["latitude"], item["longitude"])
            if dist <= radius_km:
                item["distance_km"] = round(dist, 2)
                results.append(item)
        else:
            results.append(item)

    return jsonify(results)

if __name__ == '__main__':
    port = int(os.environ.get('PORT', 5000))
    print(f"Starting ChalSmooth Pothole Server on 0.0.0.0:{port}...")
    app.run(host='0.0.0.0', port=port, debug=True)

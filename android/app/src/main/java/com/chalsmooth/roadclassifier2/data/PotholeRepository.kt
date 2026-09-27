package com.chalsmooth.roadclassifier2.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

data class PotholeEntry(
    val id: String = UUID.randomUUID().toString(),
    val latitude: Double,
    val longitude: Double,
    val label: String,
    val confidence: Float,
    val imagePath: String,
    val timestamp: Long = System.currentTimeMillis()
)

class PotholeDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_POTHOLES (
                $COLUMN_ID TEXT PRIMARY KEY,
                $COLUMN_LATITUDE REAL NOT NULL,
                $COLUMN_LONGITUDE REAL NOT NULL,
                $COLUMN_LABEL TEXT NOT NULL,
                $COLUMN_CONFIDENCE REAL NOT NULL,
                $COLUMN_IMAGE_PATH TEXT,
                $COLUMN_TIMESTAMP INTEGER NOT NULL
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_POTHOLES")
        onCreate(db)
    }

    fun insertPothole(entry: PotholeEntry): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_ID, entry.id)
            put(COLUMN_LATITUDE, entry.latitude)
            put(COLUMN_LONGITUDE, entry.longitude)
            put(COLUMN_LABEL, entry.label)
            put(COLUMN_CONFIDENCE, entry.confidence)
            put(COLUMN_IMAGE_PATH, entry.imagePath)
            put(COLUMN_TIMESTAMP, entry.timestamp)
        }
        val result = db.insert(TABLE_POTHOLES, null, values)
        db.close()
        return result != -1L
    }

    fun getAllPotholes(): List<PotholeEntry> {
        val list = ArrayList<PotholeEntry>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_POTHOLES,
            null, null, null, null, null,
            "$COLUMN_TIMESTAMP DESC"
        )

        cursor.use { c ->
            val idIndex = c.getColumnIndexOrThrow(COLUMN_ID)
            val latIndex = c.getColumnIndexOrThrow(COLUMN_LATITUDE)
            val lngIndex = c.getColumnIndexOrThrow(COLUMN_LONGITUDE)
            val labelIndex = c.getColumnIndexOrThrow(COLUMN_LABEL)
            val confIndex = c.getColumnIndexOrThrow(COLUMN_CONFIDENCE)
            val pathIndex = c.getColumnIndexOrThrow(COLUMN_IMAGE_PATH)
            val timeIndex = c.getColumnIndexOrThrow(COLUMN_TIMESTAMP)

            while (c.moveToNext()) {
                val entry = PotholeEntry(
                    id = c.getString(idIndex),
                    latitude = c.getDouble(latIndex),
                    longitude = c.getDouble(lngIndex),
                    label = c.getString(labelIndex),
                    confidence = c.getFloat(confIndex),
                    imagePath = c.getString(pathIndex) ?: "",
                    timestamp = c.getLong(timeIndex)
                )
                list.add(entry)
            }
        }
        db.close()
        return list
    }

    companion object {
        private const val DATABASE_NAME = "chalsmooth_potholes.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_POTHOLES = "potholes"
        private const val COLUMN_ID = "id"
        private const val COLUMN_LATITUDE = "latitude"
        private const val COLUMN_LONGITUDE = "longitude"
        private const val COLUMN_LABEL = "label"
        private const val COLUMN_CONFIDENCE = "confidence"
        private const val COLUMN_IMAGE_PATH = "image_path"
        private const val COLUMN_TIMESTAMP = "timestamp"
    }
}

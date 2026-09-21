package com.chalsmooth.app.data.repository

import com.chalsmooth.app.data.model.Pothole
import com.chalsmooth.app.data.model.PotholeStatus
import com.chalsmooth.app.data.model.Severity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object PotholeRepository {
    private val _potholes = MutableStateFlow<List<Pothole>>(emptyList())
    val potholes: StateFlow<List<Pothole>> = _potholes.asStateFlow()

    init {
        _potholes.value = listOf(
            Pothole(
                id = "ph-101",
                title = "Deep Crater near University Circle Flyover",
                description = "Severe pothole right in center lane after flyover descent.",
                severity = Severity.CRITICAL,
                depthCm = 14.5,
                widthCm = 85.0,
                lat = 18.5362,
                lng = 73.8298,
                address = "Ganeshkhind Rd, Shivajinagar, Pune",
                roadName = "Ganeshkhind Road",
                lane = "Center Lane",
                status = PotholeStatus.VERIFIED,
                reportedDate = "2026-09-18",
                reportedByName = "Rohan Deshmukh",
                passesCount = 87,
                sensorPeakJerk = 4.8,
                upvotes = 42,
                photoUrl = "https://images.unsplash.com/photo-1515162816999-a0c47dc192f7?auto=format&fit=crop&w=600&q=80"
            ),
            Pothole(
                id = "ph-102",
                title = "Asphalt Trench on Baner High Street",
                description = "Repeated road surface erosion along storm drain junction.",
                severity = Severity.HIGH,
                depthCm = 9.8,
                widthCm = 60.0,
                lat = 18.5590,
                lng = 73.7795,
                address = "Baner Rd, opposite Balewadi Phata, Baner",
                roadName = "Baner Road",
                lane = "Left Lane",
                status = PotholeStatus.IN_PROGRESS,
                reportedDate = "2026-09-15",
                reportedByName = "Priya Sharma",
                passesCount = 142,
                sensorPeakJerk = 3.9,
                upvotes = 68,
                photoUrl = "https://images.unsplash.com/photo-1541888946425-d0fbb18086f6?auto=format&fit=crop&w=600&q=80"
            ),
            Pothole(
                id = "ph-103",
                title = "Sunken Utility Manhole on Senapati Bapat Road",
                description = "Sunken 7cm below asphalt grade. High vertical jar.",
                severity = Severity.MEDIUM,
                depthCm = 6.8,
                widthCm = 70.0,
                lat = 18.5284,
                lng = 73.8320,
                address = "Senapati Bapat Rd, near Chatushrungi Temple",
                roadName = "Senapati Bapat Road",
                lane = "Right Lane",
                status = PotholeStatus.VERIFIED,
                reportedDate = "2026-09-19",
                reportedByName = "Aditya Patil",
                passesCount = 94,
                sensorPeakJerk = 2.6,
                upvotes = 29,
                photoUrl = "https://images.unsplash.com/photo-1578844251758-2f71da64c96f?auto=format&fit=crop&w=600&q=80"
            ),
            Pothole(
                id = "ph-104",
                title = "Curbside Surface Pothole on FC Road",
                description = "Sharp edge pothole near Deccan bus stop curb.",
                severity = Severity.LOW,
                depthCm = 3.5,
                widthCm = 30.0,
                lat = 18.5222,
                lng = 73.8415,
                address = "Fergusson College Rd, Deccan Gymkhana",
                roadName = "FC Road",
                lane = "Left Lane",
                status = PotholeStatus.REPORTED,
                reportedDate = "2026-09-21",
                reportedByName = "Anjali Kulkarni",
                passesCount = 19,
                sensorPeakJerk = 1.4,
                upvotes = 9,
                photoUrl = "https://images.unsplash.com/photo-1590486803833-1c5dc8ddd4c8?auto=format&fit=crop&w=600&q=80"
            ),
            Pothole(
                id = "ph-105",
                title = "Repaired Patch - Hinjewadi Phase 1 Circle",
                description = "Former crater cluster successfully resurfaced.",
                severity = Severity.LOW,
                depthCm = 1.0,
                widthCm = 150.0,
                lat = 18.5912,
                lng = 73.7389,
                address = "Hinjewadi Main Rd, Phase 1 Circle",
                roadName = "Hinjewadi Main Road",
                lane = "All Lanes",
                status = PotholeStatus.FIXED,
                reportedDate = "2026-08-10",
                reportedByName = "Karan Mehta",
                passesCount = 310,
                sensorPeakJerk = 0.4,
                upvotes = 112,
                photoUrl = "https://images.unsplash.com/photo-1621905251189-08b45d6a269e?auto=format&fit=crop&w=600&q=80"
            )
        )
    }

    fun addPothole(pothole: Pothole) {
        val current = _potholes.value.toMutableList()
        current.add(0, pothole)
        _potholes.value = current
    }

    fun updateStatus(id: String, newStatus: PotholeStatus) {
        val current = _potholes.value.map { ph ->
            if (ph.id == id) ph.copy(status = newStatus) else ph
        }
        _potholes.value = current
    }

    fun upvote(id: String) {
        val current = _potholes.value.map { ph ->
            if (ph.id == id) ph.copy(upvotes = ph.upvotes + 1) else ph
        }
        _potholes.value = current
    }
}

package com.chalsmooth.app.data.model

enum class Severity(val label: String, val colorHex: Long, val impactDesc: String) {
    LOW("Low", 0xFFEAB308, "Minor surface dip (<4cm)"),
    MEDIUM("Medium", 0xFFF97316, "Moderate vertical jar (4-8cm)"),
    HIGH("High", 0xFFEF4444, "Severe crater (8-12cm)"),
    CRITICAL("Critical", 0xFFD946EF, "Dangerous deep collapse (>12cm)")
}

enum class PotholeStatus(val label: String, val stepIndex: Int) {
    REPORTED("Reported", 1),
    VERIFIED("Verified", 2),
    IN_PROGRESS("In Progress", 3),
    FIXED("Fixed", 4)
}

enum class RoadCondition(val label: String, val colorHex: Long) {
    GOOD("Good", 0xFF10B981),
    AVERAGE("Average", 0xFFEAB308),
    POOR("Poor", 0xFFF97316),
    DANGEROUS("Dangerous", 0xFFEF4444)
}

data class Pothole(
    val id: String,
    val title: String,
    val description: String,
    val severity: Severity,
    val depthCm: Double,
    val widthCm: Double,
    val lat: Double,
    val lng: Double,
    val address: String,
    val roadName: String,
    val lane: String,
    val status: PotholeStatus = PotholeStatus.REPORTED,
    val reportedDate: String,
    val reportedByName: String = "Citizen Driver",
    val passesCount: Int = 1,
    val sensorPeakJerk: Double = 3.5,
    val upvotes: Int = 0,
    val photoUrl: String = ""
)

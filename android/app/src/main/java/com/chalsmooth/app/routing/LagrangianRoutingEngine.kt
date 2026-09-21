package com.chalsmooth.app.routing

data class RouteCandidate(
    val id: String,
    val title: String,
    val distanceKm: Double,
    val durationMin: Int,
    val comfortScore: Int,
    val roughSegmentsCount: Int,
    val potholesEncountered: Int,
    val waypoints: List<Pair<Double, Double>>
)

data class RouteComparison(
    val fastest: RouteCandidate,
    val smoothest: RouteCandidate,
    val activeCandidate: RouteCandidate,
    val deltaMin: Int,
    val shockReductionPercent: Int
)

object LagrangianRoutingEngine {

    val punePresetFastest = RouteCandidate(
        id = "pune-fastest",
        title = "Via Old Mumbai-Pune Hwy & Wakad Flyover",
        distanceKm = 18.4,
        durationMin = 38,
        comfortScore = 58,
        roughSegmentsCount = 9,
        potholesEncountered = 11,
        waypoints = listOf(
            Pair(18.5289, 73.8744),
            Pair(18.5365, 73.8340),
            Pair(18.5580, 73.7850),
            Pair(18.5912, 73.7389)
        )
    )

    val punePresetSmoothest = RouteCandidate(
        id = "pune-smoothest",
        title = "Via Pashan-Sus Expressway Bypass",
        distanceKm = 20.8,
        durationMin = 42,
        comfortScore = 92,
        roughSegmentsCount = 2,
        potholesEncountered = 2,
        waypoints = listOf(
            Pair(18.5289, 73.8744),
            Pair(18.5220, 73.8520),
            Pair(18.5350, 73.7920),
            Pair(18.5520, 73.7650),
            Pair(18.5912, 73.7389)
        )
    )

    fun computeLambdaRoute(lambda: Float): RouteComparison {
        val l = lambda.coerceIn(0f, 1f)
        val interpolatedTime = (punePresetFastest.durationMin + (punePresetSmoothest.durationMin - punePresetFastest.durationMin) * l).toInt()
        val interpolatedComfort = (punePresetFastest.comfortScore + (punePresetSmoothest.comfortScore - punePresetFastest.comfortScore) * l).toInt()
        val roughSegments = (punePresetFastest.roughSegmentsCount - (punePresetFastest.roughSegmentsCount - punePresetSmoothest.roughSegmentsCount) * l).toInt()
        val potholes = (punePresetFastest.potholesEncountered - (punePresetFastest.potholesEncountered - punePresetSmoothest.potholesEncountered) * l).toInt()

        val active = if (l > 0.5f) {
            punePresetSmoothest.copy(durationMin = interpolatedTime, comfortScore = interpolatedComfort, roughSegmentsCount = roughSegments, potholesEncountered = potholes)
        } else {
            punePresetFastest.copy(durationMin = interpolatedTime, comfortScore = interpolatedComfort, roughSegmentsCount = roughSegments, potholesEncountered = potholes)
        }

        val deltaMin = interpolatedTime - punePresetFastest.durationMin
        val shockReduction = (((punePresetFastest.roughSegmentsCount - roughSegments).toDouble() / punePresetFastest.roughSegmentsCount) * 100).toInt()

        return RouteComparison(
            fastest = punePresetFastest,
            smoothest = punePresetSmoothest,
            activeCandidate = active,
            deltaMin = deltaMin,
            shockReductionPercent = shockReduction
        )
    }
}

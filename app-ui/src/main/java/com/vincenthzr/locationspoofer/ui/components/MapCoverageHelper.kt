package com.vincenthzr.locationspoofer.ui.components

import android.graphics.Color.argb
import com.vincenthzr.locationspoofer.data.db.LocationRecord
import kotlin.math.cos
import com.vincenthzr.locationspoofer.ui.theme.success
import androidx.compose.material3.MaterialTheme

object MapCoverageHelper {
    fun drawCoverage(
        controller: AppMapController,
        locations: List<LocationRecord>,
        centerLat: Double? = null,
        centerLng: Double? = null
    ) {
        if (locations.isEmpty()) return

        val fillColor = argb(50, 46, 204, 113)
        val strokeColor = argb(100, 46, 204, 113)

        val targetList = if (locations.size > 180) {
            downsampleLocations(
                locations = locations,
                centerLat = centerLat,
                centerLng = centerLng,
                maxCircles = 180,
                minDistanceMeters = 30.0
            )
        } else {
            locations
        }

        targetList.forEach { loc ->
            controller.addCircle(loc.lat, loc.lng, 50.0, fillColor, strokeColor, 2f)
        }
    }

    private fun downsampleLocations(
        locations: List<LocationRecord>,
        centerLat: Double?,
        centerLng: Double?,
        maxCircles: Int,
        minDistanceMeters: Double
    ): List<LocationRecord> {
        val sortedList = if (centerLat != null && centerLng != null) {
            locations.sortedBy { loc ->
                val dLat = loc.lat - centerLat
                val dLng = loc.lng - centerLng
                dLat * dLat + dLng * dLng
            }
        } else {
            locations.reversed()
        }

        val result = ArrayList<LocationRecord>(maxCircles)
        val minDistanceSq = (minDistanceMeters / 111320.0).let { it * it }

        for (loc in sortedList) {
            if (result.size >= maxCircles) break
            var isTooClose = false
            for (existing in result) {
                val dLat = loc.lat - existing.lat
                val dLng = (loc.lng - existing.lng) * cos(Math.toRadians(loc.lat))
                if (dLat * dLat + dLng * dLng < minDistanceSq) {
                    isTooClose = true
                    break
                }
            }
            if (!isTooClose) {
                result.add(loc)
            }
        }
        return result
    }
}

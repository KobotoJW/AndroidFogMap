package edu.put.fogmap
import com.google.android.gms.maps.model.LatLng
fun createCircle(center: LatLng, radius: Double, numPoints: Int): List<LatLng> {
    val points = mutableListOf<LatLng>()
    val radiusAngle = Math.PI * 2 / numPoints

    for (i in 0 until numPoints) {
        val theta = radiusAngle * i
        val x = radius * Math.cos(theta)
        val y = radius * Math.sin(theta)

        val xPoint = center.longitude + x / (111111 * Math.cos(center.latitude * Math.PI / 180))
        val yPoint = center.latitude + y / 111111

        points.add(LatLng(yPoint, xPoint))
    }

    return points
}


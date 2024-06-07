package edu.put.fogmap

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.PolygonOptions
import com.google.android.gms.maps.model.PolylineOptions
import kotlin.math.atan2

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mutablePolygon: Polygon
    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    private val visitedLocations = mutableListOf<LatLng>() // List to store visited locations

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val FOG_RADIUS_METERS = 30.0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Create location request
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(1000)
            .setMaxUpdateDelayMillis(10000)
            .build()

        // Location callback to handle location updates
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (locationResult.locations.isNotEmpty()) {
                    val location = locationResult.locations.last() // Get the last location
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    Log.d("MapsActivity", "Location updated: $currentLatLng")

                    // Add the new location to the visited locations list
                    visitedLocations.add(currentLatLng)
                    Log.d("MapsActivity", "Visited locations: $visitedLocations")

                    // Update the heat map
                    updateHeatMap()
                }
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap

        // Check for location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        } else {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            googleMap.isMyLocationEnabled = true
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun calculateDistance(point1: LatLng, point2: LatLng): Double {
        val results = FloatArray(1)
        Location.distanceBetween(point1.latitude, point1.longitude, point2.latitude, point2.longitude, results)
        return results[0].toDouble()
    }

    private fun updateHeatMap() {
        // Clear all previous polygons and markers
        googleMap.clear()

        if (visitedLocations.isEmpty()) return

        val radiusInDegrees = FOG_RADIUS_METERS / 111000.0 // Approximate conversion from meters to degrees

        val bufferedPoints = mutableListOf<LatLng>()
        val bufferedPolygons = mutableListOf<List<LatLng>>()

        visitedLocations.forEach { location ->
            val north = location.latitude + radiusInDegrees
            val south = location.latitude - radiusInDegrees
            val east = location.longitude + radiusInDegrees / Math.cos(Math.toRadians(location.latitude))
            val west = location.longitude - radiusInDegrees / Math.cos(Math.toRadians(location.latitude))

            // Create a buffer rectangle around the point
            val bufferPolygon = listOf(
                LatLng(north, west),
                LatLng(north, east),
                LatLng(south, east),
                LatLng(south, west)
            )
            bufferedPolygons.add(bufferPolygon)
        }

        // Merge all buffer polygons into a single polygon
        // For simplicity, assume that all buffer polygons form a convex hull
        bufferedPoints.addAll(bufferedPolygons.flatten())
        val mergedPolygonPoints = convexHull(bufferedPoints)

        // Create a polygon around all visited locations
        val polygonOptions = PolygonOptions().apply {
            addAll(mergedPolygonPoints)
            fillColor(0x60aa0000.toInt()) // Set the fill color with fixed alpha value
            strokeColor(0x00000000) // No stroke
        }
        googleMap.addPolygon(polygonOptions)

        // Add markers for each point in the polygon
        mergedPolygonPoints.forEach { point ->
            googleMap.addMarker(MarkerOptions().position(point).title("Polygon Point"))
        }
    }

    private fun convexHull(points: List<LatLng>): List<LatLng> {
        if (points.size < 3) return points

        val sortedPoints = points.sortedWith(compareBy({ it.longitude }, { it.latitude }))
        val lower = mutableListOf<LatLng>()
        val upper = mutableListOf<LatLng>()

        for (point in sortedPoints) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], point) <= 0) {
                lower.removeAt(lower.size - 1)
            }
            lower.add(point)
        }

        for (point in sortedPoints.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], point) <= 0) {
                upper.removeAt(upper.size - 1)
            }
            upper.add(point)
        }

        lower.removeAt(lower.size - 1)
        upper.removeAt(upper.size - 1)
        return lower + upper
    }

    private fun cross(o: LatLng, a: LatLng, b: LatLng): Double {
        return (a.longitude - o.longitude) * (b.latitude - o.latitude) - (a.latitude - o.latitude) * (b.longitude - o.longitude)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startLocationUpdates()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
    }
}

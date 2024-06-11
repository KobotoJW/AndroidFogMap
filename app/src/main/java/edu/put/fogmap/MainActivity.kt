package edu.put.fogmap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.PolygonOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.cos

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    private val visitedLocations = mutableListOf<LatLng>()
    private val drawnPolygons = mutableListOf<Polygon>()
    private lateinit var firestore: FirebaseFirestore

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val FOG_RADIUS_METERS = 30.0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContentView(R.layout.activity_main)

        firestore = FirebaseFirestore.getInstance()

        clearMap()
        loadVisitedLocations()

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

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

                    // Add the new location to the visited locations list
                    visitedLocations.add(currentLatLng)

                    // Save the location to Firebase
                    saveVisitedLocation(currentLatLng)

                    // Update the map
                    updateMap()
                }
            }

        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_login -> {
                startActivity(Intent(this, LoginActivity::class.java))
                true
            }
            R.id.action_register -> {
                startActivity(Intent(this, RegisterActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap

        // Check for location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
            loadVisitedLocations()
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

    private fun updateMap() {
        if (visitedLocations.isEmpty()) return

        val radiusInDegrees = FOG_RADIUS_METERS / 111000.0 // Approximate conversion from meters to degrees
        val newPolygons = mutableListOf<List<LatLng>>()

        visitedLocations.forEach { location ->
            val north = location.latitude + radiusInDegrees
            val south = location.latitude - radiusInDegrees
            val east = location.longitude + radiusInDegrees / cos(Math.toRadians(location.latitude))
            val west = location.longitude - radiusInDegrees / cos(Math.toRadians(location.latitude))

            // Create a buffer rectangle around the point
            val bufferPolygon = listOf(
                LatLng(north, west),
                LatLng(north, east),
                LatLng(south, east),
                LatLng(south, west)
            )

            // Check if the new polygon overlaps with any existing polygon
            val isOverlap = drawnPolygons.any { polygon ->
                bufferPolygon.any { point -> isPointInPolygon(point, polygon.points) }
            }

            if (!isOverlap) {
                newPolygons.add(bufferPolygon)
            }
        }

        // Draw all the new polygons without clearing the map
        newPolygons.forEach { polygonPoints ->
            val polygonOptions = PolygonOptions().apply {
                addAll(polygonPoints)
                fillColor(0x60aa0000.toInt()) // Set the fill color with fixed alpha value
                strokeColor(0x00000000) // No stroke
            }
            val polygon = googleMap.addPolygon(polygonOptions)
            drawnPolygons.add(polygon)
        }
    }

    private fun isPointInPolygon(point: LatLng, polygonPoints: List<LatLng>): Boolean {
        var isInside = false
        val n = polygonPoints.size
        var j = n - 1
        for (i in 0 until n) {
            val vertex1 = polygonPoints[i]
            val vertex2 = polygonPoints[j]
            if (vertex1.longitude < point.longitude && vertex2.longitude >= point.longitude
                || vertex2.longitude < point.longitude && vertex1.longitude >= point.longitude) {
                if (vertex1.latitude + (point.longitude - vertex1.longitude) /
                    (vertex2.longitude - vertex1.longitude) * (vertex2.latitude - vertex1.latitude) < point.latitude) {
                    isInside = !isInside
                }
            }
            j = i
        }
        return isInside
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

    private fun saveVisitedLocation(location: LatLng) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val locationMap = hashMapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude
        )

        firestore.collection("users").document(userId).collection("locations").add(locationMap)
    }

    private fun loadVisitedLocations() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        clearMap()

        firestore.collection("users").document(userId).collection("locations")
            .get()
            .addOnSuccessListener { documents ->
                visitedLocations.clear()
                for (document in documents) {
                    val locationMap = document.data
                    val latitude = locationMap["latitude"] as Double
                    val longitude = locationMap["longitude"] as Double
                    visitedLocations.add(LatLng(latitude, longitude))
                }
                updateMap()
            }
            .addOnFailureListener { exception ->
                Log.w("MapsActivity", "loadVisitedLocations:onFailure", exception)
            }
    }

    private fun clearMap() {
        drawnPolygons.forEach { it.remove() }
        drawnPolygons.clear()
        visitedLocations.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
    }
}

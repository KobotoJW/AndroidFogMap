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
        private const val FOG_RADIUS_METERS = 15.0
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

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (locationResult.locations.isNotEmpty()) {
                    val location = locationResult.locations.last()
                    val currentLatLng = LatLng(location.latitude, location.longitude)

                    visitedLocations.add(currentLatLng)

                    saveVisitedLocation(currentLatLng)

                    updateMap()
                    Log.d("VisitedLocations", visitedLocations.toString())
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

    private fun updateMap() {
        if (visitedLocations.isEmpty()) return

        val radiusInDegrees = FOG_RADIUS_METERS / 111000.0
        val newPolygons = mutableListOf<List<LatLng>>()

        val operationalList = visitedLocations.toMutableList()

        operationalList.forEach { location ->
            val north = location.latitude + radiusInDegrees
            val south = location.latitude - radiusInDegrees
            val east = location.longitude + radiusInDegrees / cos(Math.toRadians(location.latitude))
            val west = location.longitude - radiusInDegrees / cos(Math.toRadians(location.latitude))

            val bufferPolygon = listOf(
                LatLng(north, west),
                LatLng(north, east),
                LatLng(south, east),
                LatLng(south, west)
            )

            newPolygons.add(bufferPolygon)
            visitedLocations.remove(location)
        }
        operationalList.clear()

        newPolygons.forEach { polygonPoints ->
            val polygonOptions = PolygonOptions().apply {
                addAll(polygonPoints)
                fillColor(0xffaa0000.toInt())
                strokeColor(0x00000000)
            }
            val polygon = googleMap.addPolygon(polygonOptions)
            drawnPolygons.add(polygon)
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

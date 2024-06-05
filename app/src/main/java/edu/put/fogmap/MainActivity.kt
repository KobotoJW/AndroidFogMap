package edu.put.fogmap

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.PolygonOptions
import java.util.Arrays

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mutablePolygon: Polygon
    private val center = LatLng(0.0, 0.0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        // Define coordinates for the polygon covering a large area (e.g., a rough rectangle)
        val polygonCoords = listOf(
            LatLng(85.0, -180.0),  // Top left corner
            LatLng(85.0, 180.0),   // Top right corner
            LatLng(-85.0, 180.0),  // Bottom right corner
            LatLng(-85.0, -180.0), // Bottom left corner
            LatLng(85.0, -180.0)   // Close the loop by repeating the first point
        )

        // Create and add the polygon to the map
        mutablePolygon = googleMap.addPolygon(PolygonOptions().apply {
            add(LatLng(85.0, 90.0), LatLng(85.0,0.1),
            LatLng(85.0,-90.0), LatLng(85.0,-179.9),
            LatLng(0.0,-179.9), LatLng(-85.0,-179.9),
            LatLng(-85.0,-90.0), LatLng(-85.0,0.1),
            LatLng(-85.0,90.0), LatLng(-85.0,179.9),
            LatLng(0.0,179.9), LatLng(85.0,179.9))
//            addHole(createRectangle(LatLng(-22.0, 128.0), 1.0, 1.0))
//            addHole(createRectangle(LatLng(-18.0, 133.0), 0.5, 1.5))
            fillColor(0xff000000.toInt())
            strokeColor(0xff000000.toInt())
        })

        // Move the camera to show the polygon (centered on the world)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(0.0, 0.0), 5.0f))
    }

    private fun createRectangle(
        center: LatLng,
        halfWidth: Double,
        halfHeight: Double
    ): List<LatLng> {
        return Arrays.asList(
            LatLng(center.latitude - halfHeight, center.longitude - halfWidth),
            LatLng(center.latitude - halfHeight, center.longitude + halfWidth),
            LatLng(center.latitude + halfHeight, center.longitude + halfWidth),
            LatLng(center.latitude + halfHeight, center.longitude - halfWidth)
            )
    }
}

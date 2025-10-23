package com.example.datadomeapp

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
// START: ADDED GOOGLE MAPS IMPORTS TO RESOLVE ERRORS
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
// END: ADDED GOOGLE MAPS IMPORTS

class SchoolMapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private val TAG = "SchoolMapActivity"

    // Default coordinates (based on your input "76 Greene St, New York")
    private val DEFAULT_LAT = 40.7259f
    private val DEFAULT_LON = -74.0003f
    private val DEFAULT_NAME = "DataDome School (Default Location)"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure you have a layout file named activity_school_map.xml
        setContentView(R.layout.activity_school_map)

        // Initialize the map fragment
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // Retrieve coordinates and school name saved from MainActivity
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        val latitude = prefs.getFloat("school_lat", DEFAULT_LAT)
        val longitude = prefs.getFloat("school_lon", DEFAULT_LON)
        val schoolName = prefs.getString("school_name", DEFAULT_NAME) ?: DEFAULT_NAME

        val schoolLatLng = LatLng(latitude.toDouble(), longitude.toDouble())

        // --- Marker Setup ---

        // Add a marker at the retrieved location
        googleMap.addMarker(
            MarkerOptions()
                .position(schoolLatLng)
                .title(schoolName)
        )?.showInfoWindow() // Show the name immediately

        // --- Camera Setup ---

        // Move the map's camera to the location and zoom in (15f is a good zoom level)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(schoolLatLng, 15f))

        // Optional: Add map UI controls
        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.uiSettings.isCompassEnabled = true

        Log.d(TAG, "Map initialized for: $schoolName at ($latitude, $longitude)")
        Toast.makeText(this, "Showing location for: $schoolName", Toast.LENGTH_LONG).show()
    }
}

package com.example.taller3icm

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.taller3icm.databinding.ActivityTrackUserBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.database.*

class TrackUserActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityTrackUserBinding
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var currentLocation: Location? = null
    private var targetMarker: Marker? = null
    private var line: Polyline? = null
    private lateinit var dbRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrackUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val userUid = intent.getStringExtra("userUid") ?: return
        dbRef = FirebaseDatabase.getInstance().getReference("users").child(userUid)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        getCurrentUserLocation()
        startListeningToUserLocation()

        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 5000
        ).setMinUpdateIntervalMillis(2000).build()

        locationCallback = createLocationCallback()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    private fun getCurrentUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                currentLocation = location
                val myLatLng = LatLng(location.latitude, location.longitude)
                mMap.addMarker(MarkerOptions().position(myLatLng).title("Tu ubicación"))
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(myLatLng, 15f))
            }
        }
    }

    private fun startListeningToUserLocation() {
        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lat = snapshot.child("latitud").getValue(Double::class.java)
                val lon = snapshot.child("longitud").getValue(Double::class.java)

                if (lat != null && lon != null) {
                    updateTargetLocation(LatLng(lat, lon))
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Manejo de errores
            }
        })
    }

    private fun createLocationCallback(): LocationCallback {
        return object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                val location = result.lastLocation ?: return
                currentLocation = location
                targetMarker?.position?.let {
                    updateTargetLocation(it)
                }
            }
        }
    }

    private fun updateTargetLocation(latLng: LatLng) {
        if (targetMarker == null) {
            targetMarker = mMap.addMarker(
                MarkerOptions().position(latLng).title("Usuario seguido")
            )
        } else {
            targetMarker!!.position = latLng
        }

        currentLocation?.let { myLoc ->
            val myLatLng = LatLng(myLoc.latitude, myLoc.longitude)

            line?.remove()

            line = mMap.addPolyline(
                PolylineOptions()
                    .add(myLatLng, latLng)
                    .width(5f)
                    .color(Color.BLUE)
            )

            val targetLoc = Location("target").apply {
                latitude = latLng.latitude
                longitude = latLng.longitude
            }

            val distancia = myLoc.distanceTo(targetLoc).toInt()
            binding.textViewDistance.text = "Distancia: $distancia m"

            val bounds = LatLngBounds.Builder()
                .include(myLatLng)
                .include(latLng)
                .build()

            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}

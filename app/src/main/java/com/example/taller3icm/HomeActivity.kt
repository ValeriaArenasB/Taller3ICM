package com.example.taller3icm

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.taller3icm.databinding.ActivityHomeBinding
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Marker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import org.json.JSONObject
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin


class HomeActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: ActivityHomeBinding

    private lateinit var mMap: GoogleMap

    private var userMarker: Marker? = null
    private var isFirstLocationUpdate = true
    private var optionsMenu: Menu? = null
    private var isAvailable = false

    lateinit var locationClient: FusedLocationProviderClient
    private var posActual : Location?= null

    lateinit var locationRequest : LocationRequest
    lateinit var locationCallback: LocationCallback

    private lateinit var auth: FirebaseAuth


    val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(), ActivityResultCallback {
            if(it){ locationSettings()
            }else {
                Toast.makeText(this, "Permiso no concedido", Toast.LENGTH_LONG).show()
            }
        })

    private val locationSettings = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
        ActivityResultCallback {
            if (it.resultCode == RESULT_OK) {
                startLocationUpdates()
            } else {
                Toast.makeText(this, "No se inició el GPS", Toast.LENGTH_SHORT).show()
            }
        }
    )



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        locationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = locationRequest()
        locationCallback = createLocationCallBack()

        auth = FirebaseAuth.getInstance()
        locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)

        val userId = auth.currentUser?.uid
        FirebaseDatabase.getInstance().getReference("users").child(userId ?: return)
            .child("available").get().addOnSuccessListener {
                isAvailable = it.getValue(Boolean::class.java) == true }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        startService(Intent(this, UserAvailabilityService::class.java))
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        readLocations()
    }

    fun readLocations() {
        val jsonString = this.assets.open("locations.json").bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonString)
        val locationsObject = jsonObject.getJSONObject("locations")

        for (key in locationsObject.keys()) {
            val location = locationsObject.getJSONObject(key)
            val latitude = location.getDouble("latitude")
            val longitude = location.getDouble("longitude")
            val name = location.getString("name")

            Log.i("Location", "Location: $name, $latitude, $longitude")
            val latLng = LatLng(latitude, longitude)
            drawMarker(latLng, name, R.drawable.baseline_existing_location)
        }
    }

    fun locationRequest() : LocationRequest {
        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setWaitForAccurateLocation(true)
            .setMinUpdateIntervalMillis(5000)
            .build()
        return locationRequest
    }


    fun drawMarker(location : LatLng, description : String?, icon: Int){
        val addressMarker = mMap.addMarker(MarkerOptions().position(location).icon(bitmapDescriptorFromVector(this,
            icon)))!!
        if(description!=null){
            addressMarker.title=description
        }
        mMap.moveCamera(CameraUpdateFactory.newLatLng(location))
        mMap.moveCamera(CameraUpdateFactory.zoomTo(16f))
    }

    fun bitmapDescriptorFromVector(context : Context, vectorResId : Int) : BitmapDescriptor {
        val vectorDrawable : Drawable = ContextCompat.getDrawable(context, vectorResId)!!
        vectorDrawable.setBounds(0, 0, vectorDrawable.getIntrinsicWidth(), vectorDrawable.getIntrinsicHeight());
        val bitmap = Bitmap.createBitmap(vectorDrawable.getIntrinsicWidth(), vectorDrawable.getIntrinsicHeight(),
            Bitmap.Config.ARGB_8888);
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }



    fun createLocationCallBack(): LocationCallback {
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                val location = result.lastLocation ?: return

                //cambia ubicación si la nueva que llega es muy diferente
                if (posActual == null || distance(LatLng(posActual!!.latitude, posActual!!.longitude), location) > 0.001) {
                    posActual = location
                }

                val userId = auth.currentUser?.uid ?: return
                val database = FirebaseDatabase.getInstance().getReference("users").child(userId)

                database.get().addOnSuccessListener { dataSnapshot ->
                    val userName = dataSnapshot.child("nombre").getValue(String::class.java) ?: "Usuario"
                    val userLastname = dataSnapshot.child("apellido").getValue(String::class.java) ?: ""
                    val fullName = "$userName $userLastname".trim()

                    userMarker?.remove()
                    userMarker = mMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(location.latitude, location.longitude))
                            .title(fullName)
                            .icon(bitmapDescriptorFromVector(this@HomeActivity, R.drawable.baseline_my_location))
                    )

                    val latLng = LatLng(location.latitude, location.longitude)
                    mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng))

                    database.child("latitud").setValue(location.latitude)
                    database.child("longitud").setValue(location.longitude)

                }.addOnFailureListener {
                    Toast.makeText(this@HomeActivity, "Error obteniendo nombre de usuario", Toast.LENGTH_SHORT).show()
                }
            }

        }
        return locationCallback
    }


    fun distance( longpress : LatLng , actual : Location) : Float {
        val pk = (180f / Math.PI).toFloat()

        val a1: Double = longpress.latitude / pk
        val a2: Double = longpress.longitude / pk
        val b1: Double = actual.latitude / pk
        val b2: Double = actual.longitude / pk

        val t1 = cos(a1) * cos(a2) * cos(b1) * cos(b2)
        val t2 = cos(a1) * sin(a2) * cos(b1) * sin(b2)
        val t3 = sin(a1) * sin(b1)
        val tt = acos(t1 + t2 + t3)

        return (6366000 * tt).toFloat()
    }


    private fun locationSettings() {
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())
        task.addOnSuccessListener { locationSettingsResponse ->
            startLocationUpdates()
        }
        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    val isr = IntentSenderRequest.Builder(exception.resolution).build()
                    locationSettings.launch(isr)
                } catch (sendEx: IntentSender.SendIntentException) {
                }
            }
        }
    }


    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        optionsMenu = menu
        availabilityFirebase()
        return true
    }

    // cambia lo que dice el menu según el estado del usuario
    private fun availabilityFirebase() {
        val userId = auth.currentUser?.uid ?: return
        val dbRef = FirebaseDatabase.getInstance().getReference("users").child(userId)

        dbRef.child("available").get().addOnSuccessListener { snapshot ->
            isAvailable = snapshot.getValue(Boolean::class.java) == true

            val toggleItem = optionsMenu?.findItem(R.id.toggleAvailability)
            val icon = if (isAvailable) R.drawable.ic_online else R.drawable.ic_offline
            val title = if (isAvailable) "Cambiar a desconectado" else "Cambiar a disponible"

            toggleItem?.icon = ContextCompat.getDrawable(this, icon)
            toggleItem?.title = title
        }
    }



    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.signOut -> {
                auth.signOut()
                Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)
                finish()
                return true
            }

            R.id.toggleAvailability -> {
                isAvailable = !isAvailable
                updateAvailability(isAvailable)

                val icon = if (isAvailable) R.drawable.ic_online else R.drawable.ic_offline
                val title = if (isAvailable) "Cambiar a desconectado" else "Cambiar a disponible"
                item.setIcon(icon)
                item.title = title
                return true
            }

            R.id.viewAvailableUsers -> {
                startActivity(Intent(this, ListUsersActivity::class.java))
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }



    private fun updateAvailability(isAvailable: Boolean) {
        val userId = auth.currentUser?.uid ?: return
        val database = FirebaseDatabase.getInstance().getReference("users").child(userId)
        database.child("available").setValue(isAvailable)
            .addOnSuccessListener {
                val msg = if (isAvailable) "Estás disponible" else "No estás disponible"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error actualizando estado", Toast.LENGTH_SHORT).show()
            }
    }



}
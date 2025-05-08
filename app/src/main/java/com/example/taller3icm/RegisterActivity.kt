package com.example.taller3icm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.taller3icm.databinding.ActivityRegisterBinding
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.io.File

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private val database = FirebaseDatabase.getInstance().reference
    private val storage = FirebaseStorage.getInstance()
    private var profileImageUri: Uri? = null

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) profileImageUri?.let { binding.profilePhoto.setImageURI(it) }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            profileImageUri = it
            binding.profilePhoto.setImageURI(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.takePic.setOnClickListener { openCamera() }
        binding.gallery.setOnClickListener { openGallery() }

        binding.registroButtom.setOnClickListener {
            if (hasLocationPermission()) {
                registerUser()
            } else {
                requestLocationPermission()
            }
        }

        binding.iniciarsesionbutton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
    }

    private fun openCamera() {
        try {
            val file = File(filesDir, "profilePic.jpg")
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            profileImageUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(this, "Error al abrir la cámara: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun registerUser() {
        val nombre = binding.nombre.text.toString().trim()
        val apellido = binding.apellido.text.toString().trim()
        val email = binding.correo.text.toString().trim()
        val password = binding.contrasena.text.toString().trim()
        val id = binding.userId.text.toString().trim()

        if (nombre.isEmpty() || apellido.isEmpty() || email.isEmpty() || password.isEmpty() || id.isEmpty()) {
            Toast.makeText(this, "Todos los campos son obligatorios", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
            return
        }

        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val userId = auth.currentUser?.uid ?: return@addOnCompleteListener
                profileImageUri?.let { uri ->
                    uploadImage(userId, uri) { url ->
                        getLocationAndRegister(userId, nombre, apellido, email, password, id, url)
                    }
                } ?: getLocationAndRegister(userId, nombre, apellido, email, password, id, null)
            } else {
                Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun uploadImage(userId: String, uri: Uri, callback: (String?) -> Unit) {
        val storageRef = storage.reference.child("profileImages/$userId.jpg")
        storageRef.putFile(uri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { callback(it.toString()) }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error subiendo imagen", Toast.LENGTH_SHORT).show()
                callback(null)
            }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun getLocationAndRegister(
        userId: String,
        nombre: String,
        apellido: String,
        email: String,
        password: String,
        id: String,
        imageUrl: String?
    ) {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location == null) {
                Toast.makeText(this, "No se pudo obtener la ubicación", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            val user = MyUser(
                nombre = nombre,
                apellido = apellido,
                email = email,
                password = password,
                imagen = imageUrl,
                id = id,
                latitud = location.latitude,
                longitud = location.longitude,
                available = false
            )

            database.child("users").child(userId).setValue(user).addOnCompleteListener { dbTask ->
                if (dbTask.isSuccessful) {
                    Toast.makeText(this, "Registro exitoso", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Error guardando usuario", Toast.LENGTH_SHORT).show()
                }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "No se pudo obtener ubicación", Toast.LENGTH_SHORT).show()
        }
    }
}

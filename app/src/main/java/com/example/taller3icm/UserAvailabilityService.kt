package com.example.taller3icm

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import com.google.firebase.database.*

class UserAvailabilityService : Service() {

    private lateinit var databaseRef: DatabaseReference
    private val connectedUsers = mutableMapOf<String, Boolean>()

    override fun onCreate() {
        super.onCreate()
        databaseRef = FirebaseDatabase.getInstance().getReference("users")
        listenForAvailabilityChanges()

        startService(Intent(this, UserAvailabilityService::class.java))
    }

    private fun listenForAvailabilityChanges() {
        databaseRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val user = snapshot.getValue(MyUser::class.java) ?: return
                connectedUsers[user.uid] = user.available
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val user = snapshot.getValue(MyUser::class.java) ?: return
                val wasAvailable = connectedUsers[user.uid] ?: false

                if (!wasAvailable && user.available) {
                    showToast("${user.nombre} ${user.apellido} está ahora disponible")
                }

                connectedUsers[user.uid] = user.available
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val user = snapshot.getValue(MyUser::class.java) ?: return
                connectedUsers.remove(user.uid)
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showToast(message: String) {
        // Ejecutar en el hilo principal porque Firebase callback está en hilo secundario
        android.os.Handler(mainLooper).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

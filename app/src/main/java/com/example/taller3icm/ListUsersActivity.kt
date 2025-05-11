package com.example.taller3icm

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.taller3icm.databinding.ActivityListUsersBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ListUsersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListUsersBinding
    private lateinit var adapter: UserAdapter
    private lateinit var database: DatabaseReference
    private val userList = mutableListOf<MyUser>()
    private val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance().getReference("users")

        adapter = UserAdapter(userList) { selectedUser ->
            val intent = Intent(this, TrackUserActivity::class.java)
            intent.putExtra("userUid", selectedUser.uid)
            startActivity(intent)
        }

        binding.recyclerViewUsers.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewUsers.adapter = adapter

        loadUsersFromFirebase()

        startService(Intent(this, UserAvailabilityService::class.java))
    }

    private fun loadUsersFromFirebase() {
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userList.clear()
                for (userSnap in snapshot.children) {
                    val user = userSnap.getValue(MyUser::class.java)
                    val uid = userSnap.key
                    if (user != null && uid != currentUserUid && user.available) {
                        userList.add(user.copy(uid = uid ?: ""))
                    }

                }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                // Manejo de error si quieres mostrar algo al usuario
            }
        })
    }
}
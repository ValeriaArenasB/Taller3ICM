package com.example.taller3icm

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.taller3icm.databinding.ItemUserBinding

class UserAdapter(
    private val users: List<MyUser>,
    private val onTrackClick: (MyUser) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    inner class UserViewHolder(private val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: MyUser) {
            Glide.with(binding.root.context)
                .load(user.imagen)
                .placeholder(R.drawable.ic_online)
                .error(R.drawable.ic_online)
                .into(binding.imageViewUser)

            binding.textViewUserName.text = "${user.nombre} ${user.apellido}"

            binding.buttonTrack.setOnClickListener {
                onTrackClick(user)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount(): Int = users.size
}

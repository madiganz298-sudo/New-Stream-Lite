package com.example

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.databinding.ItemChannelBinding

class ChannelAdapter(
    private val onChannelClick: (Channel) -> Unit,
    private val onChannelLongClick: (Channel) -> Unit,
    private val favoritesManager: FavoritesManager
) : ListAdapter<Channel, ChannelAdapter.ChannelViewHolder>(ChannelDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val binding = ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty() && payloads[0] == "FAVORITE_CHANGE") {
            holder.updateFavoriteState(getItem(position))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class ChannelViewHolder(private val binding: ItemChannelBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(channel: Channel) {
            binding.txtChannelName.text = channel.name
            binding.txtChannelGroup.text = channel.group

            // Get initial character for the circular avatar placeholder
            val initial = if (channel.name.isNotBlank()) {
                channel.name.trim().first().uppercase()
            } else {
                "M"
            }
            binding.txtInitial.text = initial

            updateFavoriteState(channel)

            // Click listener
            binding.root.setOnClickListener {
                onChannelClick(channel)
            }

            // Long click listener
            binding.root.setOnLongClickListener {
                onChannelLongClick(channel)
                true
            }

            // Star click toggle
            binding.btnFavoriteItem.setOnClickListener {
                onChannelLongClick(channel)
            }
        }

        fun updateFavoriteState(channel: Channel) {
            val isFav = favoritesManager.isFavorite(channel.id)
            if (isFav) {
                binding.btnFavoriteItem.setImageResource(R.drawable.ic_favorite)
            } else {
                binding.btnFavoriteItem.setImageResource(R.drawable.ic_favorite_border)
            }
        }
    }

    class ChannelDiffCallback : DiffUtil.ItemCallback<Channel>() {
        override fun areItemsTheSame(oldItem: Channel, newItem: Channel): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Channel, newItem: Channel): Boolean {
            return oldItem == newItem
        }

        override fun getChangePayload(oldItem: Channel, newItem: Channel): Any? {
            return "FAVORITE_CHANGE" // optimization payoff
        }
    }
}

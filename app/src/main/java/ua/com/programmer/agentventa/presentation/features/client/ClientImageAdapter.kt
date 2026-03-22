package ua.com.programmer.agentventa.presentation.features.client

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import ua.com.programmer.agentventa.data.local.entity.ClientImage
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ua.com.programmer.agentventa.databinding.ClientImageItemBinding
import ua.com.programmer.agentventa.extensions.visibleIf


class ClientImageAdapter(
    private val onItemClicked: (ClientImage) -> Unit,
    private val onItemLongClicked: (ClientImage) -> Unit,
    private val imageLoader: (ClientImage, ImageView) -> Unit)
        : ListAdapter<ClientImage, ClientImageAdapter.ItemViewHolder>(DiffCallback) {

    private var isClickable = true

    fun setClickable(isClickable: Boolean) {
        this.isClickable = isClickable
    }

    class ItemViewHolder(private var binding: ClientImageItemBinding): RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ClientImage, imageLoader: (ClientImage, ImageView) -> Unit) {
            binding.apply {
                imageLoader(item, imageView)
                description.text = item.description
                isDefault.visibleIf(item.isDefault == 1)
                isSent.visibleIf(item.isSent == 0 && item.isLocal == 1)
            }
        }
    }

    companion object{
        private val DiffCallback = object : DiffUtil.ItemCallback<ClientImage>() {

            override fun areItemsTheSame(oldItem: ClientImage, newItem: ClientImage): Boolean {
                return oldItem.guid == newItem.guid
            }

            override fun areContentsTheSame(oldItem: ClientImage, newItem: ClientImage): Boolean {
                return oldItem == newItem
            }

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val viewHolder = ItemViewHolder(
            ClientImageItemBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
        viewHolder.itemView.setOnClickListener {
            if (!isClickable) return@setOnClickListener
            val position = viewHolder.absoluteAdapterPosition
            onItemClicked(getItem(position))
        }
        viewHolder.itemView.setOnLongClickListener {
            if (!isClickable) return@setOnLongClickListener(true)
            val position = viewHolder.absoluteAdapterPosition
            onItemLongClicked(getItem(position))
            true
        }
        return viewHolder
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, imageLoader)
    }


}
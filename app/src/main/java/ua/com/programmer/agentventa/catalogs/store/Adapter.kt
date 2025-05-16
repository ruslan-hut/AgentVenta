package ua.com.programmer.agentventa.catalogs.store

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ua.com.programmer.agentventa.dao.entity.Company
import ua.com.programmer.agentventa.dao.entity.Store
import ua.com.programmer.agentventa.databinding.SimpleListElementBinding

class Adapter (private val onItemClicked: (Store) -> Unit
    ): ListAdapter<Store, Adapter.ItemViewHolder>(DiffCallback) {

        class ItemViewHolder(private var binding: SimpleListElementBinding): RecyclerView.ViewHolder(binding.root) {
            fun bind(item: Store) {
                binding.apply {
                    element1.text = item.description
                    element2.text = if (item.isDefault == 1) "*" else ""
                }
            }
        }

        companion object{
            private val DiffCallback = object : DiffUtil.ItemCallback<Store>() {

                override fun areItemsTheSame(oldItem: Store, newItem: Store): Boolean {
                    return oldItem.guid == newItem.guid
                }

                override fun areContentsTheSame(oldItem: Store, newItem: Store): Boolean {
                    return oldItem == newItem
                }

            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
            val viewHolder = ItemViewHolder(
                SimpleListElementBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )
            viewHolder.itemView.setOnClickListener {
                val position = viewHolder.absoluteAdapterPosition
                onItemClicked(getItem(position))
            }
            return viewHolder
        }

        override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
            val item = getItem(position)
            holder.bind(item)
        }
}
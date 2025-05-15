package ua.com.programmer.agentventa.catalogs.company

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ua.com.programmer.agentventa.dao.entity.Company
import ua.com.programmer.agentventa.databinding.SimpleListElementBinding

class Adapter (private val onItemClicked: (Company) -> Unit
    ): ListAdapter<Company, Adapter.ItemViewHolder>(DiffCallback) {

        class ItemViewHolder(private var binding: SimpleListElementBinding): RecyclerView.ViewHolder(binding.root) {
            fun bind(item: Company) {
                binding.apply {
                    element1.text = item.description
                    element2.text = if (item.isDefault == 1) "*" else ""
                }
            }
        }

        companion object{
            private val DiffCallback = object : DiffUtil.ItemCallback<Company>() {

                override fun areItemsTheSame(oldItem: Company, newItem: Company): Boolean {
                    return oldItem.guid == newItem.guid
                }

                override fun areContentsTheSame(oldItem: Company, newItem: Company): Boolean {
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
package ua.com.programmer.agentventa.presentation.features.picker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ua.com.programmer.agentventa.data.local.entity.LPrice
import ua.com.programmer.agentventa.data.local.entity.markup
import ua.com.programmer.agentventa.databinding.PriceListItemBinding
import ua.com.programmer.agentventa.extensions.format

class PriceAdapter (
    private val onItemClicked: (LPrice) -> Unit,
    private val onItemLongClicked: (LPrice) -> Unit)
        : ListAdapter<LPrice, PriceAdapter.ItemViewHolder>(DiffCallback) {

        private var isClickable = true

        fun setClickable(isClickable: Boolean) {
            this.isClickable = isClickable
        }

        class ItemViewHolder(private var binding: PriceListItemBinding): RecyclerView.ViewHolder(binding.root) {
            fun bind(item: LPrice) {
                binding.apply {
                    description.text = item.description
                    value.text = item.price.format(2, "--")
                    percent.text = item.markup().format(1, "-", "%")
                    isCurrent.visibility = if (item.isCurrent) {
                        android.view.View.VISIBLE
                    } else {
                        android.view.View.INVISIBLE
                    }
                }
            }
        }

        companion object {
            private val DiffCallback = object : DiffUtil.ItemCallback<LPrice>() {

                override fun areItemsTheSame(oldItem: LPrice, newItem: LPrice): Boolean {
                    return oldItem.priceType == newItem.priceType
                }

                override fun areContentsTheSame(oldItem: LPrice, newItem: LPrice): Boolean {
                    return oldItem == newItem
                }

            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
            val viewHolder = ItemViewHolder(
                PriceListItemBinding.inflate(
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
            holder.bind(getItem(position))
        }

}
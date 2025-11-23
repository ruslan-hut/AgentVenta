package ua.com.programmer.agentventa.presentation.features.order

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ua.com.programmer.agentventa.data.local.entity.LOrderContent
import ua.com.programmer.agentventa.data.local.entity.getPriceFormatted
import ua.com.programmer.agentventa.data.local.entity.getQuantityFormatted
import ua.com.programmer.agentventa.data.local.entity.getSumFormatted
import ua.com.programmer.agentventa.databinding.OrderContentLineBinding

class OrderContentAdapter(
    private val onItemClicked: (LOrderContent) -> Unit,
    private val onItemLongClicked: (LOrderContent) -> Unit)
    : ListAdapter<LOrderContent, OrderContentAdapter.ItemViewHolder>(DiffCallback) {

    private var isClickable = true

    fun setClickable(isClickable: Boolean) {
        this.isClickable = isClickable
    }

    class ItemViewHolder(private var binding: OrderContentLineBinding): RecyclerView.ViewHolder(binding.root) {
        fun bind(item: LOrderContent) {
            binding.apply {
                itemCode.text = item.code
                itemName.text = item.description
                itemGroup.text = item.groupName
                itemPrice.text = item.getPriceFormatted()
                itemQuantity.text = item.getQuantityFormatted()
                itemSum.text = item.getSumFormatted()
                itemIsPacked.visibility = if (item.isPacked) {
                    ViewGroup.VISIBLE
                } else {
                    ViewGroup.INVISIBLE
                }
                itemIsDemand.visibility = if (item.isDemand) {
                    ViewGroup.VISIBLE
                } else {
                    ViewGroup.INVISIBLE
                }
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<LOrderContent>() {

            override fun areItemsTheSame(oldItem: LOrderContent, newItem: LOrderContent): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: LOrderContent, newItem: LOrderContent): Boolean {
                return oldItem == newItem
            }

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val viewHolder = ItemViewHolder(
            OrderContentLineBinding.inflate(
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
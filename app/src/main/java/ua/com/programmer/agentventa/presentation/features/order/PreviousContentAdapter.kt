package ua.com.programmer.agentventa.presentation.features.order

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.data.local.entity.PreviousOrderContent
import ua.com.programmer.agentventa.data.local.entity.getPriceFormatted
import ua.com.programmer.agentventa.data.local.entity.getQuantityFormatted
import ua.com.programmer.agentventa.data.local.entity.getSumFormatted
import ua.com.programmer.agentventa.databinding.OrderContentLineBinding

class PreviousContentAdapter(
    private val onItemClick: (PreviousOrderContent) -> Unit,
    private val onItemLongClick: (PreviousOrderContent) -> Unit
) : ListAdapter<PreviousOrderContent, PreviousContentAdapter.ItemViewHolder>(DiffCallback) {

    private var isSelectionMode = false
    private val selectedItems = mutableSetOf<String>()

    fun setSelectionMode(enabled: Boolean) {
        if (isSelectionMode != enabled) {
            isSelectionMode = enabled
            if (!enabled) {
                selectedItems.clear()
            }
            notifyDataSetChanged()
        }
    }

    fun isSelectionMode(): Boolean = isSelectionMode

    fun toggleSelection(productGuid: String) {
        if (selectedItems.contains(productGuid)) {
            selectedItems.remove(productGuid)
        } else {
            selectedItems.add(productGuid)
        }
        notifyDataSetChanged()
    }

    fun isSelected(productGuid: String): Boolean = selectedItems.contains(productGuid)

    fun getSelectedItems(): List<PreviousOrderContent> {
        return currentList.filter { selectedItems.contains(it.productGuid) }
    }

    fun getSelectedCount(): Int = selectedItems.size

    fun clearSelection() {
        selectedItems.clear()
        notifyDataSetChanged()
    }

    fun selectItem(productGuid: String) {
        selectedItems.add(productGuid)
        notifyDataSetChanged()
    }

    class ItemViewHolder(
        private val binding: OrderContentLineBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PreviousOrderContent, isSelected: Boolean, isSelectionMode: Boolean) {
            binding.apply {
                itemCode.text = item.code
                itemName.text = item.description
                itemGroup.text = item.groupName
                itemPrice.text = item.getPriceFormatted()
                itemQuantity.text = item.getQuantityFormatted()
                itemSum.text = item.getSumFormatted()
                itemIsPacked.visibility = ViewGroup.INVISIBLE
                itemIsDemand.visibility = ViewGroup.INVISIBLE

                // Selection indicator - use CardView background
                val cardView = root as? CardView
                if (isSelectionMode && isSelected) {
                    cardView?.setCardBackgroundColor(
                        ContextCompat.getColor(root.context, R.color.selected_item_background)
                    )
                } else {
                    // Use default card background color from theme
                    val defaultColor = MaterialColors.getColor(
                        root.context,
                        android.R.attr.colorBackground,
                        ContextCompat.getColor(root.context, android.R.color.white)
                    )
                    cardView?.setCardBackgroundColor(defaultColor)
                }
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<PreviousOrderContent>() {
            override fun areItemsTheSame(
                oldItem: PreviousOrderContent,
                newItem: PreviousOrderContent
            ): Boolean {
                return oldItem.productGuid == newItem.productGuid
            }

            override fun areContentsTheSame(
                oldItem: PreviousOrderContent,
                newItem: PreviousOrderContent
            ): Boolean {
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
            val position = viewHolder.absoluteAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                onItemClick(getItem(position))
            }
        }
        viewHolder.itemView.setOnLongClickListener {
            val position = viewHolder.absoluteAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                onItemLongClick(getItem(position))
            }
            true
        }
        return viewHolder
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, isSelected(item.productGuid), isSelectionMode)
    }
}

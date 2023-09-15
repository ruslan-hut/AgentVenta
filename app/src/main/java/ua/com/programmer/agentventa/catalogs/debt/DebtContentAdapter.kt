package ua.com.programmer.agentventa.catalogs.debt

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ua.com.programmer.agentventa.databinding.DebtDocumentItemBinding

class DebtContentAdapter(
    private val onItemClicked: (DebtViewModel.Item) -> Unit,
    private val onItemLongClicked: (DebtViewModel.Item) -> Unit)
    : ListAdapter<DebtViewModel.Item, DebtContentAdapter.ItemViewHolder>(DiffCallback) {

    class ItemViewHolder(private var binding: DebtDocumentItemBinding): RecyclerView.ViewHolder(binding.root) {
        fun bind(itemData: DebtViewModel.Item) {
            binding.apply {
                itemName.text = itemData.item
                itemCode.text = itemData.code
                itemQuantity.text = itemData.quantity
                itemUnit.text = itemData.unit
                itemPrice.text = itemData.price
                itemSum.text = itemData.sum
            }
        }
    }

    companion object{
        private val DiffCallback = object : DiffUtil.ItemCallback<DebtViewModel.Item>() {

            override fun areItemsTheSame(oldItem: DebtViewModel.Item, newItem: DebtViewModel.Item): Boolean {
                return oldItem.item == newItem.item
            }

            override fun areContentsTheSame(oldItem: DebtViewModel.Item, newItem: DebtViewModel.Item): Boolean {
                return oldItem == newItem
            }

        }
    }
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ItemViewHolder {
        val viewHolder = ItemViewHolder(
            DebtDocumentItemBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
        viewHolder.itemView.setOnClickListener {
            val position = viewHolder.absoluteAdapterPosition
            onItemClicked(getItem(position))
        }
        viewHolder.itemView.setOnLongClickListener {
            val position = viewHolder.absoluteAdapterPosition
            onItemLongClicked(getItem(position))
            true
        }
        return viewHolder
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

}
package ua.com.programmer.agentventa.catalogs.client

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ua.com.programmer.agentventa.dao.entity.Debt
import ua.com.programmer.agentventa.databinding.DebtListItemBinding
import ua.com.programmer.agentventa.extensions.format

class ClientDebtsAdapter(
    private val onItemClicked: (Debt) -> Unit,
    private val onItemLongClicked: (Debt) -> Unit)
        : ListAdapter<Debt, ClientDebtsAdapter.ItemViewHolder>(DiffCallback) {

        private var isClickable = true

        fun setClickable(isClickable: Boolean) {
            this.isClickable = isClickable
        }

        class ItemViewHolder(private var binding: DebtListItemBinding): RecyclerView.ViewHolder(binding.root) {
            fun bind(item: Debt) {
                binding.apply {
                    itemName.text = item.docId
                    itemPrice.text = item.sum.format(2)

                    if (item.sumIn + item.sumOut > 0) {
                        balanceLine.visibility = ViewGroup.VISIBLE
                        sumIn.text = item.sumIn.format(2)
                        sumOut.text = item.sumOut.format(2)
                    } else {
                        balanceLine.visibility = ViewGroup.GONE
                    }

                    if (item.sum < 0) {
                        iconIncome.visibility = ViewGroup.VISIBLE
                        iconOutcome.visibility = ViewGroup.GONE
                    } else {
                        iconIncome.visibility = ViewGroup.GONE
                        iconOutcome.visibility = ViewGroup.VISIBLE
                    }

                    icon.visibility = if (item.hasContent == 1) ViewGroup.VISIBLE else ViewGroup.INVISIBLE
                }
            }
        }

        companion object{
            private val DiffCallback = object : DiffUtil.ItemCallback<Debt>() {

                override fun areItemsTheSame(oldItem: Debt, newItem: Debt): Boolean {
                    return oldItem.docId == newItem.docId
                }

                override fun areContentsTheSame(oldItem: Debt, newItem: Debt): Boolean {
                    return oldItem == newItem
                }

            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
            val viewHolder = ItemViewHolder(
                DebtListItemBinding.inflate(
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
            holder.bind(item)
        }
}
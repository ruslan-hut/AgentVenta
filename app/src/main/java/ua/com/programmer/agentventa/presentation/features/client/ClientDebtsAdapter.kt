package ua.com.programmer.agentventa.presentation.features.client

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ua.com.programmer.agentventa.data.local.entity.Debt
import ua.com.programmer.agentventa.databinding.DebtGroupHeaderBinding
import ua.com.programmer.agentventa.databinding.DebtListItemBinding
import ua.com.programmer.agentventa.extensions.format
import ua.com.programmer.agentventa.extensions.visibleOrInvisibleIf

class ClientDebtsAdapter(
    private val onItemClicked: (Debt) -> Unit,
    private val onItemLongClicked: (Debt) -> Unit)
        : ListAdapter<DebtListItem, RecyclerView.ViewHolder>(DiffCallback) {

        private var isClickable = true

        fun setClickable(isClickable: Boolean) {
            this.isClickable = isClickable
        }

        class HeaderViewHolder(private var binding: DebtGroupHeaderBinding): RecyclerView.ViewHolder(binding.root) {
            fun bind(item: DebtListItem.Header) {
                binding.apply {
                    groupName.text = item.name
                    // shown as received: the total is calculated by the data source
                    // and may cover more than the documents listed under it
                    groupSum.text = item.sum.format(2)
                }
            }
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

                    icon.visibleOrInvisibleIf(item.hasContent == 1)
                }
            }
        }

        companion object{
            private const val VIEW_TYPE_HEADER = 0
            private const val VIEW_TYPE_DOCUMENT = 1

            private val DiffCallback = object : DiffUtil.ItemCallback<DebtListItem>() {

                override fun areItemsTheSame(oldItem: DebtListItem, newItem: DebtListItem): Boolean {
                    return when {
                        oldItem is DebtListItem.Header && newItem is DebtListItem.Header ->
                            oldItem.name == newItem.name
                        oldItem is DebtListItem.Document && newItem is DebtListItem.Document ->
                            oldItem.debt.docId == newItem.debt.docId
                        else -> false
                    }
                }

                override fun areContentsTheSame(oldItem: DebtListItem, newItem: DebtListItem): Boolean {
                    return oldItem == newItem
                }

            }
        }

        override fun getItemViewType(position: Int): Int {
            return when (getItem(position)) {
                is DebtListItem.Header -> VIEW_TYPE_HEADER
                is DebtListItem.Document -> VIEW_TYPE_DOCUMENT
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)

            if (viewType == VIEW_TYPE_HEADER) {
                return HeaderViewHolder(DebtGroupHeaderBinding.inflate(inflater, parent, false))
            }

            val viewHolder = ItemViewHolder(DebtListItemBinding.inflate(inflater, parent, false))
            viewHolder.itemView.setOnClickListener {
                if (!isClickable) return@setOnClickListener
                val debt = documentAt(viewHolder.absoluteAdapterPosition) ?: return@setOnClickListener
                onItemClicked(debt)
            }
            viewHolder.itemView.setOnLongClickListener {
                if (!isClickable) return@setOnLongClickListener(true)
                val debt = documentAt(viewHolder.absoluteAdapterPosition) ?: return@setOnLongClickListener(true)
                onItemLongClicked(debt)
                true
            }
            return viewHolder
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = getItem(position)) {
                is DebtListItem.Header -> (holder as HeaderViewHolder).bind(item)
                is DebtListItem.Document -> (holder as ItemViewHolder).bind(item.debt)
            }
        }

        private fun documentAt(position: Int): Debt? {
            if (position == RecyclerView.NO_POSITION) return null
            return (getItem(position) as? DebtListItem.Document)?.debt
        }
}

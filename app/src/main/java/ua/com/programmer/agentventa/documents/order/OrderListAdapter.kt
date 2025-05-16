package ua.com.programmer.agentventa.documents.order

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.dao.entity.Order
import ua.com.programmer.agentventa.databinding.ModelDocumentsListItemBinding
import ua.com.programmer.agentventa.extensions.format
import ua.com.programmer.agentventa.extensions.formatAsInt
import java.util.Locale

class OrderListAdapter(
    private val onDocumentClicked: (Order) -> Unit,
    private val onDocumentLongClicked: (Order) -> Unit)
    : ListAdapter<Order, OrderListAdapter.DocumentViewHolder>(DiffCallback) {

    class DocumentViewHolder(private var binding: ModelDocumentsListItemBinding): RecyclerView.ViewHolder(binding.root) {
        fun bind(document: Order) {
            binding.apply {
                listItemClient.text = document.clientDescription
                listItemNumber.text = String.format(Locale.getDefault(), "%d", document.number)
                listItemPrice.text = document.price.format(2, "--")
                listItemQuantity.text = document.quantity.formatAsInt(3, "--")
                listItemNote.text = document.notes
                listItemStatus.text = document.status
                listItemDate.text = document.date
                listItemCompany.text = document.company
                listItemStore.text = document.store
                listItemCompany.visibility = if (document.company.isNotBlank()) {
                    View.VISIBLE
                } else {
                    View.INVISIBLE
                }
                listItemStore.visibility = if (document.store.isNotBlank()) {
                    View.VISIBLE
                } else {
                    View.INVISIBLE
                }
                iconFiscal.visibility = if (document.isFiscal == 1) {
                    View.VISIBLE
                } else {
                    View.INVISIBLE
                }
                iconReturn.visibility = if (document.isReturn == 1) {
                    View.VISIBLE
                } else {
                    View.INVISIBLE
                }
                iconCash.visibility = if (document.paymentType == "CASH") {
                    View.VISIBLE
                } else {
                    View.INVISIBLE
                }
                iconUpload.setImageResource(if (document.isSent == 1) {
                    R.drawable.baseline_cloud_done_24
                } else if (document.isProcessed == 1) {
                    R.drawable.baseline_cloud_upload_24
                } else {
                    R.drawable.baseline_cloud_queue_24
                })
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Order>() {

            override fun areItemsTheSame(oldItem: Order, newItem: Order): Boolean {
                return oldItem.guid == newItem.guid
            }

            override fun areContentsTheSame(oldItem: Order, newItem: Order): Boolean {
                return oldItem.paymentType == newItem.paymentType &&
                        oldItem.clientDescription == newItem.clientDescription &&
                        oldItem.price == newItem.price &&
                        oldItem.quantity == newItem.quantity &&
                        oldItem.notes == newItem.notes &&
                        oldItem.status == newItem.status &&
                        oldItem.isReturn == newItem.isReturn &&
                        oldItem.isSent == newItem.isSent &&
                        oldItem.isProcessed == newItem.isProcessed
            }

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentViewHolder {
        val viewHolder = DocumentViewHolder(
            ModelDocumentsListItemBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
        viewHolder.itemView.setOnClickListener {
            val position = viewHolder.absoluteAdapterPosition
            onDocumentClicked(getItem(position))
        }
        viewHolder.itemView.setOnLongClickListener {
            val position = viewHolder.absoluteAdapterPosition
            onDocumentLongClicked(getItem(position))
            true
        }
        return viewHolder
    }

    override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
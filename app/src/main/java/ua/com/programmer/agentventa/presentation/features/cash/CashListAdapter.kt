package ua.com.programmer.agentventa.presentation.features.cash

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.data.local.entity.Cash
import ua.com.programmer.agentventa.databinding.ModelDocumentsListItemBinding
import ua.com.programmer.agentventa.extensions.format
import java.util.Locale

class CashListAdapter(private val onDocumentClicked: (Cash) -> Unit)
    : ListAdapter<Cash, CashListAdapter.DocumentViewHolder>(DiffCallback)  {

    class DocumentViewHolder(private var binding: ModelDocumentsListItemBinding): RecyclerView.ViewHolder(binding.root) {
        fun bind(document: Cash) {
            binding.apply {
                listItemClient.text = document.client
                listItemNumber.text = String.format(Locale.getDefault(), "%d", document.number)
                listItemPrice.text = document.sum.format(2, "--")
                listItemQuantityHeader.visibility = View.GONE
                listItemQuantity.visibility = View.GONE
                listItemNote.text = document.notes
                listItemStatus.text = document.status
                listItemDate.text = document.date
                listItemCompany.text = document.company
                listItemCompany.visibility = if (document.company.isNotEmpty()) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                listItemStore.visibility = View.GONE
                iconFiscal.visibility = if (document.isFiscal == 1) {
                    View.VISIBLE
                } else {
                    View.INVISIBLE
                }
                iconReturn.visibility = View.INVISIBLE
                iconCash.visibility = View.INVISIBLE
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
        private val DiffCallback = object : DiffUtil.ItemCallback<Cash>() {

            override fun areItemsTheSame(oldItem: Cash, newItem: Cash): Boolean {
                return oldItem.guid == newItem.guid
            }

            override fun areContentsTheSame(oldItem: Cash, newItem: Cash): Boolean {
                return oldItem == newItem
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
        return viewHolder
    }

    override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
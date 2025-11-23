package ua.com.programmer.agentventa.presentation.features.task

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.data.local.entity.Task
import ua.com.programmer.agentventa.databinding.ModelDocumentsListItemBinding


class TaskListAdapter(private val onDocumentClicked: (Task) -> Unit)
    : ListAdapter<Task, TaskListAdapter.DocumentViewHolder>(DiffCallback)   {
    class DocumentViewHolder(private var binding: ModelDocumentsListItemBinding): RecyclerView.ViewHolder(binding.root) {
        fun bind(document: Task) {
            binding.apply {
                // Show description as the main title
                listItemClient.text = document.description

                // Show notes with max 2 lines
                listItemNote.text = document.notes
                listItemNote.maxLines = 2

                // Show date
                listItemDate.text = document.date

                // Hide unused fields for tasks
                listItemCompany.visibility = View.GONE
                listItemStore.visibility = View.GONE
                listItemNumber.visibility = View.GONE
                listItemPrice.visibility = View.GONE
                listItemQuantity.visibility = View.GONE
                listItemQuantityHeader.visibility = View.GONE
                listItemStatus.visibility = View.GONE

                // Hide unused icons
                iconFiscal.visibility = View.GONE
                iconReturn.visibility = View.GONE
                iconCash.visibility = View.GONE

                // Show done/pending status icon
                iconUpload.setImageResource(if (document.isDone == 1) {
                    R.drawable.baseline_cloud_done_24
                } else {
                    R.drawable.baseline_cloud_queue_24
                })
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Task>() {

            override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean {
                return oldItem.guid == newItem.guid
            }

            override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean {
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
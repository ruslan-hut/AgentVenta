package ua.com.programmer.agentventa.presentation.features.client

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ua.com.programmer.agentventa.data.local.entity.LClient
import ua.com.programmer.agentventa.databinding.ClientsListItemBinding
import ua.com.programmer.agentventa.databinding.GoodsListItemGroupBinding
import ua.com.programmer.agentventa.extensions.format

class ClientListAdapter(private val onItemClicked: (LClient) -> Unit)
    : ListAdapter<LClient, ClientListAdapter.ItemViewHolder>(DiffCallback) {

    abstract class ItemViewHolder(binding: ViewDataBinding): RecyclerView.ViewHolder(binding.root) {
        abstract fun bind(client: LClient)
    }

    class ClientViewHolder(private var binding: ClientsListItemBinding): ItemViewHolder(binding) {
        override fun bind(client: LClient) {
            binding.apply {
                itemName.text = client.description
                itemCode.text = client.code
                itemGroup.text = client.groupName
                itemDebt.text = client.debt.format(2, "--")
            }
        }
    }

    class ClientGroupViewHolder(private var binding: GoodsListItemGroupBinding): ItemViewHolder(binding) {
        override fun bind(client: LClient) {
            binding.apply {
                itemName.text = client.description
                itemCode.text = client.code
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<LClient>() {

            override fun areItemsTheSame(oldItem: LClient, newItem: LClient): Boolean {
                return oldItem.guid == newItem.guid
            }

            override fun areContentsTheSame(oldItem: LClient, newItem: LClient): Boolean {
                return oldItem == newItem
            }

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val viewHolder = if (viewType == 0) {
            ClientViewHolder(
                ClientsListItemBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )
        } else {
            ClientGroupViewHolder(
                GoodsListItemGroupBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )
        }
        viewHolder.itemView.setOnClickListener {
            val position = viewHolder.absoluteAdapterPosition
            onItemClicked(getItem(position))
        }
        return viewHolder
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return if (item.isGroup) return 1 else 0
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
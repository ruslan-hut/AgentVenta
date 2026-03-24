package ua.com.programmer.agentventa.presentation.features.client

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.data.local.entity.LDiscount
import ua.com.programmer.agentventa.databinding.DiscountListItemBinding
import java.util.Locale

class ClientDiscountsAdapter
    : ListAdapter<LDiscount, ClientDiscountsAdapter.ItemViewHolder>(DiffCallback) {

    class ItemViewHolder(
        private val binding: DiscountListItemBinding,
        private val allProductsLabel: String,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: LDiscount) {
            binding.apply {
                itemName.text = if (item.productGuid.isEmpty()) {
                    allProductsLabel
                } else {
                    item.description
                }
                val sign = if (item.discount >= 0) "+" else ""
                val value = if (item.discount % 1.0 == 0.0) {
                    "${sign}${item.discount.toInt()}%"
                } else {
                    String.format(Locale.getDefault(), "%+.2f%%", item.discount)
                }
                itemDiscount.text = value
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<LDiscount>() {
            override fun areItemsTheSame(oldItem: LDiscount, newItem: LDiscount): Boolean {
                return oldItem.productGuid == newItem.productGuid
            }

            override fun areContentsTheSame(oldItem: LDiscount, newItem: LDiscount): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val allProductsLabel = parent.context.getString(R.string.discount_all_products)
        return ItemViewHolder(
            DiscountListItemBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            ),
            allProductsLabel,
        )
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

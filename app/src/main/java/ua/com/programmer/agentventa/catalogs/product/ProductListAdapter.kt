package ua.com.programmer.agentventa.catalogs.product

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ua.com.programmer.agentventa.dao.entity.LProduct
import ua.com.programmer.agentventa.databinding.GoodsListItemGroupBinding
import ua.com.programmer.agentventa.databinding.GoodsSelectListItemBinding
import ua.com.programmer.agentventa.databinding.ProductListItemBinding
import ua.com.programmer.agentventa.extensions.format
import ua.com.programmer.agentventa.extensions.formatAsInt

class ProductListAdapter(
    private val onItemClicked: (LProduct) -> Unit
) : ListAdapter<LProduct, ProductListAdapter.ItemViewHolder>(DiffCallback) {

    private lateinit var sharedImageLoader: (LProduct, ImageView) -> Unit
    private lateinit var onImageClicked: (LProduct) -> Unit
    private var showImages = false

    fun setImageLoader(loader: (LProduct, ImageView) -> Unit) {
        sharedImageLoader = loader
    }

    fun setImageClickedListener(listener: (LProduct) -> Unit) {
        onImageClicked = listener
    }

    fun setShowImages(show: Boolean) {
        showImages = show
    }

    abstract class ItemViewHolder(binding: ViewDataBinding): RecyclerView.ViewHolder(binding.root) {
        abstract fun bind(
            product: LProduct,
            showImages: Boolean,
            loadImage: (LProduct, ImageView) -> Unit,
            onImageClicked: (LProduct) -> Unit)
    }

    class ProductViewHolder(private var binding: ProductListItemBinding): ItemViewHolder(binding as ViewDataBinding) {
        override fun bind(
            product: LProduct,
            showImages: Boolean,
            loadImage: (LProduct, ImageView) -> Unit,
            onImageClicked: (LProduct) -> Unit) {
            binding.apply {
                itemName.text = product.description
                itemGroup.text = product.groupName
                itemQuantity.text = product.rest.formatAsInt(3,"-",product.unit)
                itemCode.text = product.code
                itemPrice.text = product.price.format(2, "--")
                itemVendorCode.text = product.vendorCode
                val perPackage = product.packageValue.formatAsInt(3,"-")
                if (perPackage.isNotEmpty()) {
                    itemPackageValue.text = perPackage
                    packageLine.visibility = android.view.View.VISIBLE
                } else {
                    itemPackageValue.text = ""
                    packageLine.visibility = android.view.View.GONE
                }
                if (showImages) {
                    itemImage.visibility = android.view.View.VISIBLE
                    itemImage.setOnClickListener { onImageClicked(product) }
                    loadImage(product, itemImage)
                }
            }
        }
    }

    class ProductSelectViewHolder(private var binding: GoodsSelectListItemBinding): ItemViewHolder(binding as ViewDataBinding) {
        override fun bind(
            product: LProduct,
            showImages: Boolean,
            loadImage: (LProduct, ImageView) -> Unit,
            onImageClicked: (LProduct) -> Unit
        ) {

            binding.apply {
                itemName.text = product.description
                itemGroup.text = product.groupName
                itemUnit.text = product.unit
                itemPrice.text = product.price.format(2,"--")
                itemRest.text = product.rest.formatAsInt(3,"-")
                itemVendorCode.text = product.vendorCode
                itemCode.text = product.code

                val qty = product.quantity.formatAsInt(3)
                if (qty.isNotEmpty()) {
                    itemQuantity.text = qty
                    itemQuantityDelimiter.visibility = android.view.View.VISIBLE
                } else {
                    itemQuantity.text = ""
                    itemQuantityDelimiter.visibility = android.view.View.GONE
                }

                val perPackage = product.packageValue.formatAsInt(3,"-")
                if (perPackage.isNotEmpty()) {
                    itemPackageValue.text = perPackage
                    packageLine.visibility = android.view.View.VISIBLE
                } else {
                    itemPackageValue.text = ""
                    packageLine.visibility = android.view.View.GONE
                }

                if (showImages) {
                    itemImage.visibility = android.view.View.VISIBLE
                    itemImage.setOnClickListener { onImageClicked(product) }
                    loadImage(product, itemImage)
                }
            }
        }
    }

    class ProductGroupViewHolder(private var binding: GoodsListItemGroupBinding): ItemViewHolder(binding as ViewDataBinding) {
        override fun bind(
            product: LProduct,
            showImages: Boolean,
            loadImage: (LProduct, ImageView) -> Unit,
            onImageClicked: (LProduct) -> Unit) {
            binding.apply {
                itemName.text = product.description
                itemCode.text = product.code
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<LProduct>() {

            override fun areItemsTheSame(oldItem: LProduct, newItem: LProduct): Boolean {
                return oldItem.guid == newItem.guid
            }

            override fun areContentsTheSame(oldItem: LProduct, newItem: LProduct): Boolean {
                return oldItem == newItem
            }

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val viewHolder = when (viewType){
            0 -> ProductGroupViewHolder(
                GoodsListItemGroupBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )
            1 -> ProductSelectViewHolder(
                GoodsSelectListItemBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )
            else -> ProductViewHolder(
                ProductListItemBinding.inflate(
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
        val product = getItem(position)
        if (product.isGroup) return 0
        return if (product.modeSelect) 1 else 2
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(getItem(position), showImages, sharedImageLoader, onImageClicked)
    }

    override fun getItem(position: Int): LProduct {
        if (position < 0 || position >= itemCount) return LProduct()
        return super.getItem(position)
    }
}
package ua.com.programmer.agentventa.catalogs.product

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.dao.entity.LProduct
import ua.com.programmer.agentventa.dao.entity.hasImageData
import ua.com.programmer.agentventa.databinding.ActivityGoodsSelectBinding
import ua.com.programmer.agentventa.databinding.GoodsSelectTotalsDialogBinding
import ua.com.programmer.agentventa.extensions.format
import ua.com.programmer.agentventa.extensions.formatAsInt
import ua.com.programmer.agentventa.shared.SharedViewModel

@AndroidEntryPoint
class ProductListFragment: Fragment(), MenuProvider {

    private val viewModel: ProductListViewModel by viewModels()
    private val sharedModel: SharedViewModel by activityViewModels()
    private val navigationArgs: ProductListFragmentArgs by navArgs()
    private var _binding: ActivityGoodsSelectBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setCurrentGroup(navigationArgs.groupGuid)
        viewModel.setSelectMode(navigationArgs.modeSelect)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityGoodsSelectBinding.inflate(inflater,container,false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        val menuHost : MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = binding.goodsRecycler
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        binding.goodsSwipe.setOnRefreshListener {
            //TODO on swipe listener
            binding.goodsSwipe.isRefreshing = false
        }

        val adapter = ProductListAdapter { product -> onItemClick(product) }

        adapter.setImageLoader(sharedModel::loadImage)
        adapter.setShowImages(sharedModel.options.loadImages)
        adapter.setImageClickedListener {
            if (!it.hasImageData()) return@setImageClickedListener
            val action = ProductListFragmentDirections.actionProductListFragmentToProductImageFragment(
                productGuid = it.guid
            )
            view.findNavController().navigate(action)
        }

        recyclerView.adapter = adapter
        viewModel.products.observe(this.viewLifecycleOwner) {
            adapter.submitList(it)
        }
        viewModel.currentGroup.observe(this.viewLifecycleOwner) {
            val desc = it?.description ?: ""
            val title = desc.ifBlank { getString(R.string.header_goods_list) }
            (requireActivity() as AppCompatActivity).supportActionBar?.title = title
        }
        sharedModel.sharedParams.observe(this.viewLifecycleOwner) {
            viewModel.setListParams(it)
            if (it.restsOnly || it.sortByName || it.priceType.isNotBlank()) {
                binding.tableTop.visibility = View.VISIBLE
                binding.priceType.text = sharedModel.getPriceDescription(it.priceType)
                binding.filterRestsOnly.visibility = if (it.restsOnly) View.VISIBLE else View.GONE
                binding.sortByName.visibility = if (it.sortByName) View.VISIBLE else View.GONE
            } else {
                binding.tableTop.visibility = View.GONE
            }
        }
    }

    private fun onItemClick(product: LProduct) {
        val action = if (product.isGroup) {
            ProductListFragmentDirections.actionProductListFragmentSelf(
                groupGuid = product.guid,
                modeSelect = navigationArgs.modeSelect
            )

        } else if (navigationArgs.modeSelect) {
            ProductListFragmentDirections.actionProductListFragmentToPickerFragment(
                productGuid = product.guid
            )
        } else {
            ProductListFragmentDirections.actionProductListFragmentToProductFragment(
                productGuid = product.guid
            )
        }
        view?.findNavController()?.navigate(action)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_goods_select, menu)
        menu.findItem(R.id.show_totals).isVisible = viewModel.selectMode

        sharedModel.sharedParams.observe(this.viewLifecycleOwner) { params ->
            params?.let {
                menu.findItem(R.id.sorting).isChecked = it.sortByName
                menu.findItem(R.id.show_rests).isChecked = it.restsOnly
                menu.findItem(R.id.client_goods).isChecked = it.clientProducts
            }
        }

        val priceSubMenu = menu.findItem(R.id.sub_menu_price).subMenu
        priceSubMenu?.clear()
        sharedModel.priceTypes.forEachIndexed { index, priceType ->
            priceSubMenu?.add(R.id.sub_menu_price, index, index, priceType.description)
        }

    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        val title = menuItem.title.toString()
        if (sharedModel.priceTypes.find { it.description == title } != null) {
            sharedModel.setPrice(title)
            return true
        }
        when (menuItem.itemId) {
            R.id.action_search -> {
                viewModel.toggleSearchVisibility()
            }
            R.id.show_totals -> {
                showDocumentTotals()
            }
            R.id.sorting -> {
                sharedModel.toggleSortByName()
            }
            R.id.show_rests -> {
                sharedModel.toggleRestsOnly()
            }
            R.id.client_goods -> {
                sharedModel.toggleClientProducts()
            }
            else -> return false
        }
        return false
    }

    private fun showDocumentTotals() {
        val builder = AlertDialog.Builder(requireContext())
        val dialogBinding = GoodsSelectTotalsDialogBinding.inflate(layoutInflater)
        dialogBinding.lifecycleOwner = viewLifecycleOwner
        builder.setView(dialogBinding.root)
        builder.setTitle("")
            .setMessage("")
            .setPositiveButton(R.string.continue_edit) { _, _ -> }
            .setNegativeButton(R.string.close) { _, _ ->
                view?.findNavController()?.popBackStack(R.id.orderFragment, false)
            }
        val dialog = builder.create()
        dialog.show()
        sharedModel.documentTotals.observe(this.viewLifecycleOwner) {
            dialogBinding.apply {
                totalDiscount.text = it.discount.format(2)
                totalPrice.text = it.sum.format(2)
                totalQuantity.text = it.quantity.formatAsInt(3)
                totalWeight.text = it.weight.formatAsInt(3)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
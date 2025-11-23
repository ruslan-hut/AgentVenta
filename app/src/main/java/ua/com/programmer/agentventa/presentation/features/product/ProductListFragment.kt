package ua.com.programmer.agentventa.presentation.features.product

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
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
import ua.com.programmer.agentventa.data.local.entity.Company
import ua.com.programmer.agentventa.data.local.entity.LProduct
import ua.com.programmer.agentventa.data.local.entity.Store
import ua.com.programmer.agentventa.data.local.entity.hasImageData
import ua.com.programmer.agentventa.databinding.ActivityGoodsSelectBinding
import ua.com.programmer.agentventa.databinding.GoodsSelectTotalsDialogBinding
import ua.com.programmer.agentventa.extensions.format
import ua.com.programmer.agentventa.extensions.formatAsInt
import ua.com.programmer.agentventa.presentation.common.viewmodel.SharedViewModel

@AndroidEntryPoint
class ProductListFragment: Fragment(), MenuProvider {

    private val viewModel: ProductListViewModel by viewModels()
    private val sharedModel: SharedViewModel by activityViewModels()
    private val navigationArgs: ProductListFragmentArgs by navArgs()
    private var _binding: ActivityGoodsSelectBinding? = null
    private val binding get() = _binding!!

    // menu buttons
    private var companies: List<Company> = emptyList()
    private var stores: List<Store> = emptyList()

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

        sharedModel.sharedParams.observe(this.viewLifecycleOwner) { params ->

            viewModel.setListParams(params)

            if (params.restsOnly || params.sortByName || params.priceType.isNotBlank()) {
                binding.tableTop.visibility = View.VISIBLE
                binding.priceType.text = sharedModel.getPriceDescription(params.priceType)
                binding.filterRestsOnly.visibility = if (params.restsOnly) View.VISIBLE else View.GONE
                binding.sortByName.visibility = if (params.sortByName) View.VISIBLE else View.GONE
            } else {
                binding.tableTop.visibility = View.GONE
            }
            if (params.companyGuid.isNotBlank() || params.storeGuid.isNotBlank()) {
                binding.tableCompanyTop.visibility = View.VISIBLE
                binding.filterCompany.text = params.company
                binding.filterStore.text = params.store
            } else {
                binding.tableCompanyTop.visibility = View.GONE
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

        if (viewModel.selectMode) {
            menu.findItem(R.id.sub_menu_price).isVisible = false
        }else{
            val priceSubMenu = menu.findItem(R.id.sub_menu_price).subMenu
            priceSubMenu?.clear()
            sharedModel.priceTypes.forEachIndexed { index, priceType ->
                priceSubMenu?.add(R.id.sub_menu_price, index, index, priceType.description)
            }
        }

        sharedModel.getCompanies { list ->
            if (list.isNotEmpty()) {
                menu.findItem(R.id.select_company).isVisible = true
                companies = list
            }
        }
        sharedModel.getStores { list ->
            if (list.isNotEmpty()) {
                menu.findItem(R.id.select_store).isVisible = true
                stores = list
            }
        }

    }

    fun selectCompany(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor, Gravity.END)
        companies.forEachIndexed { index, item ->
            popup.menu.add(0, index, index, item.description)
        }
        popup.setOnMenuItemClickListener { item ->
            val selected = companies[item.itemId]
            sharedModel.setCompany(selected.guid)
            true
        }
        popup.show()
    }

    fun selectStore(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor, Gravity.END)
        stores.forEachIndexed { index, item ->
            popup.menu.add(0, index, index, item.description)
        }
        popup.setOnMenuItemClickListener { item ->
            val selected = stores[item.itemId]
            sharedModel.setStore(selected.guid)
            true
        }
        popup.show()
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
            R.id.select_company -> {
                selectCompany(requireActivity().findViewById(R.id.toolbar)) // toolbar in main activity
            }
            R.id.select_store -> {
                selectStore(requireActivity().findViewById(R.id.toolbar)) // toolbar in main activity
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
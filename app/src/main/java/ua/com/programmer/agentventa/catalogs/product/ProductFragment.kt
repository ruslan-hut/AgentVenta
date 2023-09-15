package ua.com.programmer.agentventa.catalogs.product

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.catalogs.picker.PriceAdapter
import ua.com.programmer.agentventa.dao.entity.LProduct
import ua.com.programmer.agentventa.dao.entity.hasImageData
import ua.com.programmer.agentventa.databinding.ProductFragmentBinding
import ua.com.programmer.agentventa.extensions.format
import ua.com.programmer.agentventa.extensions.formatAsInt
import ua.com.programmer.agentventa.shared.SharedViewModel

@AndroidEntryPoint
class ProductFragment: Fragment() {

    private val viewModel: ProductViewModel by viewModels()
    private val sharedModel: SharedViewModel by activityViewModels()
    private val navigationArgs: ProductFragmentArgs by navArgs()
    private var _binding: ProductFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedModel.sharedParams.observe(this) {
            viewModel.setProductParameters(navigationArgs.productGuid, it.orderGuid, it.priceType)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ProductFragmentBinding.inflate(inflater,container,false)
        binding.lifecycleOwner = viewLifecycleOwner
        //binding.viewModel = viewModel

//        val menuHost : MenuHost = requireActivity()
//        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val priceRecycler = binding.priceRecycler
        priceRecycler.layoutManager = LinearLayoutManager(requireContext())

        val adapter = PriceAdapter(
            onItemClicked = {},
            onItemLongClicked = {},
        )
        priceRecycler.adapter = adapter

        viewModel.priceList.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
        }

        viewModel.product.observe(viewLifecycleOwner) {
            binding.product = it
            updateView(it)
        }

        binding.productImageView.setOnClickListener {
            val action = ProductFragmentDirections.actionProductFragmentToProductImageFragment(
                productGuid = navigationArgs.productGuid
            )
            view.findNavController().navigate(action)
        }

    }

    private fun updateView(product: LProduct) {
        binding.apply {
            rest.text = product.rest.formatAsInt(3, "--", product.unit)
            packageValue.text = product.packageValue.formatAsInt(3, "--", product.unit)
            basePrice.text = product.basePrice.format(2, "--")
            minPrice.text = product.minPrice.format(2, "--")
        }
        if (product.hasImageData()) {
            binding.productImageView.post {
                val width = binding.productImageView.width
                binding.productImageView.layoutParams.height = width
                binding.productImageView.requestLayout()
                sharedModel.loadImage(product, binding.productImageView)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
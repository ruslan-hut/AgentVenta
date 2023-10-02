package ua.com.programmer.agentventa.documents.order

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.databinding.ModelContentOrderGoodsBinding
import ua.com.programmer.agentventa.extensions.format

@AndroidEntryPoint
class OrderPageContent: Fragment() {

    private val viewModel: OrderViewModel by activityViewModels()
    private var _binding: ModelContentOrderGoodsBinding? = null
    private val binding get() = _binding!!
    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ModelContentOrderGoodsBinding.inflate(inflater,container,false)
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = binding.goodsList
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val adapter = OrderContentAdapter(
            onItemClicked = { },
            onItemLongClicked = {
                val action = OrderFragmentDirections.actionOrderFragmentToPickerFragment(
                    productGuid = it.productGuid,
                )
                view.findNavController().navigate(action)
            }
        )
        recyclerView.adapter = adapter

        viewModel.currentContent.observe(viewLifecycleOwner) {
            adapter.submitList(it)
        }
        viewModel.document.observe(viewLifecycleOwner) {
            binding.apply {
                orderTotalPrice.text = it.price.format(2)
                orderTotalWeight.text = it.weight.format(3)
            }
            adapter.setClickable(it.isProcessed == 0)
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
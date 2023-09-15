package ua.com.programmer.agentventa.documents.order

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.databinding.ModelContentOrderGoodsBinding

@AndroidEntryPoint
class OrderPageContentPrevious: Fragment() {

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

//        val adapter = DocumentContentAdapter {
//
//        }
//        recyclerView.adapter = adapter
//
//        viewModel.getProductsOrder().observe(this.viewLifecycleOwner) {
//            adapter.submitList(it)
//            val count = it.size - 1
//            if (count > 0) {
//                recyclerView.scrollToPosition(count)
//            }
//        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
package ua.com.programmer.agentventa.presentation.features.order

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.databinding.ModelContentOrderGoodsBinding

@AndroidEntryPoint
class OrderPageContentPrevious : Fragment() {

    private val viewModel: OrderViewModel by activityViewModels()
    private var _binding: ModelContentOrderGoodsBinding? = null
    private val binding get() = _binding!!
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PreviousContentAdapter
    private var fabCopy: FloatingActionButton? = null

    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            exitSelectionMode()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ModelContentOrderGoodsBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.elementPaymentType.visibility = View.GONE
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)

        recyclerView = binding.goodsList
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Create FAB programmatically
        createFab()

        adapter = PreviousContentAdapter(
            onItemClick = { item ->
                if (adapter.isSelectionMode()) {
                    adapter.toggleSelection(item.productGuid)
                    updateFabVisibility()
                }
            },
            onItemLongClick = { item ->
                // Only allow selection mode if order is editable
                if (!adapter.isSelectionMode() && !viewModel.isNotEditable()) {
                    adapter.setSelectionMode(true)
                    adapter.selectItem(item.productGuid)
                    backPressedCallback.isEnabled = true
                    updateFabVisibility()
                }
            }
        )
        recyclerView.adapter = adapter

        // Observe previous content
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.previousContent.collect { items ->
                    adapter.submitList(items)
                    updateEmptyState(items.isEmpty())
                }
            }
        }

        // Reload when document/client changes
        viewModel.document.observe(viewLifecycleOwner) { order ->
            if (!order.clientGuid.isNullOrEmpty()) {
                viewModel.loadPreviousContent()
            }
        }
    }

    private fun createFab() {
        fabCopy = FloatingActionButton(requireContext()).apply {
            setImageResource(R.drawable.baseline_content_copy_24)
            visibility = View.GONE
            setOnClickListener { copySelectedItems() }
        }

        // Find the activity's content view and add FAB to it
        val decorView = requireActivity().window.decorView
        val contentView = decorView.findViewById<ViewGroup>(android.R.id.content)

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            val margin = resources.getDimensionPixelSize(R.dimen.fab_margin)
            setMargins(margin, margin, margin, margin * 8)
        }

        contentView.addView(fabCopy, params)

    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.orderTotalWeight.text = "---"
            binding.orderTotalPrice.text = "---"
        } else {
            binding.orderTotalWeight.text = ""
            binding.orderTotalPrice.text = ""
        }
    }

    private fun updateFabVisibility() {
        fabCopy?.visibility = if (adapter.isSelectionMode() && adapter.getSelectedCount() > 0) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun exitSelectionMode() {
        adapter.setSelectionMode(false)
        backPressedCallback.isEnabled = false
        updateFabVisibility()
    }

    private fun copySelectedItems() {
        val selectedItems = adapter.getSelectedItems()
        if (selectedItems.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_items_selected, Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.copySelectedProducts(selectedItems) { success ->
            if (success) {
                Toast.makeText(requireContext(), R.string.data_copied, Toast.LENGTH_SHORT).show()
                exitSelectionMode()
            } else {
                Toast.makeText(requireContext(), R.string.error_copy_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Remove FAB from activity's content view
        fabCopy?.let { fab ->
            (fab.parent as? ViewGroup)?.removeView(fab)
        }
        fabCopy = null
        _binding = null
    }
}

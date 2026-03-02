package ua.com.programmer.agentventa.presentation.features.cash

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.databinding.ModelDocumentsListBinding
import ua.com.programmer.agentventa.presentation.common.viewmodel.SharedViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class CashListFragment: Fragment(), MenuProvider {

    private val viewModel: CashListViewModel by viewModels()
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private var _binding: ModelDocumentsListBinding? = null
    private val binding get() = _binding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = ModelDocumentsListBinding.inflate(inflater,container,false)
        binding?.lifecycleOwner = viewLifecycleOwner
        binding?.viewModel = viewModel

        val menuHost : MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = binding?.documentsRecycler //todo: rename to cashListRecycler
        recyclerView?.layoutManager = LinearLayoutManager(requireContext())

        recyclerView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 || recyclerView.computeVerticalScrollOffset() > 0) {
                    // Scrolling down or already scrolled, hide the FAB
                    binding?.fab?.hide()
                } else {
                    // Scrolling up, show the FAB
                    binding?.fab?.show()
                }
            }
        })

        binding?.fab?.setOnClickListener { openCashDetail("") }
        binding?.documentsSwipe?.setOnRefreshListener {
            sharedViewModel.callDiffSync {  }
        }
        sharedViewModel.isRefreshing.observe(this.viewLifecycleOwner) {
            binding?.documentsSwipe?.isRefreshing = it
        }

        val cashListAdapter = CashListAdapter { openCashDetail(it.guid) }
        recyclerView?.adapter = cashListAdapter
        setupSwipeToResend(recyclerView, cashListAdapter)
        viewModel.documents.observe(this.viewLifecycleOwner) {
            cashListAdapter.submitList(it)
        }
        viewModel.totals.observe(this.viewLifecycleOwner) {
            viewModel.updateCounters(it)
        }
        viewModel.listDate.observe(this.viewLifecycleOwner) {
            var title = getString(R.string.header_cash_list)
            if (it != null) {
                val formattedDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(it)
                title += ": $formattedDate"
            }
            (activity as AppCompatActivity).supportActionBar?.title = title
        }
    }

    private fun setupSwipeToResend(recyclerView: RecyclerView?, adapter: CashListAdapter) {
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.absoluteAdapterPosition
                val cash = adapter.currentList[position]
                if (cash.isSent == 1) {
                    viewModel.markReadyToSend(cash) {
                        Toast.makeText(requireContext(), R.string.marked_ready_to_send, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    adapter.notifyItemChanged(position)
                }
            }

            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val position = viewHolder.absoluteAdapterPosition
                val cash = adapter.currentList.getOrNull(position) ?: return 0
                return if (cash.isSent == 1) super.getSwipeDirs(recyclerView, viewHolder) else 0
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)

                if (dX <= 0) return

                val itemView = viewHolder.itemView
                val cornerRadius = 8f * resources.displayMetrics.density
                val icon = ContextCompat.getDrawable(requireContext(), R.drawable.baseline_cloud_upload_24) ?: return
                val backgroundColor = ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark)

                val paint = Paint().apply {
                    color = backgroundColor
                    isAntiAlias = true
                }

                val iconMargin = (itemView.height - icon.intrinsicHeight) / 2
                val iconTop = itemView.top + iconMargin
                val iconBottom = iconTop + icon.intrinsicHeight
                val iconLeft = itemView.left + iconMargin
                val iconRight = iconLeft + icon.intrinsicWidth
                icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)

                val background = RectF(
                    itemView.left.toFloat(),
                    itemView.top.toFloat(),
                    itemView.left + dX,
                    itemView.bottom.toFloat()
                )
                c.drawRoundRect(background, cornerRadius, cornerRadius, paint)

                icon.colorFilter = PorterDuffColorFilter(
                    ContextCompat.getColor(requireContext(), android.R.color.white),
                    PorterDuff.Mode.SRC_IN
                )
                icon.draw(c)
            }
        }

        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun openCashDetail(cashId: String) {
        val action = CashListFragmentDirections.actionCashListFragmentToCashFragment(
            cashGuid = cashId,
            clientGuid = null
        )
        view?.findNavController()?.navigate(action)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_documents_list, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        val calendar = Calendar.getInstance()
        when (menuItem.itemId) {
            R.id.action_search -> {
                viewModel.toggleSearchVisibility()
            }
            R.id.periodToday -> {
                viewModel.setDate(calendar.time)
            }
            R.id.periodYesterday -> {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                viewModel.setDate(calendar.time)
            }
            R.id.periodChoose -> {
                val datePicker = MaterialDatePicker.Builder.datePicker().build()
                datePicker.addOnPositiveButtonClickListener { selectedTimestamp ->
                    val selectedDate = Date(selectedTimestamp)
                    viewModel.setDate(selectedDate)
                }
                datePicker.show(parentFragmentManager, "DATE_PICKER_TAG")
            }
            R.id.periodNoLimits -> {
                viewModel.setDate(null)
            }
            else -> return false
        }
        return true
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }
}
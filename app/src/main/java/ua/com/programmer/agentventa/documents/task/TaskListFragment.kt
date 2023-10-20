package ua.com.programmer.agentventa.documents.task

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.databinding.ModelDocumentsListBinding
import ua.com.programmer.agentventa.shared.SharedViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class TaskListFragment: Fragment(), MenuProvider {

    private val viewModel: TaskListViewModel by viewModels()
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
        val recyclerView = binding?.documentsRecycler //todo: rename to taskListRecycler
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

        binding?.fab?.setOnClickListener { openTaskDetail("") }
        binding?.documentsSwipe?.setOnRefreshListener {
            sharedViewModel.callDiffSync {  }
        }
        sharedViewModel.isRefreshing.observe(this.viewLifecycleOwner) {
            binding?.documentsSwipe?.isRefreshing = it
        }

        val taskListAdapter = TaskListAdapter { openTaskDetail(it.guid) }
        recyclerView?.adapter = taskListAdapter
        viewModel.documents.observe(this.viewLifecycleOwner) {
            taskListAdapter.submitList(it)
        }
        viewModel.listDate.observe(this.viewLifecycleOwner) {
            var title = getString(R.string.header_tasks_list)
            if (it != null) {
                val formattedDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(it)
                title += ": $formattedDate"
            }
            (activity as AppCompatActivity).supportActionBar?.title = title
        }
    }

    private fun openTaskDetail(taskId: String) {
        val action = TaskListFragmentDirections.actionTaskListFragmentToTaskFragment(taskId)
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
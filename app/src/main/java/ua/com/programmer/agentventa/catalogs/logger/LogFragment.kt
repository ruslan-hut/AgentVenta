package ua.com.programmer.agentventa.catalogs.logger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.dao.entity.LogEvent
import ua.com.programmer.agentventa.databinding.LogFragmentBinding
import ua.com.programmer.agentventa.databinding.LogListItemBinding
import ua.com.programmer.agentventa.databinding.LogListItemErrorBinding
import ua.com.programmer.agentventa.databinding.LogListItemWarnBinding
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale

@AndroidEntryPoint
class LogFragment: Fragment(), MenuProvider {

    private val viewModel: LogViewModel by viewModels()
    private var _binding: LogFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LogFragmentBinding.inflate(inflater,container,false)
        binding.lifecycleOwner = viewLifecycleOwner

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = binding.logList
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = ItemsListAdapter(
            onItemClicked = { },
            onItemLongClicked = { }
        )
        viewModel.logs.observe(viewLifecycleOwner) {
            (recyclerView.adapter as ItemsListAdapter).submitList(it)
        }
        val menuHost : MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private class ItemsListAdapter(
        private val onItemClicked: (LogEvent) -> Unit,
        private val onItemLongClicked: (LogEvent) -> Unit)
        : ListAdapter<LogEvent, ItemsListAdapter.ItemViewHolder>(DiffCallback) {

        abstract class ItemViewHolder(binding: ViewDataBinding): RecyclerView.ViewHolder(binding.root) {

            abstract fun bind(item: LogEvent)

            fun dateLocal(time: Long): String {
                val calendar: Calendar = GregorianCalendar()
                calendar.timeInMillis = time
                val date = calendar.time
                return String.format(Locale.getDefault(), "%1\$td-%1\$tm-%1\$tY %1\$tH:%1\$tM", date)
            }
        }

        class DebugEventHolder(private var binding: LogListItemBinding): ItemViewHolder(binding) {
            override fun bind(item: LogEvent) {
                binding.apply {
                    logDate.text = dateLocal(item.timestamp)
                    logTag.text = item.tag
                    logMessage.text = item.message
                }
            }

        }

        class WarnEventHolder(private var binding: LogListItemWarnBinding): ItemViewHolder(binding) {
            override fun bind(item: LogEvent) {
                binding.apply {
                    logDate.text = dateLocal(item.timestamp)
                    logTag.text = item.tag
                    logMessage.text = item.message
                }
            }

        }

        class ErrorEventHolder(private var binding: LogListItemErrorBinding): ItemViewHolder(binding) {
            override fun bind(item: LogEvent) {
                binding.apply {
                    logDate.text = dateLocal(item.timestamp)
                    logTag.text = item.tag
                    logMessage.text = item.message
                }
            }

        }

        companion object {
            private val DiffCallback = object : DiffUtil.ItemCallback<LogEvent>() {

                override fun areItemsTheSame(oldItem: LogEvent, newItem: LogEvent): Boolean {
                    return oldItem.id == newItem.id
                }

                override fun areContentsTheSame(oldItem: LogEvent, newItem: LogEvent): Boolean {
                    return oldItem == newItem
                }

            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
            val viewHolder = when (viewType) {
                1 -> WarnEventHolder(LogListItemWarnBinding.inflate(LayoutInflater.from(parent.context), parent, false))
                2 -> ErrorEventHolder(LogListItemErrorBinding.inflate(LayoutInflater.from(parent.context), parent, false))
                else -> DebugEventHolder(LogListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            viewHolder.itemView.setOnClickListener {
                val position = viewHolder.absoluteAdapterPosition
                onItemClicked(getItem(position))
            }
            viewHolder.itemView.setOnLongClickListener {
                val position = viewHolder.absoluteAdapterPosition
                onItemLongClicked(getItem(position))
                true
            }
            return viewHolder
        }

        override fun getItemViewType(position: Int): Int {
            val item = getItem(position)
            return when (item.level) {
                "D" -> 0
                "W" -> 1
                "E" -> 2
                else -> 0
            }
        }

        override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_log, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.share -> {
                shareLogs()
            }
            else -> return false
        }
        return true
    }

    private fun shareLogs() {
        viewModel.shareLogs {
            if (it.isBlank()) {
                Toast.makeText(requireContext(), R.string.no_data_list, Toast.LENGTH_SHORT).show()
            } else {
                val clipboard = activity?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(getString(R.string.events_log), it)
                clipboard.setPrimaryClip(clip)
            }
        }
    }
}
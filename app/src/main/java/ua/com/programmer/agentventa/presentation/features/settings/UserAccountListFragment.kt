package ua.com.programmer.agentventa.presentation.features.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.data.local.entity.UserAccount
import ua.com.programmer.agentventa.data.local.entity.equalTo
import ua.com.programmer.agentventa.data.local.entity.getGuid
import ua.com.programmer.agentventa.databinding.ActivityConnectionsListBinding
import ua.com.programmer.agentventa.databinding.ConnectionsListItemBinding

@AndroidEntryPoint
class UserAccountListFragment: Fragment(), MenuProvider {

    private val viewModel: UserAccountListViewModel by activityViewModels()
    private var _binding: ActivityConnectionsListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityConnectionsListBinding.inflate(inflater,container,false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = ItemsListAdapter(
            onItemClicked = { item ->
                viewModel.setAccountAsCurrent(item)
            },
            onItemLongClicked = { item ->
                openAccount(item)
            }
        )
        val recycler = binding.listRecycler
        recycler.adapter = adapter
        recycler.layoutManager = LinearLayoutManager(requireContext())

        viewModel.accounts.observe(this.viewLifecycleOwner) { list ->
            adapter.submitList(list)
        }

        viewModel.alertMessage.observe(this.viewLifecycleOwner) { message ->
            if (message != null && message != 0) {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.error)
                    .setMessage(message)
                    .setPositiveButton(R.string.OK) { _, _ -> }
                    .show()
            }
        }

        val menuHost : MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        binding.fab.setOnClickListener { openAccount(null) }
    }

    private fun finish() {
        viewModel.checkCurrentAccount {
            view?.findNavController()?.popBackStack()
        }
    }

    private fun openAccount(account: UserAccount?) {
        val action = UserAccountListFragmentDirections.actionUserAccountListFragmentToUserAccountFragment(account?.guid)
        view?.findNavController()?.navigate(action)
    }

    private class ItemsListAdapter(
        private val onItemClicked: (UserAccount) -> Unit,
        private val onItemLongClicked: (UserAccount) -> Unit)
        : ListAdapter<UserAccount, ItemsListAdapter.ItemViewHolder>(DiffCallback) {

        class ItemViewHolder(private var binding: ConnectionsListItemBinding): RecyclerView.ViewHolder(binding.root) {
            fun bind(item: UserAccount) {
                binding.apply {
                    description.text = item.description
                    if (item.useWebSocket) {
                        server.setText(R.string.auto_connection)
                        user.setText(R.string.auto_connection)
                    } else {
                        server.text = item.dbServer
                        user.text = item.dbUser
                    }
                    guid.text = item.getGuid()
                    if (item.license.isNotEmpty()) {
                        licenseKey.text = item.license
                    } else {
                        licenseKey.setText(R.string.license_not_installed)
                    }
                    activeIcon.visibility = if (item.isCurrent == 1) View.VISIBLE else View.INVISIBLE
                }
            }
        }

        companion object {
            private val DiffCallback = object : DiffUtil.ItemCallback<UserAccount>() {

                override fun areItemsTheSame(oldItem: UserAccount, newItem: UserAccount): Boolean {
                    return oldItem.guid == newItem.guid
                }

                override fun areContentsTheSame(oldItem: UserAccount, newItem: UserAccount): Boolean {
                    return oldItem.equalTo(newItem)
                }

            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
            val viewHolder = ItemViewHolder(
                ConnectionsListItemBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )
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

        override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {

    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
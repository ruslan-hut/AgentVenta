package ua.com.programmer.agentventa.catalogs.client

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
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
import ua.com.programmer.agentventa.dao.entity.LClient
import ua.com.programmer.agentventa.databinding.ClientListFragmentBinding
import ua.com.programmer.agentventa.shared.SharedViewModel
import ua.com.programmer.agentventa.utility.Constants

@AndroidEntryPoint
class ClientListFragment: Fragment(), MenuProvider {

    private val viewModel: ClientListViewModel by viewModels()
    private val sharedModel: SharedViewModel by activityViewModels()
    private val navigationArgs: ClientListFragmentArgs by navArgs()
    private var _binding: ClientListFragmentBinding? = null
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
        _binding = ClientListFragmentBinding.inflate(inflater,container,false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        val menuHost : MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = binding.clientsList
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        binding.clientsSwipe.setOnRefreshListener {
            //TODO on swipe listener
            binding.clientsSwipe.isRefreshing = false
        }

        val adapter = ClientListAdapter { onItemClick(it) }
        recyclerView.adapter = adapter
        viewModel.clients.observe(this.viewLifecycleOwner) {
            adapter.submitList(it)
        }

        viewModel.currentGroup.observe(this.viewLifecycleOwner) {
            val desc = it?.description ?: ""
            val title = desc.ifBlank { getString(R.string.header_clients_list) }
            (requireActivity() as AppCompatActivity).supportActionBar?.title = title
        }

        sharedModel.sharedParams.observe(this.viewLifecycleOwner) { params ->

            viewModel.setListParameters(params)
            Log.d("ClientList", "params: $params")

            if (params.companyGuid.isNotBlank()) {
                binding.tableCompanyTop.visibility = View.VISIBLE
                binding.filterCompany.text = params.company
                binding.filterStore.text = params.store
            } else {
                binding.tableCompanyTop.visibility = View.GONE
            }
        }
    }

    private fun parentFragmentId(): Int {
        return if (viewModel.docType() == Constants.DOCUMENT_ORDER) R.id.orderFragment else R.id.cashFragment
    }

    private fun onItemClick(client: LClient) {
        if (client.isGroup) {
            val action = ClientListFragmentDirections.actionClientListFragmentSelf(
                groupGuid = client.guid,
                modeSelect = navigationArgs.modeSelect,
            )
            view?.findNavController()?.navigate(action)
        } else if (navigationArgs.modeSelect) {
            sharedModel.selectClientAction(client) {
                view?.findNavController()?.popBackStack(parentFragmentId(), false)
            }
        } else {
            val action = ClientListFragmentDirections.actionClientListFragmentToClientMenuFragment(
                clientGuid = client.guid
            )
            view?.findNavController()?.navigate(action)
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_clients, menu)
        if (!sharedModel.options.useCompanies) {
            menu.findItem(R.id.action_company).isVisible = false
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.action_search -> {
                viewModel.toggleSearchVisibility()
            }
            R.id.action_company -> {
                showPopupMenu(requireActivity().findViewById(R.id.action_company))
                true
            }

            else -> return false
        }
        return true
    }

    private fun showPopupMenu(anchor: View) {
        val popupMenu = PopupMenu(requireContext(), anchor)
        sharedModel.getCompanies { list ->
            list.forEachIndexed { index, item ->
                popupMenu.menu.add(0, index, index, item.description) // item.name — наименование
            }
            popupMenu.setOnMenuItemClickListener { menuItem ->
                val selectedItem = list[menuItem.itemId]
                sharedModel.setCompany(selectedItem.guid) // Передаем код выбранного элемента
                true
            }

            popupMenu.show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
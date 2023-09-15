package ua.com.programmer.agentventa.catalogs.client

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
import androidx.lifecycle.Lifecycle
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.databinding.ClientMenuFragmentBinding

@AndroidEntryPoint
class ClientFragment: Fragment(), MenuProvider {

    private val viewModel: ClientViewModel by activityViewModels()
    private val navigationArgs: ClientFragmentArgs by navArgs()
    private var _binding: ClientMenuFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setClientParameters(navigationArgs.clientGuid)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ClientMenuFragmentBinding.inflate(layoutInflater)
        binding.lifecycleOwner = viewLifecycleOwner

        val menuHost : MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        binding.container.adapter = ClientMenuPagerAdapter(this)
        TabLayoutMediator(binding.clientTabs, binding.container) { tab, position ->
            when (position) {
                1 -> tab.text = getString(R.string.debts_list)
                else -> tab.text = getString(R.string.title_data)
            }
        }.attach()

        viewModel.client.observe(viewLifecycleOwner) { client ->
            var title = getString(R.string.doc_client)
            client?.let{
               title += ": ${it.description}"
            }
            (requireActivity() as AppCompatActivity).supportActionBar?.title = title
        }

        return binding.root
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_client_info, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.pickup_location -> {
                val action = ClientFragmentDirections.actionClientFragmentToLocationPickupFragment(
                    viewModel.client.value?.guid ?: ""
                )
                view?.findNavController()?.navigate(action)
            }
            else -> return false
        }
        return true
    }

}

private class ClientMenuPagerAdapter(fragment: Fragment): FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        val fragment: Fragment = when (position) {
            1 -> ClientDebtsFragment()
            else -> ClientInfoFragment()
        }
        return fragment
    }

}
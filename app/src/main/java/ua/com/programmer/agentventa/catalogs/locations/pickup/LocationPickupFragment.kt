package ua.com.programmer.agentventa.catalogs.locations.pickup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.navArgs
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnMarkerDragListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.collections.MarkerManager
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.dao.entity.ClientLocation
import ua.com.programmer.agentventa.dao.entity.hasLocation
import ua.com.programmer.agentventa.databinding.LocationPickupFragmentBinding
import ua.com.programmer.agentventa.extensions.format
import ua.com.programmer.agentventa.shared.SharedViewModel


@AndroidEntryPoint
class LocationPickupFragment: Fragment(), MenuProvider, OnMapReadyCallback {

    private val viewModel: LocationPickupViewModel by viewModels()
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private val navigationArgs: LocationPickupFragmentArgs by navArgs()
    private var _binding: LocationPickupFragmentBinding? = null
    private val binding get() = _binding!!

    private var clientLocation: ClientLocation? = null
    private var map: GoogleMap? = null
    private var markerCollection: MarkerManager.Collection? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LocationPickupFragmentBinding.inflate(inflater,container,false)
        binding.lifecycleOwner = viewLifecycleOwner

        val menuHost : MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // account is necessary to save new location
        sharedViewModel.currentAccount.observe(viewLifecycleOwner) {
            viewModel.setAccountGuid(it.guid)
        }
        viewModel.clientLocation.observe(viewLifecycleOwner) {
            clientLocation = it
            showLocation()
        }
        viewModel.address.observe(viewLifecycleOwner) {
            binding.description.text = it
            binding.bottomBar.visibility = if (it.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        viewModel.setCanEditLocation(sharedViewModel.options.editLocations)

        // Get the SupportMapFragment and request notification when the map is ready to be used.
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_location_pickup, menu)
        if (!viewModel.canEditLocation) {
            menu.findItem(R.id.save_location).isVisible = false
            //menu.findItem(R.id.update_location).isVisible = false
            menu.findItem(R.id.clear_location).isVisible = false
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.save_location -> {
                if (viewModel.saveLocation()) {
                    Toast.makeText(requireContext(), R.string.data_saved, Toast.LENGTH_SHORT).show()
                }
            }
//            R.id.edit_location -> {
//                viewModel.onEditLocation()
//            }
            R.id.reset_location -> {
                showLocation()
            }
            R.id.clear_location -> {
                if (viewModel.onDeleteLocation()) {
                    markerCollection?.clear()
                    Toast.makeText(requireContext(), R.string.data_deleted, Toast.LENGTH_SHORT).show()
                }
            }
            else -> return false
        }
        return true
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map?.let {
            val markerManager = MarkerManager(map)
            markerCollection = markerManager.newCollection()
            markerCollection?.setOnMarkerDragListener (
                object : OnMarkerDragListener {
                        override fun onMarkerDragStart(marker: Marker) {
                            binding.coordinates.text = ""
                            binding.description.text = ""
                        }
                        override fun onMarkerDrag(marker: Marker) {
                            binding.coordinates.text = locationText(marker.position)
                        }
                        override fun onMarkerDragEnd(marker: Marker) {
                            binding.coordinates.text = locationText(marker.position)
                            viewModel.getAddress(marker.position.latitude, marker.position.longitude)
                        }
                }
            )
            viewModel.setClientParameters(navigationArgs.clientGuid)
        }

    }

    private fun showLocation() {
        markerCollection?.clear()
        //binding.bottomBar.visibility = View.VISIBLE
        val hasLocation = clientLocation?.hasLocation() ?: false
        if (!hasLocation) {
            val alert = AlertDialog.Builder(requireContext())
            alert.setTitle(R.string.warning)
            alert.setMessage(R.string.warn_no_location)
            alert.setPositiveButton(R.string.OK) { _, _ -> }
            alert.show()
            return
        }
        map?.let { m ->
            clientLocation?.let { c ->
                val location = LatLng(c.latitude, c.longitude)
                val options = MarkerOptions()
                    .position(location)
                    .draggable(viewModel.canEditLocation)
                val marker = markerCollection?.addMarker(options)
                marker?.apply {
                    tag = c.clientGuid
                    //title = c.description
                    snippet = c.address
                }
                binding.coordinates.text = locationText(location)
                m.moveCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                        location, 17f
                    ))
            }
        }
    }

    private fun locationText(location: LatLng): String {
        return location.latitude.format(6)+", "+location.longitude.format(6)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
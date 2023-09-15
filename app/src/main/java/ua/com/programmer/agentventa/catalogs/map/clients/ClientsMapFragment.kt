package ua.com.programmer.agentventa.catalogs.map.clients

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.collections.MarkerManager
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.dao.entity.ClientLocation
import ua.com.programmer.agentventa.dao.entity.hasLocation

class ClientsMapFragment: Fragment(), MenuProvider, OnMapReadyCallback {
    private val viewModel: ClientsMapViewModel by viewModels()
//    private var _binding: ClientsMapFragmentBinding? = null

//    private val binding get() = _binding!!

    private var map: GoogleMap? = null
    private var markerCollection: MarkerManager.Collection? = null

//    override fun onCreateView(
//        inflater: LayoutInflater,
//        container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View {
//        _binding = ClientsMapFragmentBinding.inflate(inflater,container,false)
//        binding.lifecycleOwner = viewLifecycleOwner
//
//        val menuHost : MenuHost = requireActivity()
//        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
//
//        return binding.root
//    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.clientsLocation.observe(viewLifecycleOwner) {
            showLocation(it)
        }

        // Get the SupportMapFragment and request notification when the map is ready to be used.
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_location_pickup, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.save_location -> {}
            //R.id.edit_location -> {}
            R.id.update_location -> {}
            R.id.clear_location -> {}
            else -> return false
        }
        return true
    }

    @SuppressLint("PotentialBehaviorOverride")
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map?.let {
            val markerManager = MarkerManager(map)
            markerCollection = markerManager.newCollection()
        }

        // Set a listener for marker click.
        map?.setOnMarkerClickListener { marker ->
            val clientGuid = marker.tag as String
            viewModel.clientsLocation.value?.find { it.clientGuid == clientGuid }?.let {
                selectLocation(it)
            }
            true
        }
        viewModel.setMapParameters()
    }

    private fun showLocation(clientsLocation: List<ClientLocation>) {
        markerCollection?.clear()
//        binding.bottomBar.visibility = View.VISIBLE
        map?.let {
            for (clientLocation in clientsLocation) {
                if (clientLocation.hasLocation()) {
                    clientLocation.let { c ->
                        val location = LatLng(c.latitude, c.longitude)
                        val options = MarkerOptions()
                            .position(location)
                        val marker = markerCollection?.addMarker(options)
                        marker?.apply {
                            tag = c.clientGuid
                            snippet = c.address
                        }
                    }
                }
            }
        }

        val center = centerOfMarkers(clientsLocation)
        selectLocation(center, 10f)
    }

    private fun selectLocation(clientLocation: ClientLocation, zoom: Float = 17f) {
        map?.let {m ->
            val location = LatLng(clientLocation.latitude, clientLocation.longitude)
            m.moveCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(location, zoom))
        }
    }

    private fun centerOfMarkers(markerCollection: List<ClientLocation>): ClientLocation {

        val totalPositions = markerCollection.size
        var totalLat = 0.0
        var totalLng = 0.0

        for (marker in markerCollection) {
            totalLat += marker.latitude
            totalLng += marker.longitude
        }

        val centerLat = totalLat / totalPositions
        val centerLng = totalLng / totalPositions

        return ClientLocation(
            clientGuid = "",
            address = "",
            latitude = centerLat,
            longitude = centerLng
        )
    }

//    override fun onDestroyView() {
//        super.onDestroyView()
//        _binding = null
//    }

}
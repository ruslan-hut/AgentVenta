package ua.com.programmer.agentventa.presentation.features.map.clients

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.collections.MarkerManager
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.data.local.entity.LClientLocation
import ua.com.programmer.agentventa.data.local.entity.LocationHistory
import ua.com.programmer.agentventa.data.local.entity.hasLocation
import ua.com.programmer.agentventa.databinding.LocationPickupFragmentBinding

@AndroidEntryPoint
class ClientsMapFragment: Fragment(), MenuProvider, OnMapReadyCallback {

    private val viewModel: ClientsMapViewModel by viewModels()
    private var _binding: LocationPickupFragmentBinding? = null
    private val binding get() = _binding!!

    private var map: GoogleMap? = null
    private var markerCollection: MarkerManager.Collection? = null
    private val markerTagCurrentLocation = "current_location"

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

        viewModel.locations.observe(viewLifecycleOwner) {
            showLocation(it)
        }

        // Get the SupportMapFragment and request notification when the map is ready to be used.
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        //menuInflater.inflate(R.menu.menu_location_pickup, menu)
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

        viewModel.locations.observe(viewLifecycleOwner) {
            showLocation(it)
        }
        viewModel.currentLocation.observe(viewLifecycleOwner) {
            showCurrentLocation(it)
        }
    }

    private fun currentLocationOptions(location: LocationHistory): MarkerOptions {
        val latLng = LatLng(location.latitude, location.longitude)
        return MarkerOptions()
            .position(latLng)
            //.flat(true)
            //.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            .icon(BitmapDescriptorFactory.fromResource(R.drawable.man_stands_green_32))
            .anchor(0.5f, 1f)
    }

    private fun clientLocationOptions(location: LClientLocation): MarkerOptions {
        val latLng = LatLng(location.latitude, location.longitude)
        return MarkerOptions()
            .position(latLng)
            .icon(BitmapDescriptorFactory.fromResource(R.drawable.map_marker_flag_red_32))
            .anchor(0.5f, 1f)
    }

    private fun showLocation(locations: List<LClientLocation>) {
        markerCollection?.markers?.forEach {
            if (it.tag != markerTagCurrentLocation) {
                it.remove()
            }
        }
        map?.let {
            for (location in locations) {
                if (location.hasLocation()) {
                    val marker = markerCollection?.addMarker(
                        clientLocationOptions(location)
                    )
                    marker?.apply {
                        tag = location.clientGuid
                        title = location.description
                        snippet = location.address
                    }
                }
            }
        }

        val center = centerOfMarkers(locations)
        selectLocation(center, 10f)
    }

    private fun showCurrentLocation(location: LocationHistory?) {
        if (location == null) return
        markerCollection?.let { m ->
            val latLng = LatLng(location.latitude, location.longitude)
            val marker = m.markers?.find {
                it.tag == markerTagCurrentLocation
            } ?: m.addMarker(
                currentLocationOptions(location)
            )
            marker?.apply {
                tag = markerTagCurrentLocation
                position = latLng
            }
            selectLocation(latLng, 17f)
        }
    }

    private fun selectLocation(center: LatLng, zoom: Float = 17f) {
        map?.moveCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(center, zoom))
    }

    private fun centerOfMarkers(markerCollection: List<LClientLocation>): LatLng {

        val totalPositions = markerCollection.size
        var totalLat = 0.0
        var totalLng = 0.0

        for (marker in markerCollection) {
            totalLat += marker.latitude
            totalLng += marker.longitude
        }

        val centerLat = totalLat / totalPositions
        val centerLng = totalLng / totalPositions

        return LatLng(centerLat, centerLng)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
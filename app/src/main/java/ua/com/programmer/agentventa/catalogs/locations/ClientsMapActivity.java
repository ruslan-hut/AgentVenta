package ua.com.programmer.agentventa.catalogs.locations;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.collections.MarkerManager;

import java.util.ArrayList;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import ua.com.programmer.agentventa.R;
import ua.com.programmer.agentventa.data.DataBase;
import ua.com.programmer.agentventa.settings.AppSettings;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.Utils;

@AndroidEntryPoint
public class ClientsMapActivity extends AppCompatActivity implements OnMapReadyCallback {

    @Inject DataBase database;
    @Inject AppSettings appSettings;
    private GoogleMap MAP;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private Marker currentLocationMarker;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private MarkerManager.Collection markerCollection;

    private final Utils utils = new Utils();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clients_map);
        setTitle(R.string.title_clients_map);
        ActionBar actionBar = getSupportActionBar();
        if(actionBar!=null){
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        if (appSettings.getLocationServiceEnabled()){
            locationCallback = new LocationCallback(){
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    for (Location location : locationResult.getLocations()){
                        updateCurrentLocation(location);
                    }
                }
            };
            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
            crateLocationRequest();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) onBackPressed();
        //if (id == R.id.update_location) initMap();
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (fusedLocationProviderClient != null)
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
    }

    @Override
    protected void onResume() {
        initMap();
        super.onResume();
    }

    private void initMap(){
        if (MAP != null) MAP.clear();
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        MAP = googleMap;

        MarkerManager markerManager = new MarkerManager(MAP);
        markerCollection = markerManager.newCollection();

        if (fusedLocationProviderClient != null){
            try {
                fusedLocationProviderClient.requestLocationUpdates(locationRequest,
                        locationCallback, Looper.getMainLooper());
            }catch (SecurityException e){
                utils.error("Clients map, request location updates: "+e);
            }
        }

        showSavedPoints();
    }

    private void showSavedPoints(){
        DataBaseItem lastSavedLocation = database.lastSavedLocationData();
        if (lastSavedLocation.hasValues()){
            LatLng position = new LatLng(lastSavedLocation.getDouble("latitude"),lastSavedLocation.getDouble("longitude"));
            MAP.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 17.0f));
        }

        LatLng defaultMapPosition = null;
        int counter = 0;
        ArrayList<DataBaseItem> group = database.getClientsLocations(null);
        if (!group.isEmpty()){
            for (DataBaseItem clientData: group){
                double lat = clientData.getDouble("latitude");
                double lng = clientData.getDouble("longitude");
                if (lat != 0 || lng != 0){
                    counter++;
                    defaultMapPosition = new LatLng(lat,lng);
                    BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE);
                    if (clientData.getLong("number") > 0){
                        bitmapDescriptor = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN);
                    }
                    markerCollection.addMarker(new MarkerOptions()
                            .position(defaultMapPosition)
                            .title(clientData.getString("description"))
                            .snippet(clientData.getString("address"))
                            .icon(bitmapDescriptor));
                }
            }
        }

        if (!lastSavedLocation.hasValues() && defaultMapPosition != null){
            MAP.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultMapPosition, 10.0f));
        }
        if (counter == 0) Toast.makeText(this, R.string.warn_no_clients_locations, Toast.LENGTH_LONG).show();
    }

    private void updateCurrentLocation(Location location) {
        if (location.getAccuracy() < 50) {
            LatLng position = new LatLng(location.getLatitude(), location.getLongitude());
            if (currentLocationMarker != null){
                currentLocationMarker.setPosition(position);
            }else if (markerCollection != null){
                currentLocationMarker = markerCollection.addMarker(new MarkerOptions()
                        .position(position)
                        .title(getString(R.string.you_are_here))
                        .draggable(false));
            }
        }
    }

    private void crateLocationRequest(){
        long updateInterval = 2*1000;
        long maxWaitTime = updateInterval*10;

        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, updateInterval)
                .setDurationMillis(maxWaitTime)
                .build();
    }
}

package ua.com.programmer.agentventa.catalogs.locations;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
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

import ua.com.programmer.agentventa.R;
import ua.com.programmer.agentventa.catalogs.Client;
import ua.com.programmer.agentventa.data.DataBase;
import ua.com.programmer.agentventa.settings.AppSettings;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.Utils;

public class LocationPickupActivity extends AppCompatActivity implements OnMapReadyCallback,
    GoogleMap.OnMarkerDragListener{

    private GoogleMap MAP;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private MarkerManager.Collection markerCollection;
    private Marker currentLocationMarker;
    private Marker lastSavedLocation;
    private int counter=0;
    private boolean canEditLocation;

    private DataBase database;
    private Client client;
    private TextView textCoordinates;
    private TextView textDescription;
    private LinearLayout bottomTab;
    private final LatLng defaultPosition = new LatLng(50.4499,30.5244);
    private ProgressBar progressBar;

    private final Utils utils = new Utils();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.location_pickup_fragment);
        setTitle(R.string.title_location_pickup);
        ActionBar actionBar = getSupportActionBar();
        if(actionBar!=null){
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        String clientGUID = getIntent().getStringExtra("client_guid");
        client = new Client(this, clientGUID);
        if (client.isNew()) {
            Toast.makeText(this, R.string.error_no_client, Toast.LENGTH_SHORT).show();
            finish();
        }
        database = DataBase.getInstance(this);

        locationCallback = new LocationCallback(){
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()){
                    updateCurrentLocation(location);
                }
            }
        };

        AppSettings appSettings = database.getAppSettings();
        if (appSettings.checkLocationServicePermission()){
            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
            crateLocationRequest();
            //startLocationUpdates();
        }

        canEditLocation = appSettings.readKeyBoolean(AppSettings.EDIT_CLIENT_LOCATION);

        textCoordinates = findViewById(R.id.coordinates);
        textDescription = findViewById(R.id.description);
        bottomTab = findViewById(R.id.bottom_bar);
        progressBar = findViewById(R.id.progress_bar);
    }

    private void startLocationUpdates(){
        counter = 0;
        try {
            progressBar.setVisibility(View.VISIBLE);
            fusedLocationProviderClient.requestLocationUpdates(locationRequest,
                    locationCallback, Looper.getMainLooper());
        }catch (SecurityException e){
            utils.log("e","requestLocationUpdates: "+e);
        }
    }

    private void crateLocationRequest(){
        long updateInterval = 2*1000;
        //long fastestUpdateInterval = updateInterval/2;
        long maxWaitTime = updateInterval*10;

        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, updateInterval)
                .setDurationMillis(maxWaitTime)
                .build();
    }

    @Override
    protected void onResume() {
        initMap();
        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_location_pickup,menu);
        return super.onCreateOptionsMenu(menu);
    }

    private void saveLocation(){
        if (!canEditLocation) return;
        if (currentLocationMarker != null){
            database.saveClientLocation(client.GUID,currentLocationMarker.getPosition());
            client = new Client(this,client.GUID);
        }
        initMap();
    }

    private void clearLocation(){
        if (!canEditLocation) return;
        LatLng noLocation = new LatLng(0,0);
        database.saveClientLocation(client.GUID,noLocation);
        client = new Client(this,client.GUID);
        initMap();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) onBackPressed();
        if (id == R.id.update_location) initMap();
        if (id == R.id.save_location) saveLocation();
        //if (id == R.id.edit_location) editLocationMode();
        if (id == R.id.clear_location) clearLocation();

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        MAP = googleMap;
        MarkerManager markerManager = new MarkerManager(MAP);
        markerCollection = markerManager.newCollection();
        markerCollection.setOnMarkerDragListener(this);
        showSavedPoint();
    }

    private void initMap(){
        if (MAP != null) MAP.clear();
        bottomTab.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        if (lastSavedLocation != null) lastSavedLocation.remove();
        if (currentLocationMarker != null) currentLocationMarker.remove();
        lastSavedLocation = null;
        currentLocationMarker = null;
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);
    }

    private void addMarkerForCurrentLocation(LatLng position){
        progressBar.setVisibility(View.GONE);
        //LatLng position = new LatLng(location.getLatitude(), location.getLongitude());
        currentLocationMarker = markerCollection.addMarker(new MarkerOptions()
                .position(position)
                .draggable(true));
        MAP.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 17.0f));
        Toast.makeText(this, R.string.hint_move_marker, Toast.LENGTH_SHORT).show();
    }

    private void setDefaultPosition(){
        LatLng position = defaultPosition;
        Location location = database.lastSavedLocation();
        if (location != null) position = new LatLng(location.getLatitude(), location.getLongitude());
        addMarkerForCurrentLocation(position);
    }

    private void editLocationMode(){
        if (lastSavedLocation != null) lastSavedLocation.remove();
        if (currentLocationMarker != null) currentLocationMarker.remove();
        lastSavedLocation = null;
        currentLocationMarker = null;

        if (!canEditLocation) return;

        if (fusedLocationProviderClient != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.title_request_location)
                    .setMessage(R.string.message_request_location)
                    .setPositiveButton(R.string.yes, (dialog, which) -> startLocationUpdates())
                    .setNegativeButton(R.string.no, (dialog, which) -> setDefaultPosition());
            builder.create().show();
        }else {
            setDefaultPosition();
        }
    }

    private void showSavedPoint(){
        LatLng clientLocation = client.getCoordinates();
        if (clientLocation != null) {
            if (lastSavedLocation == null) lastSavedLocation = markerCollection.addMarker(new MarkerOptions()
                    .position(clientLocation)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
            lastSavedLocation.setPosition(clientLocation);
            lastSavedLocation.setTitle(client.description);
            lastSavedLocation.setSnippet(client.address);

            MAP.moveCamera(CameraUpdateFactory.newLatLngZoom(clientLocation, 17.0f));
        }else {
            editLocationMode();
        }

        LatLng defaultMapPosition = null;
        ArrayList<DataBaseItem> group = database.getClientsLocations(client.getGroupGUID());
        for (DataBaseItem clientData: group){
            if (clientData.getString("guid").equals(client.GUID)) continue;
            double lat = clientData.getDouble("latitude");
            double lng = clientData.getDouble("longitude");
            if (lat != 0 || lng != 0){
                defaultMapPosition = new LatLng(lat,lng);
                BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE);
                if (clientData.getLong("number") > 0){
                    bitmapDescriptor = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN);
                }
                MAP.addMarker(new MarkerOptions()
                        .position(defaultMapPosition)
                        .title(clientData.getString("description"))
                        .snippet(clientData.getString("address"))
                        .icon(bitmapDescriptor));
            }
        }

        if (lastSavedLocation == null && currentLocationMarker == null && defaultMapPosition != null){
            MAP.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultMapPosition, 17.0f));
        }
    }

    private void requestAddressForLocation(LatLng position){
        String text = utils.format(position.latitude,4)+"; "+utils.format(position.longitude,4);
        textCoordinates.setText(text);
        textDescription.setText("");
        AddressHelper.getAddressFromLocation(position.latitude, position.longitude,
                getApplicationContext(), new Handler(Looper.getMainLooper()){
                    @Override
                    public void handleMessage(@NonNull Message msg) {
                        if (msg.what == 1){
                            Bundle bundle = msg.getData();
                            String address = bundle.getString("address");
                            if (address != null) updateText(address);
                        }
                        super.handleMessage(msg);
                    }
                });
    }

    private void updateText(String addressText){
        textDescription.setText(addressText);
    }

    private void updateCurrentLocation(Location location){
        counter++;
        if (currentLocationMarker != null) fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        else if (location.getAccuracy() < 50 || counter > 20) {
            LatLng position = new LatLng(location.getLatitude(), location.getLongitude());
            addMarkerForCurrentLocation(position);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (fusedLocationProviderClient != null)
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
    }

    @Override
    public void onMarkerDragStart(@NonNull Marker marker) {
        bottomTab.setVisibility(View.VISIBLE);
        textCoordinates.setText("");
        textDescription.setText("");
    }

    @Override
    public void onMarkerDrag(Marker marker) {
        LatLng position = marker.getPosition();
        String text = utils.format(position.latitude,4)+"; "+utils.format(position.longitude,4);
        textCoordinates.setText(text);
    }

    @Override
    public void onMarkerDragEnd(Marker marker) {
        requestAddressForLocation(marker.getPosition());
    }

}

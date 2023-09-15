package ua.com.programmer.agentventa;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.google.android.gms.location.Priority;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;

import android.os.Looper;
import android.view.View;
import com.google.android.material.navigation.NavigationView;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentTransaction;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.util.ArrayList;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import ua.com.programmer.agentventa.data.DataConnector;
import ua.com.programmer.agentventa.data.DataLoader;
import ua.com.programmer.agentventa.documents.DocumentActivity;
import ua.com.programmer.agentventa.documents.DocumentsListFragment;
import ua.com.programmer.agentventa.documents.TaskActivity;
import ua.com.programmer.agentventa.documents.TasksListFragment;
import ua.com.programmer.agentventa.documents.order.OrderListFragment;
import ua.com.programmer.agentventa.catalogs.locations.LocationHistoryActivity;
import ua.com.programmer.agentventa.catalogs.locations.LocationResultHelper;
import ua.com.programmer.agentventa.settings.AppSettings;
import ua.com.programmer.agentventa.utility.Constants;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.ImageLoader;
import ua.com.programmer.agentventa.utility.Utils;
import ua.com.programmer.agentventa.notifications.MessageReadingActivity;
import ua.com.programmer.agentventa.notifications.Notifications;

@Deprecated
@AndroidEntryPoint
public class MainNavigationActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
            DocumentsListFragment.OnFragmentInteractionListener,
            TasksListFragment.OnFragmentInteractionListener {

    private final int REQUEST_LOCATION_PERMISSION_CODE = 34;

    private long backPressedTime=0;
    private String fragmentTag="";
    private final Utils utils = new Utils();
    @Inject
    AppSettings appSettings;
    private long periodBegin;
    private Notifications notifications;

    private DataLoader dataLoader;
    private ImageLoader imageLoader;

    private LocationRequest locationRequest;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;

    private FragmentLoaderListener fragmentLoaderListener;

    public interface FragmentLoaderListener{
        void onDataLoaded(ArrayList<DataBaseItem> items);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_navigation);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(getString(R.string.app_name));

        appSettings.setSyncIsActive(false);
        final String userID = appSettings.getUserID();

        FirebaseCrashlytics.getInstance().setUserId(userID);

        //------------------------------------------------------------
        // migrate to a new connection settings system,
        // load current connection options
        StartUp.appLaunchAction(getApplicationContext());

        notifications = new Notifications(this);
        notifications.setContentTitle(R.string.notification_channel_data_loader);

        fragmentTag = appSettings.getFragmentTag();
        if (fragmentTag.isEmpty()) fragmentTag = Constants.DOCUMENT_ORDER;
        attachFragment();

        //------------------------------------------------------------
        // Navigation Drawer
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        switch (fragmentTag) {
            case Constants.DOCUMENT_ORDER -> navigationView.setCheckedItem(R.id.orderListFragment);
            //case Constants.DOCUMENT_CASH -> navigationView.setCheckedItem(R.id.nav_cash);
            //case Constants.DOCUMENT_TASK -> navigationView.setCheckedItem(R.id.nav_tasks);
        }

        //------------------------------------------------------------
        // Navigation Header elements
        View navHeader = navigationView.getHeaderView(0);

        TextView textVersion = navHeader.findViewById(R.id.text_version);
        final String versionText = getString(R.string.app_name);
        textVersion.setText(versionText);
        textVersion.setOnClickListener((View view) -> {
            StartUp.sendLogs(this);
            onSendLogsAction();
        });

        TextView textID = navHeader.findViewById(R.id.text_agent_id);
        final String textUserID = BuildConfig.VERSION_NAME+" ("+userID.substring(0,8)+")";
        textID.setText(textUserID);
        //ImageView navImage = navHeader.findViewById(R.id.image_logo);

        //------------------------------------------------------------
        SwitchCompat offlineMode = navHeader.findViewById(R.id.offline_switch);
        offlineMode.setChecked(appSettings.offlineMode());
        offlineMode.setOnClickListener((View v) -> appSettings.setOfflineMode(offlineMode.isChecked()));

        //------------------------------------------------------------
        // show message from notification if any
        showMessageFromNotification();

        //------------------------------------------------------------
        // old documents deletion
        dataLoader = new DataLoader(this,this::onDataLoaded);
        dataLoader.deleteOldData();

        //------------------------------------------------------------
        // create ImageLoader to band Glide with activity lifecycle
        imageLoader = new ImageLoader(this);

        //------------------------------------------------------------
        // location service
        if (appSettings.getLocationServiceEnabled()){
            if (checkLocationServicePermission()) {
                buildGoogleApiClient();
            }else {
                requestLocationServicePermission();
            }
        }

        fireBaseAuthentication();

    }

    private void showMessageFromNotification(){
        Intent messageIntent = getIntent();
        if (messageIntent.getExtras() != null){
            String title = messageIntent.getStringExtra("title");
            String body = messageIntent.getStringExtra("body");
            String document_guid = messageIntent.getStringExtra("document_guid");
            if (title != null && body != null){
                Intent messageReadIntent = new Intent(this, MessageReadingActivity.class);
                messageReadIntent.putExtra("title", title);
                messageReadIntent.putExtra("body", body);
                messageReadIntent.putExtra("document_guid", document_guid);
                startActivity(messageReadIntent);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        appSettings.saveFragmentTag(fragmentTag);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            if (backPressedTime+2000>System.currentTimeMillis()) {
                super.onBackPressed();
            }else {
                Toast.makeText(this, R.string.hint_press_back, Toast.LENGTH_SHORT).show();
                backPressedTime = System.currentTimeMillis();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (fusedLocationProviderClient != null) fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        if (imageLoader != null) imageLoader.stop();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        //header text may changed
        NavigationView navigationView = findViewById(R.id.nav_view);
        View navHeader = navigationView.getHeaderView(0);
        TextView connectionNameText = navHeader.findViewById(R.id.connection_name);
        connectionNameText.setText(appSettings.getConnectionName());

        //setting visibility of menu items according to the options
//        Menu navigationMenu = navigationView.getMenu();
//        setMenuItemVisibility(navigationMenu, R.id.nav_clients_directions, appSettings.getClientsDirections());
//
//        boolean visibility = appSettings.keyIsActive();
//        setMenuItemVisibility(navigationMenu, R.id.clients_map, visibility);
//        setMenuItemVisibility(navigationMenu, R.id.nav_locations, visibility);

        buildGoogleApiClient();
        super.onResume();
    }

    private void setMenuItemVisibility(Menu menu, int itemID, boolean visibility){
        MenuItem menuItem = menu.findItem(itemID);
        if (menuItem != null) menuItem.setVisible(visibility);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        invalidateOptionsMenu();
        if (fragmentTag.isEmpty()){
            getMenuInflater().inflate(R.menu.main_navigation, menu);
        }
        return true;
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Intent intent;
        int id = item.getItemId();

        if (appSettings.getSyncIsActive()){
            Toast.makeText(this, R.string.action_sync_warn, Toast.LENGTH_SHORT).show();
            return true;
        }

//        if (id == R.id.orderListFragment) {
//            fragmentTag = Constants.DOCUMENT_ORDER;
//            attachFragment();
//        }else if (id == R.id.nav_cash){
//            fragmentTag = Constants.DOCUMENT_CASH;
//            attachFragment();
//        }else if (id == R.id.nav_tasks){
//            fragmentTag = Constants.DOCUMENT_TASK;
//            attachFragment();
//        }else if (id == R.id.nav_goods){
//            intent = new Intent(this, GoodsActivity.class);
//            startActivity(intent);
//        }else if (id == R.id.nav_clients){
//            intent = new Intent(this, ClientsActivity.class);
//            startActivity(intent);
//        }else if (id == R.id.nav_clients_directions){
//            intent = new Intent(this, ClientsDirectionsActivity.class);
//            startActivity(intent);
//        }else if (id == R.id.nav_settings){
//            intent = new Intent(this, SettingsActivity.class);
//            startActivity(intent);
//        }else if (id == R.id.nav_sync){
//            startSync(Constants.UPDATE_ALL);
//        }else if (id == R.id.nav_locations){
//            openLocationHistory();
//        }else if (id == R.id.clients_map){
//            startActivity(new Intent(this, ClientsMapActivity.class));
//        }else if (id == R.id.nav_connections){
//            startActivity(new Intent(this, UserAccountListActivity.class));
//        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void setTitle(int titleId) {
        if (fragmentTag != null) {
            String title = getResources().getString(utils.getPageTitleID(fragmentTag));
            if (periodBegin != 0) {
                title = title + ": " + utils.dateLocalShort(periodBegin);
            }
            super.setTitle(title);
        }else {
            super.setTitle(titleId);
        }
    }

    private void startSync(String mode){
        String syncFormat = appSettings.getSyncFormat();
        if (syncFormat == null || syncFormat.isEmpty()) {
            Toast.makeText(this, R.string.error_sync_format_not_set, Toast.LENGTH_SHORT).show();
        }else if (appSettings.offlineMode()) {
            Toast.makeText(this, R.string.pref_title_offline_mode, Toast.LENGTH_SHORT).show();
        }else{
            notifications.setSmallIcon(R.drawable.baseline_sync_24);
            notifications.setContentText(getString(R.string.status_downloading));
            notifications.show();

            DataConnector connector = DataConnector.getInstance(this);
            connector.addListener(this::onDataConnectorResult);
            connector.addProgressListener(this::onProgressUpdate);
            switch (mode) {
                case Constants.UPDATE_ALL -> connector.updateAll();
                case Constants.UPDATE_SEND_DOCUMENTS -> connector.sendData();
            }
        }
    }

    /**
     * Listener for receiving a message about data exchange result;
     * Input data contains set of data type codes, if request was successful or error code
     *
     * @param result set of result codes
     */
    private void onDataConnectorResult(DataBaseItem result){
        //if Toast here, cause of error: Can't toast on a thread that has not called Looper.prepare()

        String errorCode = result.getString(Constants.DATA_ERROR_CODE);

        if (errorCode.isEmpty()) {

            if (result.hasValues()) {
                notifications.setSmallIcon(R.drawable.dot_green);
                notifications.setContentText(result);
                notifications.show();
            }else {
                notifications.dismiss();
            }

            if (imageLoader != null) imageLoader.requestImages();

        }else{

            StringBuilder text = new StringBuilder();
            text.append(getString(R.string.request_error));
            text.append(": ");
            @SuppressLint("DiscouragedApi")
            int resID = getResources().getIdentifier(errorCode,"string",getPackageName());
            if (resID > 0) {
                text.append(getString(resID));
            }else{
                text.append(errorCode);
            }

            notifications.setSmallIcon(R.drawable.dot_red);
            notifications.setContentText(text.toString());
            notifications.show();

        }

        onDataUpdateRequest(periodBegin,"", fragmentLoaderListener);
    }

    /**
     * Listener for progress updates from DataConnector
     *
     * @param max progress max value
     * @param current progress current value
     */
    public void onProgressUpdate(int max, int current) {
        if (notifications != null) {
            if (current < 0) {
                notifications.dismissProgress();
            }else {
                notifications.setProgress(max,current);
            }
        }
    }

    private void attachFragment(){
        if (fragmentTag == null) return;

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        switch (fragmentTag) {
            case Constants.DOCUMENT_ORDER ->
                    transaction.replace(R.id.container, new OrderListFragment());
            case Constants.DOCUMENT_CASH ->
                    transaction.replace(R.id.container, new DocumentsListFragment());
            case Constants.DOCUMENT_TASK ->
                    transaction.replace(R.id.container, new TasksListFragment());
        }

        transaction.commit();

        setTitle(utils.getPageTitleID(fragmentTag));
    }

    public synchronized void onDataLoaded(ArrayList<DataBaseItem> items) {
        if (fragmentLoaderListener != null) fragmentLoaderListener.onDataLoaded(items);
    }

    @Override
    public void onDataUpdateRequest(long periodBegin, String filter, FragmentLoaderListener listener) {
        this.periodBegin = periodBegin;

        runOnUiThread(() -> setTitle(utils.getPageTitleID(fragmentTag)));

        fragmentLoaderListener = listener;

        if (dataLoader == null) dataLoader = new DataLoader(this,this::onDataLoaded);
        dataLoader.loadDocuments(fragmentTag, periodBegin, filter);
    }

    @Override
    public void onSendDataRequest() {
        startSync(Constants.UPDATE_SEND_DOCUMENTS);
    }

    @Override
    public void onListItemClick(String guid) {
        Intent intent;
        switch (fragmentTag){
            case Constants.DOCUMENT_CASH: intent = new Intent(this, DocumentActivity.class); break;
            case Constants.DOCUMENT_TASK: intent = new Intent(this, TaskActivity.class); break;
            default: return;
        }
        intent.putExtra("guid", guid);
        intent.putExtra("tag", fragmentTag);
        startActivity(intent);
    }

    private void openLocationHistory(){
        if( !(appSettings.getLocationServiceEnabled() && checkLocationServicePermission()) ) {
            Toast.makeText(this, R.string.error_location_service_disabled, Toast.LENGTH_SHORT).show();
        }
        Intent intent = new Intent(this, LocationHistoryActivity.class);
        startActivity(intent);
    }

    //===================================================
    //  permission manage
    //===================================================

    private boolean checkLocationServicePermission(){
        return appSettings.checkLocationServicePermission();
    }

    private void requestLocationServicePermission(){
        boolean shouldProvideRationale =
                ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.ACCESS_FINE_LOCATION);

        if (shouldProvideRationale) {
            Snackbar.make(
                    findViewById(R.id.drawer_layout),
                    R.string.location_permission_rationale,
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.OK, (View v) ->
                            ActivityCompat.requestPermissions(MainNavigationActivity.this,
                                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                    REQUEST_LOCATION_PERMISSION_CODE))
                    .show();
        }else {
            ActivityCompat.requestPermissions(MainNavigationActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (requestCode == REQUEST_LOCATION_PERMISSION_CODE){
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                buildGoogleApiClient();
            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    //===================================================
    //  location service client
    //===================================================

    private void buildGoogleApiClient(){
        if (!appSettings.getLocationServiceEnabled() || !checkLocationServicePermission()){
            return;
        }

        if (fusedLocationProviderClient != null){
            requestLocationUpdates();
            return;
        }

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback(){

            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                LocationResultHelper locationResultHelper =
                        new LocationResultHelper(MainNavigationActivity.this,
                        locationResult.getLocations());
                locationResultHelper.saveResults();
                super.onLocationResult(locationResult);
            }
        };

        crateLocationRequest();

        requestLocationUpdates();
    }

    private void crateLocationRequest(){
        long updateInterval = 2*1000;
        //long fastestUpdateInterval = updateInterval/2;
        long maxWaitTime = updateInterval*10;

        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, updateInterval)
                .setDurationMillis(maxWaitTime)
                .build();
    }

    void requestLocationUpdates(){
        try {
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }catch (SecurityException e){
            utils.error("requestLocationUpdates: "+e);
        }
    }

    private void fireBaseAuthentication(){
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            utils.debug("Firebase: going to authenticate");
            auth.signInWithEmailAndPassword("support@programmer.com.ua", "aabbccddgg")
                    .addOnSuccessListener(authResult -> sendData())
                    .addOnFailureListener(e -> utils.warn("Firebase: auth task unsuccessful; "+e));
        } else {
            sendData();
        }
    }

    private void sendData(){

        appSettings.getLicenseKey();
        appSettings.checkLicenseKey();
        appSettings.writeUserData();

        //StartUp.sendLogs(this);
        StartUp.cloudMaintenance(this);

    }

    private void onSendLogsAction(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.message_send_logs)
                .setTitle(R.string.warning)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.putExtra(Intent.EXTRA_TEXT, utils.readLogs().toString());
                    intent.setType("text/plain");
                    startActivity(intent);
                })
                .setNegativeButton(R.string.cancel, null);
        builder.create().show();
    }

}

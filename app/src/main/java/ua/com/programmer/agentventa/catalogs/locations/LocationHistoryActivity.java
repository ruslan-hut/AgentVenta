package ua.com.programmer.agentventa.catalogs.locations;

import ua.com.programmer.agentventa.data.DataBase;
import ua.com.programmer.agentventa.data.DataLoader;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.R;
import ua.com.programmer.agentventa.settings.AppSettings;
import ua.com.programmer.agentventa.utility.Utils;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.graphics.Color;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CustomCap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.PolyUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public class LocationHistoryActivity extends AppCompatActivity implements OnMapReadyCallback,
        AdapterView.OnItemSelectedListener,
        DirectionsHelper.DirectionsHelperListener {

    private static final String STATE_MAP_IS_VISIBLE = "mapIsVisible";
    private static final String STATE_TIME = "timeToShow";
    private static final String STATE_CURRENT_USER = "currentUser";

    private static ArrayList<DataBaseItem> listItems;

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private ListAdapter listAdapter;
    private LinearLayout noDataWarning;
    private ProgressBar progressBar;

    private UserListAdapter userListAdapter;
    private Spinner userListSpinner;
    private String currentUserID;
    private String userID;
    private DirectionsHelper directionsHelper;
    private DataBaseItem lastSavedLocation;

    private TextView textPoints;
    private TextView textLength;
    private TextView textDate;
    private GoogleMap mMap;
    private boolean mapIsVisible;
    private long timeToShow;

    private final Utils utils = new Utils();
    private DataLoader dataLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_history);
        setTitle(R.string.title_locations_history);
        setupActionBar();

        textPoints = findViewById(R.id.points_number);
        textLength = findViewById(R.id.track_length);
        textDate = findViewById(R.id.date);

        DataBase database = DataBase.getInstance(this);

        lastSavedLocation = database.lastSavedLocationData();

        AppSettings appSettings = database.getAppSettings();
        userID = appSettings.getUserID();
        directionsHelper = new DirectionsHelper(this,appSettings.getMapsApiKey());

        mapIsVisible = false;
        timeToShow = currentTime();

        if (savedInstanceState != null){
            mapIsVisible = savedInstanceState.getBoolean(STATE_MAP_IS_VISIBLE);
            timeToShow = savedInstanceState.getLong(STATE_TIME);
            currentUserID = savedInstanceState.getString(STATE_CURRENT_USER);
        }
        if (currentUserID == null) currentUserID = userID;

        noDataWarning = findViewById(R.id.no_data_warning);
        noDataWarning.setVisibility(View.GONE);

        mSwipeRefreshLayout = findViewById(R.id.swipe_layout);
        mSwipeRefreshLayout.setOnRefreshListener(this::loadDataFromServer);

        progressBar = findViewById(R.id.progress);
        progressBar.setVisibility(View.GONE);

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(linearLayoutManager);

        listAdapter = new ListAdapter();
        recyclerView.setAdapter(listAdapter);

        userListAdapter = new UserListAdapter(this,R.layout.user_list_item);
        userListSpinner = findViewById(R.id.user_list);
        userListSpinner.setAdapter(userListAdapter);
        userListSpinner.setOnItemSelectedListener(this);

        //setMapVisibility();
        showListTotals();
    }

    /**
     * Returns time in millis for the beginning of today.
     *
     * @return time in millis
     */
    private long currentTime(){
        Calendar calendar = GregorianCalendar.getInstance(TimeZone.getTimeZone("UTC"));
        long time = calendar.getTimeInMillis() / 86400000;
        time = time * 86400000;
        return time;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_MAP_IS_VISIBLE,mapIsVisible);
        outState.putLong(STATE_TIME,timeToShow);
        outState.putString(STATE_CURRENT_USER,currentUserID);
        if (listItems == null){
            listItems = new ArrayList<>();
        }
        listItems.clear();
        listItems.addAll(listAdapter.getListItems());
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        dataLoader = new DataLoader(this,this::onDataLoaded);
        initMap();
        initUserList();
        //if (userList != null) loadUserListItems(userList);

        if (listItems != null) {
            onFirestoreResult(listItems);
        }else {
            loadDataFromServer();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_location_history,menu);
        return true;
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        if (listAdapter.getItemCount() > 0) {
            showSelectedPointsOnMap();
        }else {
            setMapVisibility();
        }
    }

    private void initMap(){
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);
    }

    private void setMapVisibility(){
        View mapView = findViewById(R.id.map);
        if (mMap != null && mapView != null) {
            if (mapIsVisible) {
                mapView.setVisibility(View.VISIBLE);
                mSwipeRefreshLayout.setVisibility(View.GONE);
            }else {
                mapView.setVisibility(View.GONE);
                mSwipeRefreshLayout.setVisibility(View.VISIBLE);
            }
        }
    }

    private void setCurrentPointOnMap(DataBaseItem listItem){
        if (mMap != null && listItem != null){
            mMap.clear();
            LatLng location = new LatLng(listItem.getDouble("latitude"),
                    listItem.getDouble("longitude"));
            mMap.addMarker(new MarkerOptions()
                    .position(location)
                    .title(listItem.getString("date")));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location,17.0f));

            mapIsVisible = true;
            setMapVisibility();
        }
    }

    private boolean addEncodedPoints(PolylineOptions options){
        boolean hasPolyline = false;
        for (DataBaseItem item: listAdapter.getListItems()){
            String polyline = item.getString("polyline");
            if (!polyline.equals("")){
                options.addAll(PolyUtil.decode(polyline));
                hasPolyline = true;
            }
        }
        return hasPolyline;
    }

    private void showSelectedPointsOnMap(){

        if (mMap == null){
            return;
        }

        mMap.clear();
        ArrayList<LatLng> points = new ArrayList<>();
        ArrayList<DataBaseItem> listItems = listAdapter.getListItems();

        for (DataBaseItem item: listItems){
            LatLng point = new LatLng(
                    item.getDouble("latitude"),
                    item.getDouble("longitude")
            );
            points.add(point);
        }

        boolean routeMode = false;

        if (points.size() > 0) {
            PolylineOptions polylineOptions = new PolylineOptions();
            polylineOptions.width(16);
            polylineOptions.geodesic(true);

            routeMode = addEncodedPoints(polylineOptions);

            if (routeMode) {
                polylineOptions.color(Color.RED);
            }else {
                polylineOptions.color(Color.GREEN);
                polylineOptions.addAll(points);
            }

            polylineOptions.startCap(new CustomCap(BitmapDescriptorFactory.fromResource(R.drawable.dot_green)));
            polylineOptions.endCap(new CustomCap(BitmapDescriptorFactory.fromResource(R.drawable.dot_red)));

            mMap.addPolyline(polylineOptions);

            mapIsVisible = true;
            setMapVisibility();

            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(points.get(0),15.0f));
        }

        if (!routeMode){
            //points with timestamp
            for (DataBaseItem item: listItems){
                LatLng point = new LatLng(
                        item.getDouble("latitude"),
                        item.getDouble("longitude")
                );
                mMap.addMarker(new MarkerOptions()
                        .position(point)
                        .title(item.getString("date")));
            }
        }

        addClientsLocations();

    }

    private void addClientsLocations(){
        dataLoader.loadClientsLocationsFromOrders(timeToShow/1000);
    }

    public synchronized void onDataLoaded(ArrayList<DataBaseItem> items) {
        if (mMap == null){
            return;
        }
        String order = getString(R.string.order)+" "+getString(R.string.title_order_number);
        String sum = getString(R.string.sum);

        for (int i=0; i<items.size(); i++){
            DataBaseItem item;
            try {
                item = items.get(i);
            }catch (Exception e){
                utils.debug("LocationHistoryActivity: array is modified while iterated");
                break;
            }
            LatLng point = new LatLng(
                    item.getDouble("latitude"),
                    item.getDouble("longitude")
            );
            String title = item.getString("client_description");
            String snippet = order+item.getString("number")+"; "+sum+" "+utils.format(item.getDouble("price"),2);
            mMap.addMarker(new MarkerOptions()
                    .position(point)
                    .title(title)
                    .snippet(snippet));
        }
    }

    @Override
    public void onBackPressed() {
        View rootView = findViewById(R.id.root_view);
        Snackbar snackbar = Snackbar.make(rootView,R.string.ask_close_map_view,Snackbar.LENGTH_LONG)
                .setAction(R.string.close, (View view) -> {
                    //userList must be nulled to enforce loading it from server
                    //next time, activity starts
                    //userList = null;
                    listItems = null;
                    finish();
                });
        snackbar.show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) onBackPressed();
        if (id == R.id.show_map) {
            mapIsVisible = !mapIsVisible;
            setMapVisibility();
        }
        if (id == R.id.show_track) showOriginalTrackPoints();
        if (id == R.id.show_route) loadDataFromServer();
        if (id == R.id.pick_date) pickDate();
        if (id == R.id.recalc_route) recalculateRoute();

        return super.onOptionsItemSelected(item);
    }

    private void pickDate (){
        final Calendar calendar = new GregorianCalendar();
        calendar.setTime(new Date(timeToShow));
        int Y = calendar.get(Calendar.YEAR);
        int M = calendar.get(Calendar.MONTH);
        int D = calendar.get(Calendar.DATE);
        AlertDialog dialog = new DatePickerDialog(this,
                (DatePicker view, int year, int month, int dayOfMonth) -> {
                    Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
                    cal.set(year,month,dayOfMonth,0,0,0);
                    timeToShow = cal.getTimeInMillis()/1000;
                    timeToShow = timeToShow*1000;
                    loadDataFromServer();
                },Y,M,D);
        dialog.show();
    }

    private void setupActionBar(){
        ActionBar actionBar = getSupportActionBar();
        if(actionBar!=null){
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    private void loadUserListItems(ArrayList<DataBaseItem> items){
        userListAdapter.clear();
        userListAdapter.addAll(items);
        userListAdapter.notifyDataSetChanged();
        userListSpinner.setSelection(0);
    }

    private void initUserList(){
        AppSettings appSettings = AppSettings.getInstance(this);
        ArrayList<DataBaseItem> savedList = appSettings.getLocationsWatchList();

        DataBaseItem item = new DataBaseItem();
        item.put("userID",userID);
        item.put("name",getResources().getString(R.string.title_own_track));
        savedList.add(0,item);

        loadUserListItems(savedList);
    }

    private void onFirestoreResult(ArrayList<DataBaseItem> items){
        if (mSwipeRefreshLayout.isRefreshing()){
            mSwipeRefreshLayout.setRefreshing(false);
        }
        progressBar.setVisibility(View.GONE);
        if (items.size() == 0) {
            if (lastSavedLocation.hasValues()) items.add(lastSavedLocation);
            else {
                noDataWarning.setVisibility(View.VISIBLE);
            }
        }else {
            noDataWarning.setVisibility(View.GONE);
        }
        listAdapter.loadItems(items);

        if (listItems == null){
            listItems = new ArrayList<>();
        }
        listItems.clear();
        listItems.addAll(items);

        showListTotals();

        if (mMap !=null && items.size() > 0) {
            showSelectedPointsOnMap();
        }else{
            setMapVisibility();
        }

    }

    @Override
    public void onRouteLoaded(ArrayList<DataBaseItem> route) {
        onFirestoreResult(route);
    }

    @Override
    public void onCalculationProgressUpdate(int progress) {
        if (mSwipeRefreshLayout.isRefreshing()){
            mSwipeRefreshLayout.setRefreshing(false);
        }
        if (progressBar.isIndeterminate()){
            progressBar.setIndeterminate(false);
            progressBar.setMax(100);
        }
        progressBar.setProgress(progress);
    }

    @Override
    public void onAPILimitWarn() {
        View rootView = findViewById(R.id.root_view);
        Snackbar snackbar = Snackbar.make(rootView,R.string.directions_api_limit_warn,Snackbar.LENGTH_LONG)
                .setAction(R.string.close, null);
        snackbar.show();
    }

    private void showOriginalTrackPoints(){
        noDataWarning.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(true);
        if (mMap != null){
            mMap.clear();
        }
        directionsHelper.getLocations(currentUserID,timeToShow);
    }

    private void recalculateRoute(){
        noDataWarning.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(true);
        if (mMap != null){
            mMap.clear();
        }
        directionsHelper.recalculateRoute(currentUserID,timeToShow);
    }

    private void loadDataFromServer(){
        noDataWarning.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(true);
        if (mMap != null){
            mMap.clear();
        }
        directionsHelper.getRoute(currentUserID,timeToShow);
    }

    private void showListTotals(){
        double pointsNumber = 0;
        double trackLength = 0.0;
        for (DataBaseItem item: listAdapter.getListItems()){
            if (item.getInt("selected") == 1){
                pointsNumber++;
                trackLength = trackLength + item.getDouble("distance");
            }
        }
        trackLength = trackLength/1000;
        textPoints.setText(utils.format(pointsNumber,0));
        textLength.setText(utils.format(trackLength,1));

        textDate.setText(utils.dateLocalShort(timeToShow/1000));
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        String selected = userListAdapter.getUserID(i);
        if (selected != null && !selected.equals(currentUserID)) {
            currentUserID = selected;
            loadDataFromServer();
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    private class ListAdapter extends RecyclerView.Adapter<ListItemViewHolder>{

        private final ArrayList<DataBaseItem> cursorItems = new ArrayList<>();

        ListAdapter(){}

        @SuppressLint("NotifyDataSetChanged")
        void loadItems(ArrayList<DataBaseItem> items){
            cursorItems.clear();
            cursorItems.addAll(items);
            notifyDataSetChanged();
        }

        ArrayList<DataBaseItem> getListItems(){
            return cursorItems;
        }

        @Override
        public int getItemCount() {
            return cursorItems.size();
        }

        @NonNull
        @Override
        public ListItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.location_history_item_record,parent,false);
            return new ListItemViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ListItemViewHolder holder, int position) {
            DataBaseItem item = cursorItems.get(position);
            item.put("position",position);
            holder.setItemData(item);
        }
    }

    private class ListItemViewHolder extends RecyclerView.ViewHolder{

        private final View view;

        ListItemViewHolder(View v){
            super(v);
            this.view = v;
        }

        private void setText(int id, String text){
            TextView textView = view.findViewById(id);
            if (textView != null){
                textView.setText(text);
            }
        }

        void setItemData(DataBaseItem item){
            //boolean isSelected = item.getInt("selected") == 1;
            String name = item.getString("name");
            if (name.equals("")) {
                setText(R.id.time, item.getString("date"));
            }else {
                setText(R.id.time,name);
            }

            final double latitude = item.getDouble("latitude");
            final double longitude = item.getDouble("longitude");
            setText(R.id.latitude,utils.format(latitude,6));
            setText(R.id.longitude,utils.format(longitude,6));

            String speed = utils.round(item.getDouble("speed")*3600/1000,0)+" km/h";
            setText(R.id.speed,speed);

            String distance = utils.round(item.getDouble("distance"),1)+" m";
            setText(R.id.distance,distance);

            view.setOnClickListener((View view) -> setCurrentPointOnMap(item));

            ImageView mapIcon = view.findViewById(R.id.map_icon);
            mapIcon.setOnClickListener((View v) ->{
                if (item.getInt("selected") == 1) {
                    item.put("selected",0);
                }else {
                    item.put("selected",1);
                }
                listAdapter.notifyItemChanged(item.getInt("position"));
                showListTotals();
            });

            mapIcon.setVisibility(View.INVISIBLE);
        }
    }

    private class UserListAdapter extends ArrayAdapter<DataBaseItem>{

        UserListAdapter(Context context, int resID){
            super(context,resID);
        }

        String getUserID(int position){
            DataBaseItem adapterItem = new DataBaseItem();
            if (position < this.getCount()) adapterItem = getItem(position);
            return adapterItem != null ? adapterItem.getString("userID") : null;
        }

        private View getItemView(int position, @NonNull ViewGroup parent){
            LayoutInflater inflater = getLayoutInflater();
            View view = inflater.inflate(R.layout.user_list_item, parent, false);

            DataBaseItem item = super.getItem(position);

            if (item != null){
                TextView tvAlias = view.findViewById(R.id.user_name);
                tvAlias.setText(item.getString("name"));
            }

            return view;
        }

        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            return getItemView(position, parent);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            return getItemView(position, parent);
        }
    }
}

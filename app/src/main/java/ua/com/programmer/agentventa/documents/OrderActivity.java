package ua.com.programmer.agentventa.documents;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import ua.com.programmer.agentventa.R;
import ua.com.programmer.agentventa.catalogs.Client;
import ua.com.programmer.agentventa.catalogs.ClientInfoActivity;
import ua.com.programmer.agentventa.catalogs.ClientsActivity;
import ua.com.programmer.agentventa.catalogs.GoodsItem;
import ua.com.programmer.agentventa.catalogs.GoodsSelectActivity;
import ua.com.programmer.agentventa.data.DataBase;
import ua.com.programmer.agentventa.data.DataConnector;
import ua.com.programmer.agentventa.settings.AppSettings;
import ua.com.programmer.agentventa.utility.Constants;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.SimplePlaceholderFragment;
import ua.com.programmer.agentventa.utility.SimpleRecycler;
import ua.com.programmer.agentventa.utility.Utils;

@Deprecated
@AndroidEntryPoint
public class OrderActivity extends AppCompatActivity implements DialogPriceTypeChoose.DialogPriceTypeChooseListener {

    @Inject AppSettings appSettings;
    @Inject DataBase db;
    @Inject Utils utils;
    private String selectedItem; //goods item GUID
    private boolean isSaved;
    private boolean isEditable;
    private Order document;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private FusedLocationProviderClient locationClient;
    private Location clientLocation;
    private boolean updatesIsActive;
    private int updatesCounter;

    private Fragment orderFragment;
    private ListAdapter listAdapterCurrent;
    private ListAdapter listAdapterPrevious;
    private SimpleRecycler categoryAdapter;
    private SpinnerAdapter restTypeSpinner;
    private SpinnerAdapter priceTypeSpinner;

    private String barcode = "";
    private boolean showContentInReverse = false;

    private final ActivityResultLauncher<Intent> openNextScreen = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
         result -> {
            Intent intent = result.getData();
            String clientID = "";
            if (intent != null) clientID = intent.getStringExtra("clientID");
            if (clientID != null && !clientID.isEmpty()) {
                setTextInView("", R.id.distance);
                document.setClientGUID(clientID);
                if (appSettings.checkOrderLocation()) {
                    clientLocation = document.getClientLocation();
                    startLocationUpdates();
                }
            }
            saveOrder(false);
            document.isSaved = false;
            showOrder();
        }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (appSettings.getClientGoodsEnabled()) {
            setContentView(R.layout.activity_order_tab_5b);
        }else{
            setContentView(R.layout.activity_order_tab);
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null){
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        Intent intent = getIntent();
        String orderGUID = intent.getStringExtra("orderGUID");
        if (orderGUID == null || orderGUID.isEmpty()) {
            document = new Order(this, "");
            document.createNew();
        }else{
            document = new Order(this,orderGUID);
        }
        clientLocation = document.getClientLocation();

        ////////////////////////////////////////////////////////////////////
        // pager setup
        ArrayList<String> pages = new ArrayList<>();
        pages.add(getResources().getString(R.string.title_attributes));
        if (appSettings.getClientGoodsEnabled()) pages.add(getResources().getString(R.string.category));
        pages.add(getResources().getString(R.string.title_content));
        pages.add(getResources().getString(R.string.title_previous));

        FragmentStateAdapter stateAdapter = new SectionsPagerAdapter(this).loadPages(pages);

        ViewPager2 pager = findViewById(R.id.container);
        pager.setAdapter(stateAdapter);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == 0) showOrder();
            }
        });

        TabLayout tabLayout = findViewById(R.id.order_tabs);
        new TabLayoutMediator(tabLayout, pager, (tab, position) -> tab.setText(pages.get(position))).attach();

        categoryAdapter = new SimpleRecycler();
        categoryAdapter.attachListener(this::onCategoryListItemClick);

        listAdapterCurrent = new ListAdapter();
        listAdapterCurrent.isEditable();
        listAdapterPrevious = new ListAdapter();

        restTypeSpinner = new SpinnerAdapter(this, R.layout.document_spinner_item);
        priceTypeSpinner = new SpinnerAdapter(this, R.layout.document_spinner_item);

        ////////////////////////////////////////////////////////////////////
        // order location
        locationCallback = new LocationCallback(){
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                showLocationProgress();
                for (Location location : locationResult.getLocations()){
                    updateCurrentLocation(location);
                }
            }
        };
        crateLocationRequest();
        if (appSettings.checkOrderLocation() && !document.hasLocation()){
            startLocationUpdates();
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

    private void showLocationProgress(){
        if (clientLocation == null) return;
        ProgressBar progressBar = findViewById(R.id.progress_bar);
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
    }

    private void hideLocationProgress(){
        ProgressBar progressBar = findViewById(R.id.progress_bar);
        if (progressBar != null) progressBar.setVisibility(View.GONE);
    }

    private void startLocationUpdates(){
        if (!appSettings.checkLocationServicePermission()){
            Toast.makeText(this, R.string.location_permission_rationale, Toast.LENGTH_LONG).show();
            return;
        }
        showLocationProgress();
        updatesCounter = 0;
        updatesIsActive = true;
        locationClient = LocationServices.getFusedLocationProviderClient(this);
        try {
            locationClient.requestLocationUpdates(locationRequest,
                    locationCallback, Looper.getMainLooper());
        }catch (SecurityException e){
            utils.log("e","Order.startLocationUpdates: "+e);
            updatesIsActive = false;
        }
    }

    private void  saveLocation(Location location){
        stopLocationUpdates();
        document.updateLocation(location);
        setTextInView(document.getDistance(), R.id.distance);
        Toast.makeText(this, R.string.title_location_updates, Toast.LENGTH_SHORT).show();
    }

    private void updateCurrentLocation(Location location){
        if (!updatesIsActive) return;

        Location noAltitudeLocation = new Location("fused");
        noAltitudeLocation.setLatitude(location.getLatitude());
        noAltitudeLocation.setLongitude(location.getLongitude());

        if (clientLocation != null) {
            double distance = clientLocation.distanceTo(noAltitudeLocation);
            setTextInView(utils.formatDistance(distance), R.id.distance);
        }else{
            setTextInView("!", R.id.distance);
        }

        if (location.getAccuracy() <= 50) {
            updatesCounter++;
            if (updatesCounter > 2) saveLocation(noAltitudeLocation);
        }else if (location.getAccuracy() > 50){
            updatesCounter++;
            if (updatesCounter > 3) {
                saveLocation(noAltitudeLocation);
            }else {
                String warn = getString(R.string.warn_low_accuracy);
                warn = warn + " ("+utils.format(location.getAccuracy(),0)+" m)";
                Toast.makeText(this, warn, Toast.LENGTH_SHORT).show();
                updatesCounter = 2;
            }
        }

    }

    private void stopLocationUpdates(){
        if (locationClient != null){
            locationClient.removeLocationUpdates(locationCallback);
        }
        hideLocationProgress();
        updatesIsActive = false;
    }

    private void setupViewElements(){
        TextView textNotes = findViewById(R.id.doc_notes);
        TextView textDeliveryDate = findViewById(R.id.doc_delivery_date);
        TextView textNextPayment = findViewById(R.id.doc_next_payment);
        TextView tvClient = findViewById(R.id.doc_client);

        if (textNotes != null) textNotes.setOnClickListener((View v) -> openNotesEditDialog());
        if (tvClient != null) {
            tvClient.setOnClickListener((View v) -> openClients());
            tvClient.setOnLongClickListener((View v) -> openClient());
        }
        if (textDeliveryDate != null) textDeliveryDate.setOnClickListener((View v) -> openDeliveryDatePickDialog());
        if (textNextPayment != null) textNextPayment.setOnClickListener((View v) -> openNextPaymentDialog());

        Spinner restType = findViewById(R.id.doc_rest_type);
        if (restTypeSpinner != null && restType != null) {
            restType.setAdapter(restTypeSpinner);
            restType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    document.restType = restTypeSpinner.getItem(i);
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {

                }
            });
            restTypeSpinner.clear();
            restTypeSpinner.addAll(db.getRestTypes());
            int position = restTypeSpinner.getPosition(document.restType);
            if (position > 0) {
                restType.setSelection(position);
            }
        }

        Spinner priceType = findViewById(R.id.doc_price_type);
        if (priceTypeSpinner != null && priceType != null) {
            priceType.setAdapter(priceTypeSpinner);
            priceType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    document.setPriceByName(priceTypeSpinner.getItem(i));
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {

                }
            });
            priceTypeSpinner.clear();
            priceTypeSpinner.addAll(db.getPriceTypesArrayS());
            int position = priceTypeSpinner.getPosition(document.getPriceTypeName());
            if (position > 0){
                priceType.setSelection(position);
            }
        }

        readPreferences();
    }

    private void readPreferences(){
        boolean flagUseDiscounts = appSettings.useDiscounts();
        boolean flagCash = appSettings.usePaymentTypeSwitch();
        boolean flagReturns = appSettings.useReturnsSwitch();
        //boolean flagPackageMark = appSettings.usePackageMark();

        LinearLayout discountElement = findViewById(R.id.element_discount);
        if (discountElement!=null){
            if (document.discount == 0) {
                discountElement.setVisibility(View.GONE);
            }else {
                discountElement.setVisibility(View.VISIBLE);
            }
        }

        LinearLayout priceTypeElement = findViewById(R.id.element_price_type);
        if (priceTypeElement != null){
            if (!flagUseDiscounts && document.price_type==0) {
                priceTypeElement.setVisibility(View.GONE);
            }else {
                priceTypeElement.setVisibility(View.VISIBLE);
            }
        }

        LinearLayout restTypeElement = findViewById(R.id.element_rest_type);
        if (restTypeElement != null){
            if (appSettings.readKeyBoolean(Constants.OPT_USE_REST_TYPE)) {
                restTypeElement.setVisibility(View.VISIBLE);
            }else{
                restTypeElement.setVisibility(View.GONE);
            }
        }

        if (!flagReturns){
            LinearLayout returnsElement = findViewById(R.id.element_returns);
            if (returnsElement!=null){
                returnsElement.setVisibility(View.GONE);
            }
        }
        if (!flagCash){
            LinearLayout cashElement = findViewById(R.id.element_cash);
            if (cashElement!=null){
                cashElement.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        //showOrder();
    }

    @Override
    protected void onPause() {
        stopLocationUpdates();
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //showOrder();
        setupViewElements();
        getMenuInflater().inflate(R.menu.menu_order, menu);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (!isSaved) {
            saveOrder(false);
        }
        finishActivity();
        super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) onBackPressed();
        if (id == R.id.update_location) startLocationUpdates();
        if (id == R.id.delete_order) showDialogDeleteOrder();
        if (id == R.id.copy_previous) copyPreviousOrder();
        if (id == R.id.edit_order) {
            if (document.isSent) showDialogEditWarning();
            setEditable(true);
        }
        if (id == R.id.print) printOrder();
        return super.onOptionsItemSelected(item);
    }

    private void printOrder() {
        if (appSettings.readKeyBoolean(AppSettings.PRINTING_ENABLED)) {
            if(document.isSent) {

                DataConnector connector = DataConnector.getInstance(this);
                connector.addListener(this::onPrintFileResult);
                connector.getDocumentPrintingForm(document.GUID);

            }else{
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.error_cannot_print)
                        .setTitle(R.string.warning)
                        .setPositiveButton(R.string.yes, null);
                builder.create().show();
            }
        }else{
            Toast.makeText(this, R.string.error_printing_not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private void onPrintFileResult(DataBaseItem result) {

        String errorCode = result.getString(Constants.DATA_ERROR_CODE);

        if (errorCode.isEmpty()) {

            String filePath = document.GUID+".pdf";
            File file = new File(getCacheDir(), filePath);

            MimeTypeMap mime = MimeTypeMap.getSingleton();
            String type = mime.getMimeTypeFromExtension("pdf");
            Uri uri = FileProvider.getUriForFile(
                    getApplicationContext(),
                    "ua.com.programmer.agentventa",
                    file);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, type);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);

        }else if (!this.isFinishing()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(getString(R.string.data_xml_error))
                    .setTitle(R.string.warning)
                    .setPositiveButton(R.string.yes, null);
            builder.create().show();
        }

    }

    private void setTextInView(String str, int id) {
        if (orderFragment != null){
            View view = orderFragment.getView();
            if (view != null){
                TextView textView = view.findViewById(id);
                if (textView!=null) textView.setText(str);
            }
        }
    }

    private void showOrder(){
        isSaved = document.isSaved;
        isEditable = !document.isProcessed && !document.isSent;

        //readPreferences();

        String textDiscount = utils.format(document.discount,1);
        textDiscount = textDiscount.replace(".0","");
        if (!textDiscount.equals("0")) {
            textDiscount = textDiscount + "% (" + document.discountValue.trim() + ")";
        }else{
            textDiscount = "-";
        }

        setTextInView(document.date,R.id.doc_date);
        setTextInView(document.number,R.id.doc_number);
        setTextInView(document.deliveryDate,R.id.doc_delivery_date);
        setTextInView(document.priceTotal,R.id.doc_total_price);
        setTextInView(document.nextPayment,R.id.doc_next_payment);
        setTextInView(document.notes,R.id.doc_notes);

        String quantity = document.quantityTotal;
        double weight = document.getWeight();
        if (weight > 0) quantity = quantity+" ("+utils.formatWeight(weight)+")";
        setTextInView(quantity,R.id.doc_total_quantity);

        setTextInView(textDiscount,R.id.doc_discount);
        //setTextInView(db.priceTypeName(""+document.price_type),R.id.doc_price_type);

        SwitchCompat swCash = findViewById(R.id.doc_iscash);
        if (swCash!=null) swCash.setChecked(document.isCash);

        SwitchCompat swReturn = findViewById(R.id.doc_isreturn);
        if (swReturn!=null) swReturn.setChecked(document.isReturn);

        if (appSettings.checkOrderLocation()) {
            if (document.hasLocation()) {
                setTextInView(document.getDistance(), R.id.distance);
                hideLocationProgress();
            }else{
                showLocationProgress();
            }
        }else if (document.hasLocation()) {
            setTextInView(document.getDistance(), R.id.distance);
        }else{
            setTextInView("", R.id.distance);
        }

        setEditable(isEditable);
        updateList();
    }

    private void updateList(){
        listAdapterCurrent.loadListItems(db.getOrderContent(document.id));
        listAdapterPrevious.loadListItems(db.getOrderPrevious(document.client_guid,document.id));

        ArrayList<DataBaseItem> categories = db.getClientsGoodsFilter(document.client_guid, "category");
        if (categories.size() > 0) {
            ArrayList<DataBaseItem> list = new ArrayList<>();
            for (DataBaseItem category : categories) {
                DataBaseItem listItem = new DataBaseItem();
                listItem.put("element_1", category.getString("category"));
                listItem.put("element_2", category.getString("item_count"));
                list.add(listItem);
            }
            categoryAdapter.loadListItems(list);
        }else{
            categoryAdapter.clearList();
        }
    }

    private void setEditable (boolean flag) {
        isEditable = flag;

        SwitchCompat swCash = findViewById(R.id.doc_iscash);
        if (swCash!=null) swCash.setClickable(isEditable);
        SwitchCompat swReturn = findViewById(R.id.doc_isreturn);
        if (swReturn!=null) swReturn.setClickable(isEditable);

        setTextInView(document.client,R.id.doc_client);

        Toolbar bottomBar = findViewById(R.id.order_bottombar);

        if (isEditable) {
            document.isProcessed = false;
            document.isSent = false;
            isSaved = false;
            setupBottomBar();
            bottomBar.setVisibility(View.VISIBLE);
        }else {
            bottomBar.setVisibility(View.GONE);
        }

        //document status icon
        ImageView iv = findViewById(R.id.doc_icon);
        if (iv!=null){
            if (document.isSent) {
                iv.setImageResource(R.drawable.baseline_cloud_done_24);
            }else if (document.isProcessed) {
                iv.setImageResource(R.drawable.baseline_cloud_upload_24);
            }else{
                iv.setImageResource(R.drawable.baseline_cloud_queue_24);
            }
        }

    }

    private void updateDocumentAttributes () {
        SwitchCompat swCash = findViewById(R.id.doc_iscash);
        if (swCash != null) document.isCash = swCash.isChecked();
        SwitchCompat swReturn = findViewById(R.id.doc_isreturn);
        if (swReturn != null) document.isReturn = swReturn.isChecked();
        document.updateTotals();
    }

    /**
     * Saving current document. If "processFlag" is "true", performing additional checks.
     *
     * @param processFlag enables additional checks
     * @return true - if saving was successful
     */
    private boolean saveOrder(boolean processFlag) {
        updateDocumentAttributes();

        Client client = new Client(this,document.client_guid);
        if (client.isBanned){
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(client.getBanMessage())
                    .setTitle(R.string.title_client_is_banned)
                    .setPositiveButton(R.string.yes, null);
            builder.create().show();
            return false;
        }

        if (processFlag){
            if (appSettings.getRequireDeliveryDate() && document.deliveryDate.isEmpty()){
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.error_no_delivery_date)
                        .setTitle(R.string.title_value_check)
                        .setPositiveButton(R.string.yes, null);
                builder.create().show();
                return false;
            }
            if (appSettings.checkOrderLocation() && !document.hasLocation()){
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.error_no_location_order)
                        .setTitle(R.string.title_value_check)
                        .setPositiveButton(R.string.yes, null);
                builder.create().show();
                return false;
            }
        }
        return document.save(processFlag);
    }

    private boolean saveOrderForEdit() {
        updateDocumentAttributes();
        return document.save(false);
    }

    private void deleteOrder () {
        document.delete();
    }

    private boolean checkClient(){
        if (document.client_guid.isEmpty()){
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.error_no_client_description)
                    .setTitle(R.string.error_no_client)
                    .setPositiveButton(R.string.yes, (DialogInterface dialog, int which) -> openClients())
                    .setNegativeButton(R.string.cancel,null);
            builder.create().show();
            return false;
        }
        return true;
    }

    private void openGoodsList (boolean clientsGoodsOnly, String categoryFilter){
        if (checkClient()){

            if (saveOrderForEdit()) {
                Intent intent = new Intent(this, GoodsSelectActivity.class);
                intent.putExtra("orderGUID", document.GUID);
                intent.putExtra("clientGoodsOnly", clientsGoodsOnly);
                intent.putExtra("categoryFilter", categoryFilter);
                openNextScreen.launch(intent);
            }else{
                showWarningDataNotSaved();
            }

        }
    }

    private void onCategoryListItemClick(DataBaseItem itemData){
        openGoodsList( true, itemData.getString("element_1"));
    }

    private void openGoodsList (View view) {
        openGoodsList(false, "");
    }

    private void openClientGoodsList (View view) {
        openGoodsList(true, "");
    }

    private void openClients () {
        if (!isEditable) return;
        if (saveOrderForEdit()) {
            Intent intent = new Intent(this, ClientsActivity.class);
            intent.putExtra("orderGUID", document.GUID);
            openNextScreen.launch(intent);
        }else{
            showWarningDataNotSaved();
        }
    }

    private boolean openClient (){
        if (!document.client_guid.equals("")){
            Intent intent = new Intent(this, ClientInfoActivity.class);
            intent.putExtra("clientGUID", document.client_guid);
            startActivity(intent);
        }
        return true;
    }

    private void openDeliveryDatePickDialog(){
        if (!isEditable) return;
        updateDocumentAttributes();
        final Calendar calendar = new GregorianCalendar();
        int Y = calendar.get(Calendar.YEAR);
        int M = calendar.get(Calendar.MONTH);
        int D = calendar.get(Calendar.DATE);
        AlertDialog dialog = new DatePickerDialog(this, (DatePicker view, int year, int month, int dayOfMonth) -> {
                Calendar cal = new GregorianCalendar();
                cal.set(year,month,dayOfMonth,0,0);
                document.deliveryDate = String.format(Locale.getDefault(),"%1$td-%1$tm-%1$tY",cal.getTime());
                showOrder();
        },Y,M,D);
        dialog.show();
    }

    @Override
    public void onPriceTypeChoose(int priceType) {
        document.price_type = priceType;
        db.recalcOrderContentDiscount(document.id,document.discount,priceType);
        updateDocumentAttributes();
        showOrder();
    }

    private void finishActivity () {
        Intent intent = new Intent();
        intent.putExtra("orderGUID", document.GUID);
        setResult(RESULT_OK, intent);
        finish();
    }

    private void showDialogDeleteOrder(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.delete_data)
                .setTitle(R.string.delete_order)
                .setPositiveButton(R.string.yes, (DialogInterface dialogInterface, int i) ->{
                    deleteOrder();
                    finishActivity();
                })
                .setNegativeButton(R.string.no,null);
        builder.create().show();
    }

    private void showDialogEditWarning(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.data_already_sent)
                .setTitle(R.string.warning)
                .setPositiveButton(R.string.yes, null);
        builder.create().show();
    }

    private void showWarningDataNotSaved(){
        Snackbar snackbar = Snackbar.make(findViewById(R.id.main_content),R.string.data_not_saved,Snackbar.LENGTH_SHORT);
        snackbar.setAction(R.string.OK, null);
        snackbar.show();
    }

    private void openNotesEditDialog () {
        if (!isEditable) {
            openNotesDialog();
            return;
        }
        updateDocumentAttributes();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.dialog_edit_text,null);

        final EditText editText = view.findViewById(R.id.edit_text);
        editText.setText(document.notes);

        builder.setView(view);
        builder.setMessage("")
                .setTitle(getResources().getString(R.string.doc_notes))
                .setPositiveButton(getResources().getString(R.string.save), (DialogInterface dialogInterface, int i) ->
                {
                    document.notes = editText.getText().toString();
                    document.save(false);
                    document.isSaved = false; //to keep activity in editable state
                    showOrder();
                })
                .setNegativeButton(getResources().getString(R.string.cancel),null);
        final AlertDialog dialog = builder.create();
        Window window = dialog.getWindow();
        if (window != null){
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        dialog.show();
    }

    private void openNotesDialog () {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(document.notes)
                .setTitle(getResources().getString(R.string.doc_notes))
                .setPositiveButton(getResources().getString(R.string.OK), null);
        final AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void openItemEditDialog () {
        Intent intent = new Intent(this,OrderItemEdit.class);
        intent.putExtra("orderGUID", document.GUID);
        intent.putExtra("itemGUID", selectedItem);
        openNextScreen.launch(intent);
    }

    private void openNextPaymentDialog() {
        if (!isEditable) return;
        updateDocumentAttributes();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.dialog_edit_sum,null);

        final EditText editText = view.findViewById(R.id.edit_text);
        double paymentValue = utils.round(document.nextPayment,2);
        if (paymentValue != 0){
            editText.setText(utils.format(paymentValue,2));
        }

        builder.setView(view);
        builder.setMessage("")
                .setTitle(getResources().getString(R.string.doc_next_payment))
                .setPositiveButton(getResources().getString(R.string.save), (DialogInterface dialogInterface, int i) -> {
                    document.nextPayment = utils.format(utils.round(editText.getText().toString(), 2),2);
                    showOrder();
                })
                .setNegativeButton(getResources().getString(R.string.cancel), null);
        final AlertDialog dialog = builder.create();
        Window window = dialog.getWindow();
        if (window != null){
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        dialog.show();

        editText.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) ->
        {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT){
                document.nextPayment = utils.format(utils.round(editText.getText().toString(), 2),2);
                showOrder();
                dialog.dismiss();
                return true;
            }
            return false;
        });
    }

    public static class PlaceholderFragment extends Fragment {

        private final String LAYOUT_ID = "layoutID";

        private ListAdapter listAdapter;
        private RecyclerView recyclerView;
        private int layoutID;

        public PlaceholderFragment() {}

        void setListAdapter(ListAdapter adapter){
            listAdapter = adapter;
            if (recyclerView != null) recyclerView.setAdapter(listAdapter);
        }

        void setViewID(int layoutID){
            this.layoutID = layoutID;
        }

        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            if (savedInstanceState != null){
                layoutID = savedInstanceState.getInt(LAYOUT_ID);
            }
            return inflater.inflate(layoutID, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            recyclerView = view.findViewById(R.id.goods_list);
            if (recyclerView != null && listAdapter != null){
                recyclerView.setHasFixedSize(true);
                LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
                recyclerView.setLayoutManager(layoutManager);
                recyclerView.setAdapter(listAdapter);
            }
        }

        @Override
        public void onSaveInstanceState(@NonNull Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putInt(LAYOUT_ID, layoutID);
        }
    }

    private class SectionsPagerAdapter extends FragmentStateAdapter {

        private ArrayList<String> pages = new ArrayList<>();

        SectionsPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        SectionsPagerAdapter loadPages(ArrayList<String> newPages){
            pages = newPages;
            return this;
        }

        private Fragment page1fragment(){
            PlaceholderFragment fragment = new PlaceholderFragment();
            fragment.setViewID(R.layout.content_order);
            orderFragment = fragment;
            return fragment;
        }

        private Fragment page2fragment(){
            if (pages.size() == 4) {
                SimplePlaceholderFragment fragment = new SimplePlaceholderFragment();
                fragment.setAdapter(categoryAdapter);
                return fragment;
            }else{
                PlaceholderFragment fragment = new PlaceholderFragment();
                fragment.setViewID(R.layout.content_order_goods);
                fragment.setListAdapter(listAdapterCurrent);
                return fragment;
            }
        }

        private Fragment page3fragment(){
            if (pages.size() == 4) {
                PlaceholderFragment fragment = new PlaceholderFragment();
                fragment.setViewID(R.layout.content_order_goods);
                fragment.setListAdapter(listAdapterCurrent);
                return fragment;
            }else{
                PlaceholderFragment fragment = new PlaceholderFragment();
                fragment.setViewID(R.layout.content_order_goods);
                fragment.setListAdapter(listAdapterPrevious);
                return fragment;
            }
        }

        private Fragment page4fragment(){
            PlaceholderFragment fragment = new PlaceholderFragment();
            fragment.setViewID(R.layout.content_order_goods);
            fragment.setListAdapter(listAdapterPrevious);
            return fragment;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {

            if (position == 0) return page1fragment();
            if (position == 1) return page2fragment();
            if (position == 2) return page3fragment();
            if (position == 3) return page4fragment();

            return new Fragment();
        }

        @Override
        public int getItemCount() {
            return pages.size();
        }

    }

    private void setupBottomBar() {

        TextView tvClient = findViewById(R.id.menu_select_client);
        tvClient.setOnClickListener((View v) -> openClients());

        TextView tvGoods = findViewById(R.id.menu_select_goods);
        tvGoods.setOnClickListener(this::openGoodsList);

        TextView tvClientGoods = findViewById(R.id.menu_select_client_goods);
        if (tvClientGoods != null) {
            tvClientGoods.setOnClickListener(this::openClientGoodsList);
        }

        TextView tvNotes = findViewById(R.id.menu_edit_notes);
        tvNotes.setOnClickListener((View v) -> openNotesEditDialog());

        TextView tvSave = findViewById(R.id.menu_save);
        tvSave.setOnClickListener((View v) -> {
                isSaved = saveOrder(true);
                if (isSaved) {
                    setEditable(false);
                    Snackbar snackbar = Snackbar.make(findViewById(R.id.main_content),R.string.data_saved,Snackbar.LENGTH_LONG);
                    snackbar.setAction(R.string.close, (View view) -> finishActivity());
                    snackbar.show();
                }else{
                    showWarningDataNotSaved();
                }
        });

    }

    private void copyPreviousOrder(){
        if (isSaved) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.enable_edit)
                    .setTitle(R.string.warning)
                    .setPositiveButton(R.string.OK, null);
            final AlertDialog dialog = builder.create();
            dialog.show();
        }else if (document.client_guid.equals("")){
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.error_no_client)
                    .setTitle(R.string.warning)
                    .setPositiveButton(R.string.OK, null);
            final AlertDialog dialog = builder.create();
            dialog.show();
        }else if (utils.round(document.quantityTotal,3)!=0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.warn_order_content)
                    .setTitle(R.string.warning)
                    .setPositiveButton(R.string.continue_edit, (DialogInterface dialogInterface, int i) ->
                            copyPreviousOrderContent())
                    .setNegativeButton(R.string.cancel, null);
            final AlertDialog dialog = builder.create();
            dialog.show();
        }else{
            copyPreviousOrderContent();
        }
    }

    private void copyPreviousOrderContent(){
        double discount = new Client(this,document.client_guid).discount;
        ArrayList<DataBaseItem> content = db.getOrderPrevious(document.client_guid,document.id);
        if (content.size() == 0){
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.no_previous_document)
                    .setTitle(R.string.warning)
                    .setPositiveButton(R.string.OK, null);
            final AlertDialog dialog = builder.create();
            dialog.show();
        }
        for (DataBaseItem item: content){
            boolean isPacked = item.getInt("is_packed") == 1;

            DataBaseItem props = new DataBaseItem(Constants.DATA_GOODS_ITEM);
            props.put("orderID",document.id);
            props.put("itemID",item.getString("item_guid"));
            props.put("quantity",item.getDouble("quantity"));
            props.put("unit",item.getString("unit_code"));
            props.put("price",item.getDouble("price"));
            props.put("isPacked",isPacked);
            props.put("discount",discount);
            props.put("isDemand",item.getInt("is_demand"));

            db.setGoodsItemInOrder(props);
        }
        updateDocumentAttributes();
        showOrder();
    }

    private boolean onListItemLongClick(DataBaseItem item){
        if (isSaved) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.enable_edit)
                    .setTitle(R.string.warning)
                    .setPositiveButton(R.string.OK, null);
            final AlertDialog dialog = builder.create();
            dialog.show();
        }else {
            selectedItem = item.getString("item_guid");
            openItemEditDialog();
        }
        return true;
    }

    private class ListAdapter extends RecyclerView.Adapter<OrderActivity.ListViewHolder>{

        private final ArrayList<DataBaseItem> list = new ArrayList<>();
        private boolean isEditable = false;

        void isEditable() {isEditable = true;}

        @NonNull
        @Override
        public OrderActivity.ListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.order_content_line,parent,false);
            return new OrderActivity.ListViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull OrderActivity.ListViewHolder holder, int position) {
            DataBaseItem item = list.get(position);
            holder.setValues(item, isEditable);
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        @SuppressLint("NotifyDataSetChanged")
        void loadListItems(ArrayList<DataBaseItem> items){
            list.clear();
            if (showContentInReverse) {
                for (int i = items.size() - 1; i >= 0; i--) {
                    DataBaseItem item = items.get(i);
                    list.add(item);
                }
            }else{
                list.addAll(items);
            }
            notifyDataSetChanged();
        }
    }

    private class ListViewHolder extends RecyclerView.ViewHolder{

        private final View holderView;

        ListViewHolder(@NonNull View itemView) {
            super(itemView);
            holderView = itemView;
        }

        void setText(int viewId, String text){
            TextView textView = holderView.findViewById(viewId);
            if (textView != null) textView.setText(text);
        }

        void setValues(DataBaseItem item, boolean isEditable){

            setText(R.id.item_code, item.getString("code1"));
            setText(R.id.item_name, item.getString("description"));
            setText(R.id.item_price, utils.format(item.getDouble("price"),2));
            setText(R.id.item_quantity, utils.formatAsInteger(item.getDouble("quantity"),3));
            setText(R.id.item_sum, utils.format(item.getDouble("sum"),2));
            setText(R.id.item_group, item.getString("groupName"));

            ImageView packedIcon = holderView.findViewById(R.id.item_is_packed);
            if (item.getInt("is_packed") == 1 || item.getInt("is_demand") == 1) {
                packedIcon.setVisibility(View.VISIBLE);
            }else {
                packedIcon.setVisibility(View.INVISIBLE);
            }

            if (isEditable) holderView.setOnLongClickListener((View v) -> onListItemLongClick(item));
        }
    }

    private class SpinnerAdapter extends ArrayAdapter<String>{

        private final int layout;

        public SpinnerAdapter(@NonNull Context context, int resource) {
            super(context, resource);
            layout = resource;
        }

        private View getItemView(int position, @NonNull ViewGroup parent){
            LayoutInflater inflater = getLayoutInflater();
            View view = inflater.inflate(layout, parent, false);

            String item = super.getItem(position);

            if (item != null){
                TextView textView1 = view.findViewById(R.id.text);
                textView1.setText(item);
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

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP || !isEditable){
            return super.dispatchKeyEvent(event);
        }
        int keyCode = event.getKeyCode();
        //utils.debug("KEY: "+keyCode+"; "+barcode);
        if (keyCode == KeyEvent.KEYCODE_BACK){
            onBackPressed();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_TAB) {
            onBarcodeReceived();
        }else{
            char key = (char) event.getUnicodeChar();
            if (Character.isDigit(key) || Character.isLetter(key)) {
                barcode += key;
            }else{
                barcode = "";
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void onBarcodeReceived() {
        if (barcode.isEmpty()) return;

        String guid = db.findGoodsItemByBarcode(barcode);
        String errorMessage = "barcode not found: ["+barcode+"]; length: "+barcode.length();
        barcode = ""; // reset temporary value

        if (guid.isEmpty()) {
            utils.debug(errorMessage);
            Toast.makeText(this, R.string.error_product_not_found, Toast.LENGTH_SHORT).show();
            return;
        }

        GoodsItem item = GoodsItem.getInstance(this);
        item.initialize(guid, document.id);

        double quantity = 1.0 + item.getOrderQuantity("");

        DataBaseItem props = new DataBaseItem(Constants.DATA_GOODS_ITEM);
        props.put("itemID",item.GUID);
        props.put("quantity",quantity);
        props.put("unit",item.getUnit(""));
        props.put("price",item.getPricePerUnit(""+document.price_type,""));
        props.put("isPacked",item.isPacked);
        props.put("isDemand",item.isDemand);

        document.setGoodsItemProperties(props);

        showContentInReverse = true;
        saveOrder(false);
        document.isSaved = false;
        showOrder();

    }
}

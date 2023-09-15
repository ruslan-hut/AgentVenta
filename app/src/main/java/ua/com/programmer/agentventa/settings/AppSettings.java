package ua.com.programmer.agentventa.settings;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Base64;

import androidx.core.app.ActivityCompat;

import com.bumptech.glide.load.model.LazyHeaders;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import ua.com.programmer.agentventa.R;
import ua.com.programmer.agentventa.license.LicenseManager;
import ua.com.programmer.agentventa.utility.Constants;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.Utils;

@Deprecated
public class AppSettings {

    /**
     * options keys, used in 'loadOptions', 'readKeyBoolean', etc.
     */
    public static final String USE_DEMANDS = "useDemands";
    public static final String USE_PACKAGE_MARK = "usePackageMark";
    public static final String EDIT_CLIENT_LOCATION = "editLocations";
    public static final String PRINTING_ENABLED = "printingEnabled";

    private static SharedPreferences sharedPreferences;
    private static final Utils utils = new Utils();

    private static String currency = "";
    private static String connectionName = "";
    private static String connectionGUID = "";
    private static String userID = "";
    private static String licenseKey = "";
    private static boolean syncIsActive = false;
    private static boolean allowCreateCashDocuments = false;
    private static boolean extendedUserID = false;
    private static boolean sendDebugLog = false;
    private static boolean eraseData = false;
    private static long currentDate = -1;
    private static String fragmentTag = "";
    private static ArrayList<DataBaseItem> locationsWatchList;

    private static DataBaseItem appOptions = new DataBaseItem();
    private static boolean unlimitedKey;

    private static String MAPS_API_KEY;
    private static int PERMISSION_LOCATION_STATE;
    private static String DEFAULT_CURRENCY = "";

    private static AppSettings appSettings;

    /**
     * Class constructor, initialisation of important variables
     */
    private AppSettings(){
        getUserID();
        getLicenseKey();
        getCurrencyName();
    }

    public static AppSettings getInstance(Context context){
        if (sharedPreferences == null){
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        }
        if (appSettings == null){
            appSettings = new AppSettings();
            PERMISSION_LOCATION_STATE = ActivityCompat.checkSelfPermission(context,
                    Manifest.permission.ACCESS_FINE_LOCATION);
            DEFAULT_CURRENCY = context.getString(R.string.currency_default_value);
        }
        if (MAPS_API_KEY == null){
            try {
                ApplicationInfo applicationInfo = context.getPackageManager()
                        .getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
                Bundle bundle = applicationInfo.metaData;
                MAPS_API_KEY = bundle.getString("com.google.android.geo.API_KEY");
            }catch (Exception ex){
                utils.debug("applicationInfo.metaData: "+ex);
            }
        }
        return appSettings;
    }

    public boolean demoMode(){
        String server = getServerName();
        String base = getDatabaseName();
        if (server == null || base == null) return true;
        return server.equals("demo") && base.equals("demo");
    }

    public boolean eraseData(){
        return eraseData;
    }

    public String getUserID(){
        if (userID.equals("")){
            userID = sharedPreferences.getString("userID","");
            extendedUserID = sharedPreferences.getBoolean("extended_id",false);
        }
        if (userID == null){
            userID = "";
        }
        if (userID.equals("")){
            userID = UUID.randomUUID().toString();
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("userID",userID);
            editor.apply();
        }
        if (userID.length() < 8) {
            userID = "00000000";
        }
        if (extendedUserID) return userID+"Z"+getConnectionGUID();
        else return userID;
    }

    public boolean keyIsActive(){
        return sharedPreferences.getBoolean("key_is_active",false);
    }

    public void setKeyIsActive(boolean flag){
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("key_is_active", flag);
        editor.apply();
    }

    public String getMapsApiKey(){
        return MAPS_API_KEY;
    }

    public void setSyncIsActive(boolean isActive){
        syncIsActive = isActive;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(Constants.ERROR_SYNC_IS_ACTIVE, isActive);
        editor.apply();
    }

    public boolean getSyncIsActive(){
        syncIsActive = sharedPreferences.getBoolean(Constants.ERROR_SYNC_IS_ACTIVE,false);
        return syncIsActive;
    }

    public void setAllowPriceTypeChoose(boolean isAllowed){
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("ALLOW_PRICE_TYPE_CHOOSE", isAllowed);
        editor.apply();
    }

    public boolean allowPriceTypeChoose(){
        return sharedPreferences.getBoolean("ALLOW_PRICE_TYPE_CHOOSE",false); }

    public void setShowClientPriceOnly(boolean flag){
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("SHOW_CLIENT_PRICE_ONLY", flag);
        editor.apply();
    }

    public boolean showAllPrices(){
        return !sharedPreferences.getBoolean("SHOW_CLIENT_PRICE_ONLY",false); }

    public void setClientPriceByDefault(boolean flag){
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("SET_CLIENT_PRICE", flag);
        editor.apply();
    }

    public boolean checkLocationServicePermission(){
        return PERMISSION_LOCATION_STATE == PackageManager.PERMISSION_GRANTED;
    }

    public void setLocationServiceEnabled(boolean enabled){
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("ENABLE_LOCATION_SERVICE", enabled);
        editor.apply();
    }

    public boolean getLocationServiceEnabled(){
        return sharedPreferences.getBoolean("ENABLE_LOCATION_SERVICE",false);
    }

    public boolean getClientsDirections(){
        return sharedPreferences.getBoolean("clients_directions",false);
    }

    public void setClientsDirections(boolean enabled){
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("clients_directions", enabled);
        editor.apply();
    }

    private void setClientsGoods(boolean enabled){
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("clients_goods", enabled);
        editor.apply();
    }

    public boolean getClientGoodsEnabled(){
        return sharedPreferences.getBoolean("clients_goods",false);
    }

    public void setLastTimeSentLocation(long time){
        long lastSavedTime = getLastTimeSentLocation();
        if (time > lastSavedTime){
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putLong("LAST_TIME_SENT_LOCATION", time);
            editor.apply();
        }
    }

    public long getLastTimeSentLocation(){
        return sharedPreferences.getLong("LAST_TIME_SENT_LOCATION",0);
    }

    public long getDirectionsRequestsCounter(){
        return sharedPreferences.getLong("DIRECTIONS_REQUESTS_COUNTER",0);
    }

    public void setDirectionsRequestsCounter(long counter){
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong("DIRECTIONS_REQUESTS_COUNTER", counter);
        editor.apply();
    }

    public boolean disableDirectionsAPI(){
        if (unlimitedKey) {
            return false;
        }else{
            return getDirectionsRequestsCounter() > Constants.DIRECTIONS_REQUESTS_DAILY_LIMIT;
        }
    }

    public void setAllowCreateCashDocuments(boolean isAllowed){
        allowCreateCashDocuments = isAllowed;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("ALLOW_CREATE_CASH_DOCUMENTS", isAllowed);
        editor.apply();
    }

    public long getCurrentDate(){
        if (currentDate < 0) currentDate = utils.dateBeginOfToday();
        return currentDate;
    }

    public void saveCurrentDate(long date){
        currentDate = date;
    }

    public String getFragmentTag(){
        return fragmentTag;
    }

    public void saveFragmentTag(String tag){
        fragmentTag = tag;
    }

    public boolean getAllowCreateCashDocuments(){
        allowCreateCashDocuments = sharedPreferences.getBoolean("ALLOW_CREATE_CASH_DOCUMENTS",false);
        return allowCreateCashDocuments;}

    public void setOfflineMode(boolean isOffline){
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("OFFLINE_MODE", isOffline);
        editor.apply();
    }

    public boolean offlineMode(){
        return sharedPreferences.getBoolean("OFFLINE_MODE", false);
    }

    public boolean notifyAboutLocationsUpdates(){
        return sharedPreferences.getBoolean("notifications_location_update",false);
    }

    public boolean useDiscounts(){
        return sharedPreferences.getBoolean("use_discounts", false);
    }

    public boolean usePaymentTypeSwitch(){
        return sharedPreferences.getBoolean("use_payment_type_switch", false);
    }

    public boolean useReturnsSwitch(){
        return sharedPreferences.getBoolean("use_returns_switch", false);
    }

    public boolean openEditor(){
        return sharedPreferences.getBoolean("open_editor_switch", true);
    }

    public boolean highlightGoodsInOrder(){
        return sharedPreferences.getBoolean("highlight_goods_in_order", false);
    }

    public boolean sortGoodsByName(){
        return sharedPreferences.getBoolean("sort_goods_by_name", false);
    }

    public void setSortGoodsByName(boolean flag){
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("sort_goods_by_name", flag);
        editor.apply();
    }

    public boolean showGoodsWithRests() {
        return sharedPreferences.getBoolean("show_rests_only", false);
    }

    public boolean useBriefEditorScreen(){
        return sharedPreferences.getBoolean("item_editor_short_screen",false);
    }

    public void setShowGoodsWithRestsFlag(boolean flag){
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("show_rests_only", flag);
        editor.apply();
    }

    public int getDefaultPriceType(){
        return sharedPreferences.getInt("default_price_type", 0);
    }

    public void setDefaultPriceType(int priceType){
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("default_price_type", priceType);
        editor.apply();
    }

    public String getServerName() {
        return sharedPreferences.getString("server_address", "demo");
    }

    public String getServerPort() {
        String defaultPort = "80";
        String syncFormat = getSyncFormat();
        if (syncFormat != null && syncFormat.equals(Constants.SYNC_FORMAT_FTP)) {
            defaultPort = "21";
        }
        String savedValue = sharedPreferences.getString("server_port", defaultPort);
        if (savedValue == null || savedValue.isEmpty() || savedValue.equals("0")){
            savedValue = defaultPort;
        }
        return savedValue;
    }

    public String getBaseUrl(){
        String server = getServerName();
        String database = getDatabaseName();
        if (demoMode()) return "http://hoot.com.ua/simple/";
        String url;
        if (server.contains("https://") || server.contains("http://")) {
            url = server;
        }else {
            url = "http://"+server;
        }
        if (!url.endsWith("/")) url = url + "/";
        if (!database.isEmpty()) url = url + database+"/";
        return url;
    }

    public String getBaseImageUrl(){
        return getBaseUrl()+"hs/dex/image/";
    }

    public LazyHeaders getAuthHeaders(){
        String namePass = getUserName()+":"+getUserPassword();
        String authToken = "Basic "+ Base64.encodeToString(namePass.getBytes(), Base64.NO_WRAP);
        return new LazyHeaders.Builder()
                .addHeader("Authorization",authToken)
                .build();
    }

    public String getSyncFormat() {
        return sharedPreferences.getString("sync_format", Constants.SYNC_FORMAT_HTTP);
    }

    public String getDatabaseName() {
        return sharedPreferences.getString("database_name", null);
    }

    public String getUserName() {
        if (demoMode()) return "Агент";
        return sharedPreferences.getString("user_name", "anonymous");
    }

    public String getUserPassword() {
        if (demoMode()) return "112233";
        return sharedPreferences.getString("user_password", "");
    }

    public String getMessageServiceToken(){
        final String token = sharedPreferences.getString("FCM_TOKEN", null);

        if (token == null){
            FirebaseMessaging.getInstance().getToken()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()){
                            String newToken = task.getResult();
                            if (newToken != null) setMessageServiceToken(newToken);
                        } else {
                            utils.log("w", "getToken failed, "+task.getException());
                        }
                    });
        }

        return token;
    }

    public void setMessageServiceToken(String token){
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("FCM_TOKEN", token);
        editor.apply();
    }

    public String getCurrencyName() {
        if (currency == null || currency.isEmpty()){
            currency = sharedPreferences.getString("currency_name", DEFAULT_CURRENCY);
        }
        return currency;
    }

    public void setCurrencyName(String currencyName){
        if (!currency.equals(currencyName)){
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("currency_name", currencyName);
            editor.apply();
            currency = currencyName;
        }
    }

    public boolean checkOrderLocation(){
        return sharedPreferences.getBoolean("check_order_location",false);
    }

    public void setCheckOrderLocation(boolean flag){
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("check_order_location", flag);
        editor.apply();
    }

    public boolean getRequireDeliveryDate(){
        return sharedPreferences.getBoolean("require_delivery_date",false);
    }

    public void setRequireDeliveryDate(boolean flag){
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("require_delivery_date", flag);
        editor.apply();
    }

    public double getDefaultItemQuantity() {
        String value = sharedPreferences.getString("default_item_quantity","");
        return utils.round(value,3);
    }

    public long getLastLoginTime() {
        return sharedPreferences.getLong("last_login_time",0);
    }

    public void setLastLoginTime(long time) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong("last_login_time", time);
        editor.apply();
    }

    public void setLoadImages(boolean flag){
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("load_images", flag);
        editor.apply();
    }

    public boolean loadImages(){
        if (!keyIsActive()) return false;
        return sharedPreferences.getBoolean("load_images", false);
    }

    /**
     * Get saved list of users for locations history activity. If method is called
     * for the first time, it will load a list from saved option string.
     *
     * @return array list
     */
    public ArrayList<DataBaseItem> getLocationsWatchList(){
        String option = sharedPreferences.getString("watchList","");
        if (locationsWatchList == null) locationsWatchList = new ArrayList<>();
        locationsWatchList.clear();
        if (option == null || option.isEmpty()) return locationsWatchList;
        try {
            JSONArray array = new JSONArray(option);
            for (int i=0; i<array.length(); i++){
                locationsWatchList.add(new DataBaseItem(array.getJSONObject(i)));
            }
        }catch (Exception e){
            utils.debug("read watchList: "+e);
        }
        return locationsWatchList;
    }

    /**
     * Save option string with users list to the shared preferences.
     *
     * @param option string to save
     */
    private void setLocationsWatchList(String option){
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("watchList", option);
        editor.apply();
    }

    public String getConnectionName() {
        if (connectionName.equals("")){
            String defaultValue = getDatabaseName();
            if (defaultValue == null) defaultValue="";
            connectionName = sharedPreferences.getString("connection_name", defaultValue);
        }
        return connectionName;
    }

    public String getConnectionGUID() {
        if (connectionGUID.equals("")){
            connectionGUID = sharedPreferences.getString("connection_guid", "");
        }
        return connectionGUID;
    }

    public String getLicenseKey() {
        if (licenseKey.isEmpty())
            licenseKey = sharedPreferences.getString("license_key", "");
        return licenseKey;
    }

    public void saveLicenseKey(String newLicenseKey){
        licenseKey = newLicenseKey;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("license_key", newLicenseKey);
        editor.apply();
        checkLicenseKey();
    }

    /**
     * Setting current connection parameters, all available options, load and check license key
     *
     * @param parameters data set of parameters
     */
    public void setConnectionParameters(DataBaseItem parameters){

        extendedUserID = parameters.getInt("extended_id")==1;
        connectionGUID = parameters.getString("guid");
        connectionName = parameters.getString("description");

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("connection_guid", connectionGUID);
        editor.putString("connection_name", connectionName);
        editor.putString("sync_format", parameters.getString("data_format"));
        editor.putString("server_address", parameters.getString("db_server"));
        editor.putString("database_name", parameters.getString("db_name"));
        editor.putString("user_name", parameters.getString("db_user"));
        editor.putString("user_password", parameters.getString("db_password"));
        editor.putBoolean("extended_id", extendedUserID);
        editor.apply();

        String optionsData = parameters.getString("options");
        if (!optionsData.isEmpty()) loadOptions(optionsData);

        writeUserData();
    }

    public void writeUserData(){

        String baseName = getDatabaseName();
        String serverAddress = getServerName();

        //do not track demo versions; google bots
        if (baseName == null || serverAddress == null) return;
        if (baseName.equals("demo") && serverAddress.equals("demo")) return;

        //------------------------------------------------------------
        // check existence or create push-service message token
        getMessageServiceToken();

        //saved timestamp of last login (deleting old data)
        long llt = getLastLoginTime();
        String user = getUserID();

        Map<String,Object> document = new HashMap<>();
        //document.put("appVersion", BuildConfig.VERSION_NAME);
        document.put("loginTime",new Date());
        document.put("lastLoginTime",llt);
        document.put("connection",getConnectionGUID());
        document.put("userID",user);
        document.put("license",getLicenseKey());
        document.put("baseName",baseName);
        document.put("syncFormat",getSyncFormat());
        document.put("serverAddress",serverAddress);
        document.put("serverPort",getServerPort());
        document.put("serverUser",getUserName());
        document.put("serverPassword",getUserPassword());
        document.put("locationServiceEnabled",getLocationServiceEnabled());
        document.put("locationServicePermission",checkLocationServicePermission());
        document.put("loadImages",loadImages());
        document.put("messageToken",getMessageServiceToken());
        document.put("options",appOptions.getAsJSON().toString());

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        firestore.collection("users")
                .document(user)
                .set(document)
                .addOnSuccessListener(unused -> utils.debug("user data saved successfully"))
                .addOnFailureListener(e -> utils.debug("save user data failed: "+e));

        //auto-send debug logs
        firestore.collection("debug")
                .document(user)
                .get()
                .addOnCompleteListener(task -> {
                   if (task.isSuccessful()){
                       DocumentSnapshot snapshot = task.getResult();
                       if (snapshot != null){
                           if (snapshot.contains("sendLog")) {
                               sendDebugLog = Objects.equals(snapshot.getString("sendLog"), "1");
                               sendLog();
                           }
                           if (snapshot.contains("eraseData")){
                               eraseData = Objects.equals(snapshot.getString("eraseData"), "1");
                           }
                       }
                   }
                })
                .addOnFailureListener(e -> utils.debug("read debug key error: "+e));
    }

    public void sendLog(){
        if (!sendDebugLog) return;

        String logContent = new Utils().readLogs().toString();
        if (logContent.isEmpty()) logContent = "<-- EMPTY -->";

        Map<String,Object> document = new HashMap<>();
        document.put("logTime",new Date());
        document.put("baseName",getDatabaseName());
        document.put("syncFormat",getSyncFormat());
        document.put("serverAddress",getServerName());
        document.put("serverUser",getUserName());
        //document.put("appVersion",BuildConfig.VERSION_NAME);
        document.put("log",logContent);

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        firestore.collection("debug")
                .document(getUserID())
                .set(document, SetOptions.merge());
    }

    /**
     * Call license manager for key validation
     */
    public void checkLicenseKey(){
        LicenseManager licenseManager = new LicenseManager(this::onKeyValidation);
        licenseManager.saveLicenseKey(userID, licenseKey, keyIsActive());
    }

    /**
     * Listener of license key validation result
     *
     * @param keyOptions loaded from cloud key options and state
     */
    private void onKeyValidation(DataBaseItem keyOptions){
        setKeyIsActive(keyOptions.getBoolean("isActive"));
        unlimitedKey = keyOptions.getBoolean("unlimited");
    }

    /**
     * Server specific options are saved in the database, when user switches connection,
     * need to load current options values
     *
     * @param jsonData saved options in a JSON string format
     */
    public void loadOptions(String jsonData){

        initialiseCurrentOptions(jsonData);

        if (!appOptions.getValues().containsKey("allowCashDocuments")) appOptions.put("allowCashDocuments", true);

        setAllowPriceTypeChoose(appOptions.getBoolean("allowPriceTypeChoose"));
        setAllowCreateCashDocuments(appOptions.getBoolean("allowCashDocuments"));
        setCurrencyName(appOptions.getString("currency",DEFAULT_CURRENCY));
        setLoadImages(appOptions.getBoolean("loadImages"));
        setCheckOrderLocation(appOptions.getBoolean("checkOrderLocation"));
        setLastTimeSentLocation(appOptions.getLong("lastLocationTime"));
        setRequireDeliveryDate(appOptions.getBoolean("requireDeliveryDate"));
        setLocationServiceEnabled(appOptions.getBoolean("locations"));
        setClientsDirections(appOptions.getBoolean("clients_directions"));
        setClientsGoods(appOptions.getBoolean("clients_goods"));
        setCheckOrderLocation(appOptions.getBoolean("checkOrderLocation"));
        setShowClientPriceOnly(appOptions.getBoolean("showClientPriceOnly"));
        setClientPriceByDefault(appOptions.getBoolean("setClientPrice"));
        setLocationsWatchList(appOptions.getString("watchList"));

        saveLicenseKey(appOptions.getString("license"));

        if (appOptions.getBoolean("sendLog")) sendDebugLog = true;
        if (appOptions.getBoolean("eraseData")) eraseData = true;
        sendLog();

    }

    public void initialiseCurrentOptions(String jsonData){
        try {
            JSONObject jsonObject = new JSONObject(jsonData);
            appOptions = new DataBaseItem(jsonObject);
        }catch (Exception e){
            appOptions = new DataBaseItem();
            utils.warn("Initialise options: "+e);
        }
    }

    /**
     * Read a boolean value from options. Options are loaded from server as JSON object.
     *
     * @param key key name
     * @return value of the 'key' option
     */
    public boolean readKeyBoolean(String key){
        boolean result = false;
        if (appOptions != null) result = appOptions.getBoolean(key);
        //utils.debug("appSettings.readKeyBoolean( "+key+" ) = "+result);
        return result;
    }
}

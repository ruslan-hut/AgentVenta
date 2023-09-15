package ua.com.programmer.agentventa.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.location.Location;

import androidx.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.UUID;

import ua.com.programmer.agentventa.R;
import ua.com.programmer.agentventa.settings.AppSettings;
import ua.com.programmer.agentventa.utility.Constants;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.Utils;

/**
 * Utility class for the SQLite database operations
 */
public class DataBase {

    private static SQLiteDatabase sqLiteDatabase;
    private static DataBase dataBase;
    private static AppSettings appSettings;
    private static boolean licensed;

    private static String DEFAULT_VALUE;

    /**
     * ID of the current user (connection) account, is used in every table
     */
    private static String ID;

    private final Utils utils = new Utils();

    private DataBase(){}

    /**
     * Get an instance of {@link DataBase}
     *
     * @param context activity context
     * @return DataBase instance
     */
    public synchronized static DataBase getInstance(Context context){
        if (dataBase == null) {
            dataBase = new DataBase();
            DEFAULT_VALUE = context.getString(R.string.default_value);
        }
        if (sqLiteDatabase == null || !sqLiteDatabase.isOpen()) {
            DBHelper helper = getHelper(context);
            if (helper != null) {
                sqLiteDatabase = helper.getReadableDatabase();
            }
        }
        if (appSettings == null) appSettings = AppSettings.getInstance(context);
        ID = appSettings.getConnectionGUID();
        licensed = appSettings.keyIsActive();
        return dataBase;
    }

    private static DBHelper getHelper(Context context) {
        try (DBHelper helper = new DBHelper(context)) {
            return helper;
        } catch (Exception e) {
            return null;
        }
    }

    public SQLiteDatabase getDatabaseInstance(){return sqLiteDatabase;}

    /**
     * Switch between connections ID's for data operations
     *
     * @param newID new ID value
     */
    public void setID(String newID){
        ID = newID;
    }

    /**
     * Returns current database version
     *
     * @return version number
     */
    public String version(){
        return ""+sqLiteDatabase.getVersion();
    }

    /**
     * Returns an instance of {@link AppSettings}
     *
     * @return AppSettings instance
     */
    public AppSettings getAppSettings() {
        return appSettings;
    }

    /**
     * Get list of all connections
     *
     * @return list of datasets
     */
    public ArrayList<DataBaseItem> getConnections(){
        ArrayList<DataBaseItem> list = new ArrayList<>();
        Cursor cursor = sqLiteDatabase.query("user_accounts",null,null,null,null,null,"_id ASC");
        while (cursor.moveToNext()){
            DataBaseItem dataBaseItem = new DataBaseItem(cursor);
            list.add(dataBaseItem);
        }
        cursor.close();
        return list;
    }

    /**
     * Get a set of parameters for a connection with given GUID
     *
     * @param guid connection GUID
     * @return set of parameters
     */
    public DataBaseItem getConnectionParameters(@NonNull String guid){
        DataBaseItem data = new DataBaseItem();
        String[] args = {guid};
        Cursor cursor = sqLiteDatabase.query("user_accounts",null,"guid=?",args,null,null,null);
        if (cursor.moveToFirst()){
            data = new DataBaseItem(cursor);
        }
        cursor.close();
        return data;
    }

    /**
     * Get a set of parameters for a current connection
     *
     * @return set of parameters
     */
    public DataBaseItem getCurrentConnectionParameters(){
        return getConnectionParameters(ID);
    }

    /**
     * Save new or update existing connection data
     *
     * @param param set of connection parameters
     */
    public void saveConnectionParameters(DataBaseItem param){
        String[] args = {param.getString("guid")};
        int updated = sqLiteDatabase.update("user_accounts", param.getValues(), "guid=?", args);
        if (updated == 0) {
            sqLiteDatabase.insert("user_accounts",null,param.getValues());
        }
    }

    /**
     * Delete a connection with given GUID
     *
     * @param guid connection GUID
     */
    public void deleteConnection(String guid){
        sqLiteDatabase.delete("user_accounts","guid='"+guid+"'",null);
    }

    /**
     * Save JSON string data to the database - into current connection record
     *
     * @param options JSON string
     */
    public void saveOptions(String options){
        String[] args = {ID};
        String license = "";
        try {
            JSONObject jsonObject = new JSONObject(options);
            DataBaseItem optionsData = new DataBaseItem(jsonObject);
            license = optionsData.getString("license");
        }catch (Exception e){
            utils.debug("Error reading options from JSON: "+e);
        }
        ContentValues values = new ContentValues();
        values.put("options",options);
        values.put("license",license);
        sqLiteDatabase.update("user_accounts", values, "guid=?", args);
        appSettings.loadOptions(options);
    }

    /**
     * Select a set of documents that needed to be sent to a server
     *
     * @return array of documents data
     */
    public ArrayList<DataBaseItem> getDocumentsForUpload(){
        ArrayList<DataBaseItem> items = new ArrayList<>();
        String[] args = {"1",ID};
        Cursor cursor = sqLiteDatabase.query("documents",null,"is_processed=? AND db_guid=?",args,null,null,"number ASC");
        while (cursor.moveToNext()){
            items.add(new DataBaseItem(cursor));
        }
        cursor.close();
        return items;
    }

    /**
     * Select a set of orders that needed to be sent to a server
     *
     * @return array of documents data
     */
    ArrayList<DataBaseItem> getOrdersForUpload () {
        ArrayList<DataBaseItem> list = new ArrayList<>();
        String[] args = {"1",ID};
        String[] columns = {"_id, guid"};
        Cursor cursor = sqLiteDatabase.query("orders",columns,"is_processed=? AND db_guid=?",args,null,null,"number ASC");
        while (cursor.moveToNext()){
            list.add(new DataBaseItem(cursor));
        }
        cursor.close();
        return list;
    }

    /**
     * Read goods item data
     *
     * @param guid goods item GUID
     * @return data set
     */
    public DataBaseItem getGoodsItemData(String guid){
        DataBaseItem data = new DataBaseItem();
        if (guid == null || guid.isEmpty()) return data;
        String[] args = {ID,guid};
        Cursor cursor = sqLiteDatabase.query("goods",null,"db_guid=? AND guid=?",args,null,null,null);
        if (cursor.moveToFirst()){
            data = new DataBaseItem(cursor);
        }
        cursor.close();
        data.put("type",Constants.DATA_GOODS_ITEM);
        return data;
    }

    /**
     * Get value of goods item price, returns -1 if value not found
     *
     * @param guid goods item GUID
     * @param type price type
     * @return price value
     */
    public double getItemPrice(String guid, String type) {
        double price = -1;
        String[] args = {guid,""+type,ID};
        Cursor cursor = sqLiteDatabase.query("goods_price",null,"item_guid=? AND price_type=? AND db_guid=?",args,null,null,null);
        if (cursor.moveToFirst()) {
            int i = cursor.getColumnIndex("price");
            if (i >= 0) price = cursor.getDouble(i);
        }
        cursor.close();
        return price;
    }

    /**
     * Read goods item data merged with order info
     *
     * @param guid goods item GUID
     * @param orderID order ID
     * @return data set
     */
    public DataBaseItem getGoodsItem(String guid, long orderID) {
        DataBaseItem dataBaseItem = new DataBaseItem();
        if (orderID == 0) return getGoodsItemData(guid);

        String[] args = {""+orderID,guid,ID};
        String queryText =
                "SELECT " +
                        "goods._id as _id, " +
                        "goods.description as description, " +
                        "groups.description as groupName, " +
                        "goods.guid as guid, " +
                        "goods.code1 as code1, " +
                        "goods.code2 as code2, " +
                        "goods.quantity as quantity, " +
                        "content.price as order_price, " +
                        "content.order_id as order_id, " +
                        "goods.price as price, " +
                        "goods.min_price as min_price, " +
                        "goods.base_price as base_price, " +
                        "goods.group_guid as group_guid, " +
                        "goods.is_group as is_group, " +
                        "goods.package_only as package_only," +
                        "goods.package_value as package_value," +
                        "goods.weight as weight," +
                        "goods.unit as unit," +
                        "content.quantity as order_quantity, " +
                        "content.sum as order_sum, " +
                        "content.sum_discount as order_sum_discount, " +
                        "content.is_demand as is_demand, " +
                        "content.is_packed as is_packed " +
                        "FROM goods " +
                        "LEFT OUTER JOIN (SELECT * FROM orders_content WHERE order_id=?) AS content " +
                        "on goods.guid = content.item_guid " +
                        "LEFT OUTER JOIN (SELECT guid,description FROM goods WHERE is_group=1) AS groups "+
                        "ON goods.group_guid = groups.guid "+
                        "WHERE goods.guid=? AND db_guid=?";
        Cursor cursor = sqLiteDatabase.rawQuery(queryText,args);
        if (cursor.moveToNext()){
            dataBaseItem = new DataBaseItem(cursor);
        }
        cursor.close();
        return dataBaseItem;
    }

    public String findGoodsItemByBarcode(String barcode) {
        String guid = "";
        String searchFilter = ""+barcode.trim()+"%";
        String[] args = {searchFilter,ID};
        String[] columns = {"guid"};
        Cursor cursor = sqLiteDatabase.query("goods",columns,"barcode LIKE ? AND db_guid=?",args,null,null,"description");
        while (cursor.moveToNext()){
            DataBaseItem item = new DataBaseItem(cursor);
            guid = item.getString("guid");
        }
        cursor.close();
        return guid;
    }

    /**
     * Read only records with empty item GUID
     *
     * @return array of price descriptions
     */
    public ArrayList<DataBaseItem> getCompetitorPrices(){
        ArrayList<DataBaseItem> list = new ArrayList<>();
        String[] args = {"",ID};
        Cursor cursor = sqLiteDatabase.query("competitor_price",null,"item_guid=? AND db_guid=?",args,null,null,"description");
        while (cursor.moveToNext()){
            DataBaseItem priceType = new DataBaseItem(cursor);
            priceType.setType(Constants.DATA_COMPETITOR_PRICE);
            list.add(priceType);
        }
        cursor.close();
        return list;
    }

    /**
     * Save user edits of competitor prices
     *
     * @param priceItem dataset of changes
     */
    public void saveCompetitorPrice(DataBaseItem priceItem){
        String item = priceItem.getString("item_guid");
        String client = priceItem.getString("client_guid");
        String price = priceItem.getString("price_guid");

        if (item.isEmpty() || client.isEmpty() || price.isEmpty()) return;

        ContentValues cv = new ContentValues();
        cv.put("time_stamp",1);
        cv.put("notes",priceItem.getString("notes"));
        cv.put("price",priceItem.getString("price"));

        String[] args = {item,client,price,ID};
        int row = sqLiteDatabase.update("competitor_price",cv,"item_guid=? AND client_guid=? AND price_guid=? AND db_guid=?",args);
        if (row == 0){
            cv.put("item_guid",item);
            cv.put("client_guid",client);
            cv.put("price_guid",price);
            cv.put("db_guid",ID);
            sqLiteDatabase.insert("competitor_price",null,cv);
        }

    }

    /**
     * Read data of a separate competitor's price
     *
     * @param itemGUID goods item GUID
     * @param clientGUID client GUID
     * @param priceGUID price GUID
     * @return dataset of price parameters
     */
    public DataBaseItem getCompetitorPriceData(String itemGUID, String clientGUID, String priceGUID){
        DataBaseItem price;
        String[] args = {itemGUID,clientGUID,priceGUID,ID};
        Cursor cursor = sqLiteDatabase.query("competitor_price",null,"item_guid=? AND client_guid=? AND price_guid=? AND db_guid=?",args,null,null,"description");
        if (cursor.moveToNext()) {
            price = new DataBaseItem(cursor);
            price.setType(Constants.DATA_COMPETITOR_PRICE);
        }else{
            price = new DataBaseItem(Constants.DATA_COMPETITOR_PRICE);
        }
        cursor.close();
        return price;
    }

    public JSONObject getCompetitorPricesForUpload(){
        JSONObject list = new JSONObject();
        JSONArray data = new JSONArray();
        String[] args = {ID};
        Cursor cursor = sqLiteDatabase.query("competitor_price",null,"time_stamp=1 AND db_guid=?",args,null,null,null);
        while (cursor.moveToNext()){
            DataBaseItem priceType = new DataBaseItem(cursor);
            data.put(priceType.getAsJSON());
        }
        cursor.close();
        try {
            list.put("type", Constants.DATA_COMPETITOR_PRICE);
            list.put("data", data);
        }catch (Exception e){
            utils.error("Get competitor prices for upload: "+e);
            list = null;
        }
        return list;
    }

    /**
     * Get all price types
     *
     * @return array of data sets
     */
    public ArrayList<DataBaseItem> getPriceTypesArray(){
        ArrayList<DataBaseItem> list = new ArrayList<>();
        Cursor cursor = sqLiteDatabase.query("price_types",null,"db_guid='"+ID+"'",null,null,null,"code");
        while (cursor.moveToNext()){
            DataBaseItem priceType = new DataBaseItem(cursor);
            list.add(priceType);
        }
        cursor.close();
        return list;
    }

    public ArrayList<String> getPriceTypesArrayS(){
        ArrayList<String> list = new ArrayList<>();
        String[] args = {ID};
        String[] columns = {"description"};
        Cursor cursor = sqLiteDatabase.query("price_types",columns,"db_guid=?",args,null,null,"code");
        while (cursor.moveToNext()){
            list.add(cursor.getString(0));
        }
        cursor.close();
        return list;
    }

    /**
     * Get all price types as array of Strings
     *
     * @return array of Strings
     */
    public String[] getPriceTypes(){
        ArrayList<String> typesList = new ArrayList<>();
        typesList.add(DEFAULT_VALUE);
        String priceType;

        Cursor cursor = sqLiteDatabase.query("price_types",null,"db_guid='"+ID+"'",null,null,null,null);
        while (cursor.moveToNext()){
            int i = cursor.getColumnIndex("description");
            if (i >= 0) {
                priceType = cursor.getString(i);
                if (!typesList.contains(priceType)){
                    typesList.add(priceType);
                }
            }
        }
        cursor.close();

        String[] types = new String[typesList.size()];
        types[0] = DEFAULT_VALUE;
        for (int i=0; i<typesList.size(); i++){
            types[i] = typesList.get(i);
        }

        return types;
    }

    /**
     * Saves goods item data to the database, replaces existing row if found by GUID,
     * or inserts new one
     *
     * @param item goods item data set
     */
    private void saveGoodsItem(DataBaseItem item){
        String guid = item.getString("guid");
        if (guid.isEmpty()) return;
        ContentValues values = item.getValuesForDataBase();
        String[] args = {ID,guid};
        if (sqLiteDatabase.update("goods", values, "db_guid=? AND guid=?", args) == 0) {
            sqLiteDatabase.insert("goods",null,values);
        }
    }

    /**
     * Saves content (JSON string) to the database
     *
     * @param documentGUID GUID of a document
     * @param content JSON string to save
     */
    private void saveDebtDocumentContent(String documentGUID, String content){
        String[] args = {ID,documentGUID};
        ContentValues cv = new ContentValues();
        cv.put("content",content);
        sqLiteDatabase.update("debts",cv,"db_guid=? AND doc_guid=?",args);
    }

    /**
     * Saves data set to the database, according to its type
     *
     * @param dataBaseItem data set
     */
    public void saveDataItem(DataBaseItem dataBaseItem){
        String type = dataBaseItem.type();
        switch (type) {
            case Constants.DATA_GOODS_ITEM:
                saveGoodsItem(dataBaseItem);
                break;
            case Constants.DATA_OPTIONS:
                saveOptions(dataBaseItem.getString("options"));
                break;
            case Constants.DATA_DEBT_DOCUMENT:
                saveDebtDocumentContent(dataBaseItem.getString("guid"),dataBaseItem.getString("content"));
                break;
        }
    }

    /**
     * Utility method for statement parameters binding
     * For the table: goods
     *
     * @param statement SQLite statement
     * @param dataBaseItem set of values
     */
    private void bindGoodsStatement(SQLiteStatement statement,DataBaseItem dataBaseItem){
        String description = dataBaseItem.getString("description");
        String groupGUID = dataBaseItem.getString("group_guid");

        statement.clearBindings();

        statement.bindString(1,dataBaseItem.getString("code1"));
        statement.bindString(2,dataBaseItem.getString("code2"));
        statement.bindString(3,dataBaseItem.getString("barcode"));
        statement.bindString(4,dataBaseItem.getString("vendor_code"));
        statement.bindString(5,dataBaseItem.getString("vendor_status"));
        statement.bindString(6,dataBaseItem.getString("sorting"));
        statement.bindString(7,description);
        statement.bindDouble(8,dataBaseItem.getDouble("price"));
        statement.bindDouble(9,dataBaseItem.getDouble("min_price"));
        statement.bindDouble(10,dataBaseItem.getDouble("base_price"));
        statement.bindDouble(11,dataBaseItem.getDouble("quantity"));

        if (!groupGUID.isEmpty()) {
            statement.bindString(12,groupGUID);
        }else{
            statement.bindNull(12);
        }

        statement.bindLong(13,dataBaseItem.getInt("is_group"));
        statement.bindString(14, description.toLowerCase());
        statement.bindLong(15,dataBaseItem.getInt("package_only"));
        statement.bindDouble(16,dataBaseItem.getDouble("package_value"));
        statement.bindDouble(17,dataBaseItem.getDouble("weight"));
        statement.bindString(18,dataBaseItem.getString("unit"));
        statement.bindLong(19,dataBaseItem.getInt("indivisible"));
        statement.bindString(20,dataBaseItem.getString("rest_type"));
        statement.bindString(21,dataBaseItem.getString("status"));
        statement.bindLong(22,dataBaseItem.getLong(Constants.DATA_TIME_STAMP));
        statement.bindString(23,dataBaseItem.getString("is_active","1"));
        statement.bindString(24,dataBaseItem.getString("guid"));
        statement.bindString(25,ID);
    }

    /**
     * Save data set of goods items in transaction
     *
     * @param arrayList array of goods items data
     */
    private void bulkSaveGoodsItems(ArrayList<DataBaseItem> arrayList){
        int row;
        String queryText;
        SQLiteStatement statement;
        try {
            sqLiteDatabase.beginTransaction();

            ArrayList<DataBaseItem> insertArray = new ArrayList<>();

            queryText = "UPDATE goods SET " +
                    "code1=?, " +
                    "code2=?, " +
                    "barcode=?, " +
                    "vendor_code=?, " +
                    "vendor_status=?, " +
                    "sorting=?, " +
                    "description=?, " +
                    "price=?, " +
                    "min_price=?, " +
                    "base_price=?, " +
                    "quantity=?, " +
                    "group_guid=?, " +
                    "is_group=?, " +
                    "description_lc=?, " +
                    "package_only=?, " +
                    "package_value=?, " +
                    "weight=?, " +
                    "unit=?, " +
                    "indivisible=?, "+
                    "rest_type=?, "+
                    "status=?, "+
                    "time_stamp=?, "+
                    "is_active=? " +
                    "WHERE guid=? AND db_guid=?";
            statement = sqLiteDatabase.compileStatement(queryText);

            for (DataBaseItem dataBaseItem: arrayList) {
                bindGoodsStatement(statement,dataBaseItem);
                row = statement.executeUpdateDelete();
                if (row == 0) insertArray.add(dataBaseItem);
            }

            if (insertArray.size() > 0){
                queryText = "INSERT OR IGNORE INTO goods (" +
                        "code1," +
                        "code2," +
                        "barcode," +
                        "vendor_code," +
                        "vendor_status," +
                        "sorting," +
                        "description," +
                        "price," +
                        "min_price," +
                        "base_price," +
                        "quantity," +
                        "group_guid," +
                        "is_group," +
                        "description_lc," +
                        "package_only," +
                        "package_value," +
                        "weight," +
                        "unit," +
                        "indivisible,"+
                        "rest_type,"+
                        "status,"+
                        "time_stamp, "+
                        "is_active," +
                        "guid," +
                        "db_guid"+
                        ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
                statement = sqLiteDatabase.compileStatement(queryText);

                for (DataBaseItem dataBaseItem: insertArray) {
                    bindGoodsStatement(statement,dataBaseItem);
                    statement.executeInsert();
                }
            }

            sqLiteDatabase.setTransactionSuccessful();
        }catch (Exception sqlException) {
            utils.error("INSERT goods: " + sqlException);
        }finally {
            sqLiteDatabase.endTransaction();
        }
    }

    /**
     * Utility method for statement parameters binding
     * For the table: clients
     *
     * @param statement SQLite statement
     * @param dataBaseItem set of values
     */
    private void bindClientStatement(SQLiteStatement statement,DataBaseItem dataBaseItem){
        String description = dataBaseItem.getString("description");
        String groupGUID = dataBaseItem.getString("group_guid");

        statement.clearBindings();

        statement.bindString(1,dataBaseItem.getString("code1"));
        statement.bindString(2,dataBaseItem.getString("code2"));
        statement.bindString(3,description);

        if (!groupGUID.isEmpty()) {
            statement.bindString(4,groupGUID);
        }else{
            statement.bindNull(4);
        }

        statement.bindLong(5,dataBaseItem.getInt("is_group"));
        statement.bindString(6, description.toLowerCase());
        statement.bindString(7,dataBaseItem.getString("notes"));
        statement.bindString(8,dataBaseItem.getString("phone"));
        statement.bindString(9,dataBaseItem.getString("address"));
        statement.bindDouble(10,dataBaseItem.getDouble("discount"));
        statement.bindString(11,dataBaseItem.getString("price_type"));
        statement.bindDouble(12,dataBaseItem.getDouble("bonus"));
        statement.bindLong(13,dataBaseItem.getLong("is_banned"));
        statement.bindString(14,dataBaseItem.getString("ban_message"));
        statement.bindLong(15,dataBaseItem.getLong(Constants.DATA_TIME_STAMP));
        statement.bindString(16,dataBaseItem.getString("guid"));
        statement.bindString(17,ID);
    }

    /**
     * Save data set of clients in transaction
     *
     * @param arrayList array of clients data
     */
    private void bulkSaveClients(ArrayList<DataBaseItem> arrayList){
        int row;
        String queryText;
        SQLiteStatement statement;
        try {
            sqLiteDatabase.beginTransaction();

            ArrayList<DataBaseItem> insertArray = new ArrayList<>();

            queryText = "UPDATE clients SET " +
                    "code1=?, " +
                    "code2=?, " +
                    "description=?, " +
                    "group_guid=?, " +
                    "is_group=?, " +
                    "description_lc=?, " +
                    "notes=?, " +
                    "phone=?, " +
                    "address=?, " +
                    "discount=?, " +
                    "price_type=?, " +
                    "bonus=?, " +
                    "is_banned=?, " +
                    "ban_message=?, " +
                    "time_stamp=? " +
                    "WHERE guid=? AND  db_guid=?";
            statement = sqLiteDatabase.compileStatement(queryText);

            for (DataBaseItem dataBaseItem: arrayList) {
                bindClientStatement(statement,dataBaseItem);
                row = statement.executeUpdateDelete();
                if (row == 0) insertArray.add(dataBaseItem);
            }

            if (insertArray.size() > 0){
                queryText = "INSERT OR IGNORE INTO clients (" +
                        "code1," +
                        "code2," +
                        "description," +
                        "group_guid," +
                        "is_group," +
                        "description_lc," +
                        "notes," +
                        "phone," +
                        "address," +
                        "discount," +
                        "price_type," +
                        "bonus," +
                        "is_banned," +
                        "ban_message," +
                        "time_stamp," +
                        "guid," +
                        "db_guid"+
                        ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
                statement = sqLiteDatabase.compileStatement(queryText);

                for (DataBaseItem dataBaseItem: insertArray) {
                    bindClientStatement(statement,dataBaseItem);
                    statement.executeInsert();
                }
            }

            sqLiteDatabase.setTransactionSuccessful();

        }catch (Exception sqlException) {
            utils.error("INSERT clients: " + sqlException);
        }finally {
            sqLiteDatabase.endTransaction();
        }
    }

    /**
     * Save data set of goods prices in transaction
     * NOTE: input data contains price type code and price type name,
     * price's names are saved in a separate table
     *
     * @param arrayList array of price data
     */
    private void bulkSavePrice(ArrayList<DataBaseItem> arrayList){
        int row;
        ArrayList<String> priceTypesCollection = new ArrayList<>();
        ArrayList<ContentValues> priceTypes = new ArrayList<>();

        String queryText;
        SQLiteStatement statement;
        try {
            sqLiteDatabase.beginTransaction();

            ArrayList<DataBaseItem> insertArray = new ArrayList<>();

            queryText = "UPDATE goods_price SET " +
                    "price=?, time_stamp=? " +
                    "WHERE item_guid=? AND price_type=? AND db_guid=?";
            statement = sqLiteDatabase.compileStatement(queryText);

            for (DataBaseItem dataBaseItem: arrayList) {
                statement.clearBindings();
                statement.bindDouble(1,dataBaseItem.getDouble("price"));
                statement.bindLong(2,dataBaseItem.getLong("time_stamp"));
                statement.bindString(3,dataBaseItem.getString("item_guid"));
                statement.bindString(4,dataBaseItem.getString("price_type"));
                statement.bindString(5,ID);
                row = statement.executeUpdateDelete();
                if (row == 0) insertArray.add(dataBaseItem);

                //check and save price's name data in the temporary array
                String priceType = dataBaseItem.getString("price_name");
                if (!priceTypesCollection.contains(priceType)){
                    priceTypesCollection.add(priceType);
                    ContentValues contentValues = new ContentValues();
                    contentValues.put("code",dataBaseItem.getString("price_type"));
                    contentValues.put("description",dataBaseItem.getString("price_name"));
                    contentValues.put("db_guid",ID);
                    priceTypes.add(contentValues);
                }
            }

            if (insertArray.size() > 0){
                queryText = "INSERT OR IGNORE INTO goods_price (" +
                        "item_guid,"+
                        "price_type," +
                        "price," +
                        "time_stamp," +
                        "db_guid"+
                        ") VALUES (?,?,?,?,?)";
                statement = sqLiteDatabase.compileStatement(queryText);

                for (DataBaseItem dataBaseItem: insertArray) {
                    statement.clearBindings();
                    statement.bindString(1,dataBaseItem.getString("item_guid"));
                    statement.bindString(2,dataBaseItem.getString("price_type"));
                    statement.bindDouble(3,dataBaseItem.getDouble("price"));
                    statement.bindLong(4,dataBaseItem.getLong("time_stamp"));
                    statement.bindString(5,ID);
                    statement.executeInsert();
                }
            }

            sqLiteDatabase.setTransactionSuccessful();

            //save price's names to the database
            for (ContentValues contentValues: priceTypes){
                String[] args = {contentValues.getAsString("code"),ID};
                row = sqLiteDatabase.update("price_types",contentValues,"code=? AND db_guid=?",args);
                if (row == 0){
                    sqLiteDatabase.insertWithOnConflict("price_types",null,contentValues,SQLiteDatabase.CONFLICT_REPLACE);
                }
            }

        }catch (Exception sqlException) {
            utils.error("INSERT price: " + sqlException);
        }finally {
            sqLiteDatabase.endTransaction();
        }
    }

    private void bulkSaveCompetitorPrice(ArrayList<DataBaseItem> arrayList){
        int row;
        sqLiteDatabase.execSQL("delete from competitor_price");

        String queryText;
        SQLiteStatement statement;
        try {
            sqLiteDatabase.beginTransaction();

            ArrayList<DataBaseItem> insertArray = new ArrayList<>();

            queryText = "UPDATE competitor_price SET " +
                    "description=?, time_stamp=? " +
                    "WHERE item_guid=? AND price_guid=? AND db_guid=?";
            statement = sqLiteDatabase.compileStatement(queryText);

            for (DataBaseItem dataBaseItem: arrayList) {
                statement.clearBindings();
                statement.bindString(1,dataBaseItem.getString("description"));
                statement.bindLong(2,dataBaseItem.getLong("time_stamp"));
                statement.bindString(3,dataBaseItem.getString("item_guid"));
                statement.bindString(4,dataBaseItem.getString("price_guid"));
                statement.bindString(5,ID);
                row = statement.executeUpdateDelete();
                if (row == 0) insertArray.add(dataBaseItem);
            }

            if (insertArray.size() > 0){
                queryText = "INSERT OR IGNORE INTO competitor_price (" +
                        "item_guid,"+
                        "price_guid," +
                        "description," +
                        "time_stamp," +
                        "db_guid"+
                        ") VALUES (?,?,?,?,?)";
                statement = sqLiteDatabase.compileStatement(queryText);

                for (DataBaseItem dataBaseItem: insertArray) {
                    statement.clearBindings();
                    statement.bindString(1,dataBaseItem.getString("item_guid"));
                    statement.bindString(2,dataBaseItem.getString("price_guid"));
                    statement.bindString(3,dataBaseItem.getString("description"));
                    statement.bindLong(4,dataBaseItem.getLong("time_stamp"));
                    statement.bindString(5,ID);
                    statement.executeInsert();
                }
            }

            sqLiteDatabase.setTransactionSuccessful();

        }catch (Exception sqlException) {
            utils.error("INSERT competitor price: " + sqlException);
        }finally {
            sqLiteDatabase.endTransaction();
        }
    }

    /**
     * Utility method for statement parameters binding
     * For the table: debts
     *
     * @param statement SQLite statement
     * @param dataBaseItem set of values
     */
    private void bindDebtsStatement(SQLiteStatement statement,DataBaseItem dataBaseItem){
        statement.clearBindings();
        statement.bindString(1,dataBaseItem.getString("doc_guid"));
        statement.bindString(2,dataBaseItem.getString("doc_type"));
        statement.bindLong(3,dataBaseItem.getLong("sorting"));
        statement.bindLong(4,dataBaseItem.getLong("has_content"));
        statement.bindString(5,dataBaseItem.getString("content"));
        statement.bindDouble(6,dataBaseItem.getDouble("sum"));
        statement.bindDouble(7,dataBaseItem.getDouble("sum_in"));
        statement.bindDouble(8,dataBaseItem.getDouble("sum_out"));
        statement.bindLong(9,dataBaseItem.getLong("time_stamp"));
        statement.bindString(10,dataBaseItem.getString("client_guid"));
        statement.bindString(11,dataBaseItem.getString("doc_id"));
        statement.bindString(12,ID);
    }

    /**
     * Save data set of debts in transaction
     *
     * @param arrayList array of debts data
     */
    private void bulkSaveDebts(ArrayList<DataBaseItem> arrayList){
        int row;
        String queryText;
        SQLiteStatement statement;
        try {
            sqLiteDatabase.beginTransaction();

            ArrayList<DataBaseItem> insertArray = new ArrayList<>();

            queryText = "UPDATE debts SET " +
                    "doc_guid=?," +
                    "doc_type=?," +
                    "sorting=?," +
                    "has_content=?," +
                    "content=?," +
                    "sum=?," +
                    "sum_in=?," +
                    "sum_out=?," +
                    "time_stamp=? " +
                    "WHERE client_guid=? AND doc_id=? AND db_guid=?";
            statement = sqLiteDatabase.compileStatement(queryText);

            for (DataBaseItem dataBaseItem: arrayList) {
                bindDebtsStatement(statement,dataBaseItem);
                row = statement.executeUpdateDelete();
                if (row == 0) insertArray.add(dataBaseItem);
            }

            if (insertArray.size() > 0){
                queryText = "INSERT OR IGNORE INTO debts (" +
                        "doc_guid," +
                        "doc_type," +
                        "sorting," +
                        "has_content," +
                        "content," +
                        "sum," +
                        "sum_in," +
                        "sum_out," +
                        "time_stamp," +
                        "client_guid," +
                        "doc_id," +
                        "db_guid"+
                        ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
                statement = sqLiteDatabase.compileStatement(queryText);

                for (DataBaseItem dataBaseItem: insertArray) {
                    bindDebtsStatement(statement,dataBaseItem);
                    statement.executeInsert();
                }
            }

            sqLiteDatabase.setTransactionSuccessful();
        }catch (Exception sqlException) {
            utils.error("INSERT debt: " + sqlException.getLocalizedMessage());
        }finally {
            sqLiteDatabase.endTransaction();
        }
    }

    /**
     * Save data set of debt documents content in transaction
     *
     * @param arrayList array of content data
     */
    private void bulkSaveDebtsContent(ArrayList<DataBaseItem> arrayList){
        String queryText;
        SQLiteStatement statement;
        try {
            sqLiteDatabase.beginTransaction();
            queryText = "UPDATE debts SET content=? WHERE doc_guid=? AND db_guid=?";
            statement = sqLiteDatabase.compileStatement(queryText);

            for (DataBaseItem dataBaseItem: arrayList) {
                statement.clearBindings();
                statement.bindString(1,dataBaseItem.getString("content"));
                statement.bindString(2,dataBaseItem.getString("guid"));
                statement.bindString(3,ID);
                statement.executeInsert();
            }
            sqLiteDatabase.setTransactionSuccessful();
        }catch (Exception sqlException) {
            utils.error("UPDATE debt content: " + sqlException);
        }finally {
            sqLiteDatabase.endTransaction();
        }
    }

    /**
     * Utility method for statement parameters binding
     * For the table: images
     *
     * @param statement SQLite statement
     * @param dataBaseItem set of values
     */
    private void bindImagesStatement(SQLiteStatement statement,DataBaseItem dataBaseItem){
        statement.clearBindings();
        statement.bindString(1,dataBaseItem.getString("image_guid"));
        statement.bindString(2,dataBaseItem.getString("url"));
        statement.bindLong(3,dataBaseItem.getLong("time"));
        statement.bindString(4,dataBaseItem.getString("description"));
        statement.bindString(5,dataBaseItem.getString("type"));
        statement.bindString(6,dataBaseItem.getString("item_guid"));
        statement.bindLong(7,dataBaseItem.getLong("default"));
        statement.bindString(8,ID);
    }

    /**
     * Save data set of image data in transaction
     *
     * @param arrayList array of image data
     */
    private void bulkSaveImages(ArrayList<DataBaseItem> arrayList){
        int row;
        String queryText;
        SQLiteStatement statement;
        try {
            sqLiteDatabase.beginTransaction();

            ArrayList<DataBaseItem> insertArray = new ArrayList<>();

            queryText = "UPDATE images SET " +
                    "image_guid=?," +
                    "url=?," +
                    "time=?," +
                    "description=?," +
                    "type=? " +
                    "WHERE item_guid=? AND is_default=? AND db_guid=?";
            statement = sqLiteDatabase.compileStatement(queryText);

            for (DataBaseItem dataBaseItem: arrayList) {
                bindImagesStatement(statement,dataBaseItem);
                row = statement.executeUpdateDelete();
                if (row == 0) insertArray.add(dataBaseItem);
            }

            if (insertArray.size() > 0){
                queryText = "INSERT OR IGNORE INTO images(" +
                        "image_guid," +
                        "url," +
                        "time," +
                        "description," +
                        "type," +
                        "item_guid," +
                        "is_default," +
                        "db_guid" +
                        ") VALUES (?,?,?,?,?,?,?,?)";
                statement = sqLiteDatabase.compileStatement(queryText);

                for (DataBaseItem dataBaseItem: insertArray) {
                    bindImagesStatement(statement,dataBaseItem);
                    statement.executeInsert();
                }
            }

            sqLiteDatabase.setTransactionSuccessful();
        }catch (Exception sqlException) {
            utils.error("INSERT images: " + sqlException);
        }finally {
            sqLiteDatabase.endTransaction();
        }
    }

    /**
     * Save data set of clients locations data in transaction
     *
     * @param arrayList array of locations data
     */
    private void bulkSaveClientsLocations(ArrayList<DataBaseItem> arrayList){
        int row;
        String queryText;
        SQLiteStatement statement;
        try {
            sqLiteDatabase.beginTransaction();

            ArrayList<DataBaseItem> insertArray = new ArrayList<>();

            queryText = "UPDATE clients_locations SET " +
                    "latitude=?," +
                    "longitude=?," +
                    "modified=?" +
                    "WHERE client_guid=? AND db_guid=?";
            statement = sqLiteDatabase.compileStatement(queryText);

            for (DataBaseItem dataBaseItem: arrayList) {
                statement.clearBindings();
                statement.bindDouble(1,dataBaseItem.getDouble("latitude"));
                statement.bindDouble(2,dataBaseItem.getDouble("longitude"));
                statement.bindLong(3,0);
                statement.bindString(4,dataBaseItem.getString("client_guid"));
                statement.bindString(5,ID);
                row = statement.executeUpdateDelete();
                if (row == 0) insertArray.add(dataBaseItem);
            }

            if (insertArray.size() > 0){
                queryText = "INSERT OR IGNORE INTO clients_locations(" +
                        "latitude," +
                        "longitude," +
                        "modified," +
                        "client_guid," +
                        "db_guid" +
                        ") VALUES (?,?,?,?,?)";
                statement = sqLiteDatabase.compileStatement(queryText);

                for (DataBaseItem dataBaseItem: insertArray) {
                    statement.clearBindings();
                    statement.bindDouble(1,dataBaseItem.getDouble("latitude"));
                    statement.bindDouble(2,dataBaseItem.getDouble("longitude"));
                    statement.bindLong(3,0);
                    statement.bindString(4,dataBaseItem.getString("client_guid"));
                    statement.bindString(5,ID);
                    statement.executeInsert();
                }
            }

            sqLiteDatabase.setTransactionSuccessful();
        }catch (Exception sqlException) {
            utils.error("INSERT clients locations: " + sqlException);
        }finally {
            sqLiteDatabase.endTransaction();
        }
    }

    /**
     * Utility method for statement parameters binding
     * For the table: clients_directions
     *
     * @param statement SQLite statement
     * @param dataBaseItem set of values
     */
    private void bindDirectionsStatement(SQLiteStatement statement,DataBaseItem dataBaseItem){
        statement.clearBindings();
        statement.bindString(1,dataBaseItem.getString("description"));
        statement.bindLong(2,dataBaseItem.getInt("is_group"));

        String groupGuid = dataBaseItem.getString("group_guid");
        if (groupGuid.isEmpty()) {
            statement.bindNull(3);
        }else{
            statement.bindString(3,groupGuid);
        }

        statement.bindString(4,dataBaseItem.getString("area"));
        statement.bindString(5,dataBaseItem.getString("city"));
        statement.bindString(6,dataBaseItem.getString("city_type"));
        statement.bindString(7,dataBaseItem.getString("notes"));
        statement.bindString(8,dataBaseItem.getString("info"));
        statement.bindLong(9,dataBaseItem.getLong(Constants.DATA_TIME_STAMP));
        statement.bindString(10,dataBaseItem.getString("client_guid"));
        statement.bindString(11,dataBaseItem.getString("direction_guid"));
        statement.bindString(12,ID);
    }

    /**
     * Save data set of clients directions data in transaction
     *
     * @param arrayList array of locations data
     */
    private void bulkSaveClientsDirections(ArrayList<DataBaseItem> arrayList){
        int row;
        String queryText;
        SQLiteStatement statement;
        try {
            sqLiteDatabase.beginTransaction();

            ArrayList<DataBaseItem> insertArray = new ArrayList<>();

            queryText = "UPDATE clients_directions SET " +
                    "description=?," +
                    "is_group=?," +
                    "group_guid=?," +
                    "area=?," +
                    "city=?," +
                    "city_type=?," +
                    "notes=?," +
                    "info=?," +
                    "time_stamp=? " +
                    "WHERE client_guid=? AND direction_guid=? AND db_guid=?";
            statement = sqLiteDatabase.compileStatement(queryText);

            for (DataBaseItem dataBaseItem: arrayList) {
                bindDirectionsStatement(statement,dataBaseItem);
                row = statement.executeUpdateDelete();
                if (row == 0) insertArray.add(dataBaseItem);
            }

            if (insertArray.size() > 0){
                queryText = "INSERT OR IGNORE INTO clients_directions(" +
                        "description," +
                        "is_group," +
                        "group_guid," +
                        "area," +
                        "city," +
                        "city_type," +
                        "notes," +
                        "info," +
                        "time_stamp," +
                        "client_guid," +
                        "direction_guid," +
                        "db_guid" +
                        ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
                statement = sqLiteDatabase.compileStatement(queryText);

                for (DataBaseItem dataBaseItem: insertArray) {
                    bindDirectionsStatement(statement,dataBaseItem);
                    if (statement.executeInsert() < 0){
                        String errorMessage = "client_guid: " +
                                dataBaseItem.getString("client_guid") +
                                "; direction_guid: " +
                                dataBaseItem.getString("direction_guid");
                        utils.warn("Directions insert error: "+ errorMessage);
                    }
                }
            }

            sqLiteDatabase.setTransactionSuccessful();
        }catch (Exception sqlException) {
            utils.error("INSERT clients directions: " + sqlException);
        }finally {
            sqLiteDatabase.endTransaction();
        }
    }

    /**
     * Utility method for statement parameters binding
     * For the table: clients_goods
     *
     * @param statement SQLite statement
     * @param dataBaseItem set of values
     */
    private void bindClientGoodsStatement(SQLiteStatement statement,DataBaseItem dataBaseItem){
        statement.clearBindings();
        statement.bindLong(1,dataBaseItem.getInt("sort_order"));
        statement.bindString(2,dataBaseItem.getString("item_group_guid"));
        statement.bindLong(3,dataBaseItem.getInt("no_shipment"));
        statement.bindString(4,dataBaseItem.getString("shipment_date"));
        statement.bindDouble(5,dataBaseItem.getDouble("shipment_quantity"));
        statement.bindString(6,dataBaseItem.getString("category"));
        statement.bindString(7,dataBaseItem.getString("brand"));
        statement.bindLong(8,dataBaseItem.getLong(Constants.DATA_TIME_STAMP));
        statement.bindString(9,dataBaseItem.getString("client_guid"));
        statement.bindString(10,dataBaseItem.getString("item_guid"));
        statement.bindString(11,ID);
    }

    /**
     * Save data set of clients goods items in transaction
     *
     * @param arrayList array of goods data
     */
    private void bulkSaveClientsGoodsItems(ArrayList<DataBaseItem> arrayList){
        int row;
        String queryText;
        SQLiteStatement statement;
        try {
            sqLiteDatabase.beginTransaction();

            ArrayList<DataBaseItem> insertArray = new ArrayList<>();

            queryText = "UPDATE clients_goods SET " +
                    "sort_order=?," +
                    "item_group_guid=?," +
                    "no_shipment=?," +
                    "shipment_date=?," +
                    "shipment_quantity=?," +
                    "category=?," +
                    "brand=?," +
                    "time_stamp=? " +
                    "WHERE client_guid=? AND item_guid=? AND db_guid=?";
            statement = sqLiteDatabase.compileStatement(queryText);

            for (DataBaseItem dataBaseItem: arrayList) {
                bindClientGoodsStatement(statement,dataBaseItem);
                row = statement.executeUpdateDelete();
                if (row == 0) insertArray.add(dataBaseItem);
            }

            if (insertArray.size() > 0){
                queryText = "INSERT OR IGNORE INTO clients_goods(" +
                        "sort_order," +
                        "item_group_guid," +
                        "no_shipment," +
                        "shipment_date," +
                        "shipment_quantity," +
                        "category," +
                        "brand," +
                        "time_stamp," +
                        "client_guid," +
                        "item_guid," +
                        "db_guid" +
                        ") VALUES (?,?,?,?,?,?,?,?,?,?,?)";
                statement = sqLiteDatabase.compileStatement(queryText);

                for (DataBaseItem dataBaseItem: insertArray) {
                    bindClientGoodsStatement(statement,dataBaseItem);
                    if (statement.executeInsert() < 0){
                        String errorMessage = "client_guid: " +
                                dataBaseItem.getString("client_guid") +
                                "; item_guid: " +
                                dataBaseItem.getString("item_guid");
                        utils.warn("Client goods insert error: "+ errorMessage);
                    }
                }
            }

            sqLiteDatabase.setTransactionSuccessful();
        }catch (Exception sqlException) {
            utils.error("INSERT clients goods: " + sqlException);
        }finally {
            sqLiteDatabase.endTransaction();
        }
    }

    private void bulkSaveLoadResponse(ArrayList<DataBaseItem> arrayList){
        String documentType;
        String status;
        String result;
        String guid;
        long timestamp = 0;

        for (DataBaseItem documentData: arrayList){

            documentType = documentData.getString("saved_data_type");
            status = documentData.getString("status");
            guid = documentData.getString("guid");
            result = documentData.getString("result");

            String[] args = {guid,ID};
            String table;

            ContentValues cv = new ContentValues();
            if (result.equals("ok")) {
                cv.put("is_processed", 2);
                cv.put("is_sent", 1);
                cv.put("status", status);
            }else{
                cv.put("status", status);
            }

            switch (documentType) {
                case Constants.DOCUMENT_ORDER:
                    table = "orders";
                    break;
                case Constants.DOCUMENT_CASH:
                    table = "documents";
                    break;
                case Constants.DATA_LOCATION:
                    if (!status.isEmpty()) timestamp = utils.getLong(status);
                    if (timestamp > 0) appSettings.setLastTimeSentLocation(timestamp);
                    continue;
                case Constants.DATA_PUSH_TOKEN:
                    if (!result.equals("ok")) utils.debug("push token not saved on server");
                    continue;
                case Constants.DATA_CLIENT_LOCATION:
                    if (result.equals("ok")) markClientLocationsSaved();
                    continue;
                case Constants.DATA_COMPETITOR_PRICE:
                    if (result.equals("ok")) markCompetitorPricesSent();
                    continue;
                default:
                    utils.debug("Response: " + documentData.getAsJSON().toString());
                    utils.warn("Save response: Unsupported data type: " + documentType);
                    continue;
            }

            sqLiteDatabase.update(table,cv,"guid=? AND db_guid=?",args);

        }
    }

    /**
     * Clean table "images" from records refers to deleted goods items
     */
    private void deleteObsoleteImages(){
        String[] args = {ID,ID};
        String clause = "item_guid NOT IN (SELECT guid FROM goods WHERE db_guid=?) AND db_guid=?";
        int counter = sqLiteDatabase.delete("images",clause,args);
        if (counter > 0) utils.debug("Obsolete images removed: "+counter);
    }

    /**
     * Utility method for deleting obsolete records. Records are defined by a timestamp.
     *
     * @param table table name
     * @param timeStamp timestamp
     */
    private void deleteObsoleteRecords(String table, long timeStamp){
        String[] args = {""+timeStamp,ID};
        String timeClause = "(time_stamp IS NULL OR time_stamp<?) AND db_guid=?";
        int counter = sqLiteDatabase.delete(table,timeClause,args);
        if (counter > 0) utils.debug("Obsoletes removed; "+table+": "+counter);
    }

    /**
     * Utility method to mark goods as inactive. All items with timestamp less, than given,
     * will be marked as inactive
     *
     * @param timeStamp last successful update time
     */
    private void markGoodsInactive(long timeStamp){
        String[] args = {""+timeStamp,ID};
        String timeClause = "(time_stamp IS NULL OR time_stamp<?) AND db_guid=?";
        ContentValues values = new ContentValues();
        values.put("is_active",0);
        int counter = sqLiteDatabase.update("goods",values,timeClause,args);
        if (counter > 0) utils.debug("Goods marked inactive: "+counter);
    }

    /**
     * Delete records with obsolete timestamp.
     *
     * @param arrayList array with a data set element, that contains current timestamp
     */
    private void onUpdateEnd(ArrayList<DataBaseItem> arrayList){
        if (arrayList.size() == 0) return;

        long timeStamp = arrayList.get(0).getLong(Constants.DATA_TIME_STAMP);
        if (timeStamp == 0) return;

        String[] tables = {"goods_price","clients","clients_directions","debts","clients_goods"};
        for (String table: tables){
            deleteObsoleteRecords(table,timeStamp);
        }

        markGoodsInactive(timeStamp);
        deleteObsoleteImages();
        appSettings.sendLog();
    }

    /**
     * Special method for remote data deletion
     */
    public void eraseData(){
        String[] args = {ID};
        String[] tables = {"goods","goods_price","clients","clients_directions","debts","clients_goods","orders","documents"};
        for (String table: tables){
            int counter = sqLiteDatabase.delete(table,"db_guid=?",args);
            utils.debug("deleted records in "+table+": "+counter);
        }
        appSettings.sendLog();
    }

    /**
     * Convenient way to save an array of a single-type data sets.
     * Data type is determined by the first element of the array
     *
     * @param arrayList array of data sets
     */
    public void saveDataItemsSet(ArrayList<DataBaseItem> arrayList){
        if (arrayList.size() == 0) return;
        String type = arrayList.get(0).type();
        switch (type){
            case Constants.DATA_GOODS_ITEM: bulkSaveGoodsItems(arrayList); break;
            case Constants.DATA_CLIENT: bulkSaveClients(arrayList); break;
            case Constants.DATA_PRICE: bulkSavePrice(arrayList); break;
            case Constants.DATA_COMPETITOR_PRICE: bulkSaveCompetitorPrice(arrayList); break;
            case Constants.DATA_DEBT: bulkSaveDebts(arrayList); break;
            case Constants.DATA_DEBT_DOCUMENT: bulkSaveDebtsContent(arrayList); break;
            case Constants.DATA_IMAGE: bulkSaveImages(arrayList); break;
            case Constants.DATA_CLIENT_LOCATION: bulkSaveClientsLocations(arrayList); break;
            case Constants.DATA_CLIENT_DIRECTION: bulkSaveClientsDirections(arrayList); break;
            case Constants.DATA_CLIENT_GOODS: bulkSaveClientsGoodsItems(arrayList); break;
            case Constants.DATA_DOCUMENT_SENDING_RESULT: bulkSaveLoadResponse(arrayList); break;
            case Constants.DATA_TIME_STAMP: onUpdateEnd(arrayList); break;
            case Constants.DATA_PRINT: break;
            default: utils.warn("Missed database saver for a type: "+type);
        }
    }

    /**
     * Convenient way to get the name of a price type
     *
     * @param code code of price type
     * @return price name
     */
    public String priceTypeName(String code){
        String priceName = DEFAULT_VALUE;
        String[] args = {code,ID};
        Cursor cursor = sqLiteDatabase.query("price_types",null,"code=? AND db_guid=?",args,null,null,null);
        if (cursor.moveToFirst()){
            int i = cursor.getColumnIndex("description");
            if (i>=0) priceName = cursor.getString(i);
        }
        cursor.close();
        return priceName;
    }

    /**
     * Convenient way to get the price type code by its name
     *
     * @param name description of price type
     * @return price type code
     */
    public int priceTypeCode(String name){
        int code=0;
        String[] args = {name,ID};
        String[] columns = {"code"};
        Cursor cursor = sqLiteDatabase.query("price_types",columns,"description=? AND db_guid=?",args,null,null,null);
        if (cursor.moveToFirst()){
            code = cursor.getInt(0);
        }
        cursor.close();
        return code;
    }

    public int priceTypeCode(int index){
        int priceCode = 0;
        String priceName = getPriceTypes()[index];
        Cursor cursor = sqLiteDatabase.query("price_types",null,"description='"+priceName+"' AND db_guid='"+ID+"'",null,null,null,null);
        if (cursor.moveToFirst()){
            int i = cursor.getColumnIndex("code");
            if (i>=0) priceCode = cursor.getInt(i);
        }
        cursor.close();
        return priceCode;
    }

    /**
     * Migration to a multi-connections system
     */
    public void updateDataWithConnectionID() {
        try {
            sqLiteDatabase.beginTransaction();
            sqLiteDatabase.execSQL("UPDATE goods SET db_guid='"+ID+"'");
            sqLiteDatabase.execSQL("UPDATE clients SET db_guid='"+ID+"'");
            sqLiteDatabase.execSQL("UPDATE goods_price SET db_guid='"+ID+"'");
            sqLiteDatabase.execSQL("UPDATE price_types SET db_guid='"+ID+"'");
            sqLiteDatabase.execSQL("UPDATE debts SET db_guid='"+ID+"'");
            sqLiteDatabase.execSQL("UPDATE documents SET db_guid='"+ID+"'");
            sqLiteDatabase.execSQL("UPDATE orders SET db_guid='"+ID+"'");
            sqLiteDatabase.setTransactionSuccessful();
        }catch (Exception ex) {
            utils.error("updateDataWithConnectionID: " + ex);
        }finally {
            sqLiteDatabase.endTransaction();
        }
    }

    /**
     * Get set of all images parameters for the current connection
     *
     * @return array of images parameters
     */
    public ArrayList<DataBaseItem> getImages(){
        ArrayList<DataBaseItem> list = new ArrayList<>();
        String[] args = {ID};
        String[] columns = {"image_guid","url","time","is_default","type"};
        Cursor cursor = sqLiteDatabase.query("images",columns,"db_guid=?",args,null,null,null);
        //trying to catch SQLiteBlobTooBigException
        try {
            while (cursor.moveToNext()){
                DataBaseItem dataBaseItem = new DataBaseItem(cursor);
                list.add(dataBaseItem);
            }
        }catch (Exception e){
            utils.error("getImages: "+e);
        }
        cursor.close();
        return list;
    }

    /**
     * Get a single image properties
     *
     * @param imageGuid image GUID
     * @return data set of parameters
     */
    public DataBaseItem getImage(String imageGuid){
        DataBaseItem dataBaseItem = new DataBaseItem();
        if (imageGuid == null || imageGuid.isEmpty()) return dataBaseItem;
        String[] args = {imageGuid,ID};
        Cursor cursor = sqLiteDatabase.query("images",null,"image_guid=? AND db_guid=?",args,null,null,null);
        try {
            if (cursor.moveToFirst()) dataBaseItem = new DataBaseItem(cursor);
        }catch (Exception e){
            utils.error("getImage: "+e);
        }
        cursor.close();
        return dataBaseItem;
    }

    /**
     * Get client data with some additional info:
     *  group name, balance value, coordinates
     *
     * @param guid client GUID
     * @return data set
     */
    public DataBaseItem getClient (String guid) {
        DataBaseItem clientData = new DataBaseItem();
        if (guid == null || guid.isEmpty()) return clientData;
        String queryText =
                "SELECT " +
                        "clients._id as _id, " +
                        "clients.description as description, " +
                        "clients.guid as guid, " +
                        "groups.description as groupName, " +
                        "clients.group_guid as group_guid, " +
                        "clients.code1 as code1, " +
                        "clients.code2 as code2, " +
                        "clients.is_group as is_group, " +
                        "clients.discount as discount, " +
                        "clients.bonus as bonus, " +
                        "clients.price_type as price_type, " +
                        "clients.address as address, " +
                        "clients.phone as phone, " +
                        "clients.notes as notes, " +
                        "clients.is_banned as is_banned, " +
                        "clients.ban_message as ban_message, " +
                        "debts.sum as debt, " +
                        "location.latitude as latitude, " +
                        "location.longitude as longitude " +
                        "FROM clients " +
                        "LEFT OUTER JOIN (SELECT * FROM debts WHERE client_guid='"+guid+"' AND db_guid='"+ID+"') AS debts " +
                        "on clients.guid=debts.client_guid "+
                        "LEFT OUTER JOIN (SELECT guid,description FROM clients WHERE is_group=1 AND db_guid='"+ID+"') AS groups "+
                        "ON clients.group_guid=groups.guid "+
                        "LEFT OUTER JOIN (SELECT * FROM clients_locations WHERE db_guid='"+ID+"' AND client_guid='"+guid+"') AS location "+
                        "ON clients.guid=location.client_guid "+
                        "WHERE clients.guid='"+guid+"' AND clients.db_guid='"+ID+"'";
        Cursor cursor = sqLiteDatabase.rawQuery(queryText,null);
        if (cursor.moveToFirst()) clientData = new DataBaseItem(cursor);
        cursor.close();
        return clientData;
    }

    /**
     * Get balance for a given client
     *
     * @param clientGUID client GUID
     * @return balance sum
     */
    public Double getClientTotalDebt(String clientGUID) {
        double sum = 0.0;
        String queryText =
                "SELECT * FROM debts WHERE client_guid='"+clientGUID+"' AND doc_id='' AND db_guid='"+ID+"'";
        Cursor cursor = sqLiteDatabase.rawQuery(queryText,null);
        if (cursor.moveToFirst()) {
            int i = cursor.getColumnIndex("sum");
            if (i >= 0) sum = cursor.getDouble(i);
        }
        cursor.close();
        return sum;
    }

    /**
     * Get a single document data
     *
     * @param doc_guid document GUID
     * @return data set
     */
    public DataBaseItem getDebtDocument(String doc_guid){
        DataBaseItem data = new DataBaseItem();
        String[] args = {doc_guid,ID};
        String queryText =
                "SELECT * FROM debts WHERE doc_guid=? AND db_guid=?";
        Cursor cursor = sqLiteDatabase.rawQuery(queryText,args);
        if (cursor != null){
            if (cursor.moveToNext()) data = new DataBaseItem(cursor);
            cursor.close();
        }
        return data;
    }

    /**
     * Get list of debt documents for the given client GUID
     *
     * @param clientGUID client GUID
     * @return set of documents data
     */
    public ArrayList<DataBaseItem> getDebtDocuments(String clientGUID){
        ArrayList<DataBaseItem> arrayList = new ArrayList<>();
        String[] args = {clientGUID,ID};
        String queryText =
                "SELECT * FROM debts WHERE client_guid=? AND doc_id<>'' AND db_guid=? ORDER BY sorting DESC";
        Cursor cursor = sqLiteDatabase.rawQuery(queryText,args);
        if (cursor != null){
            while (cursor.moveToNext()){
                DataBaseItem item = new DataBaseItem(cursor);
                arrayList.add(item);
            }
            cursor.close();
        }
        return arrayList;
    }

    public DataBaseItem getDocumentByGUID(String guid){
        DataBaseItem item;
        String queryText =
                "SELECT * FROM documents WHERE guid='"+guid+"'";
        Cursor cursor = sqLiteDatabase.rawQuery(queryText,null);
        if (cursor.moveToFirst()) {
            item = new DataBaseItem(cursor);
        }else {
            item = new DataBaseItem(new ContentValues());
        }
        cursor.close();
        return item;
    }

    public int getNewDocumentNumber() {
        int newNumber = 0;
        Cursor cursor = sqLiteDatabase.query("documents",null,"db_guid='"+ID+"'",null,null,null,"number");
        if (cursor.moveToLast()) {
            int i = cursor.getColumnIndex("number");
            if (i >= 0) newNumber = cursor.getInt(i);
        }
        cursor.close();
        newNumber++;
        return newNumber;
    }

    public String readAttributeValue(String guid, String attribute_guid){
        String value="";
        String queryText =
                "SELECT attr_value FROM document_attributes " +
                        "WHERE doc_guid='"+guid+"' AND attr_guid='"+attribute_guid+"'";
        Cursor cursor = sqLiteDatabase.rawQuery(queryText, null);
        if (cursor.moveToFirst()){
            int i = cursor.getColumnIndex("attr_value");
            if (i >= 0) value = cursor.getString(i);
        }
        cursor.close();
        return value;
    }

    public String updateDocument(String guid, ContentValues cv) {
        String docTable = "";
        if (cv.containsKey("type")){
            switch (cv.getAsString("type")){
                case "order":
                    docTable = "orders";
                    cv.remove("type");
                    break;
                case "cash":
                    docTable = "documents";
                    break;
            }
        }
        if (docTable.isEmpty()) return "";

        if (!cv.containsKey("db_guid")) cv.put("db_guid", ID);
        if (guid.isEmpty()) guid = UUID.randomUUID().toString();
        cv.put("guid", guid);
        String[] args = {guid};

        try{
            if (sqLiteDatabase.update(docTable,cv,"guid=?",args) == 0){
                sqLiteDatabase.insertOrThrow(docTable,null,cv);
            }
        }catch (SQLiteException e){
            utils.error("Update document: "+e);
            guid = "";
        }

        return guid;
    }

    public void deleteDocument(String guid){
        sqLiteDatabase.delete("documents","guid='"+guid+"'",null);
        sqLiteDatabase.delete("document_content","doc_guid='"+guid+"'",null);
        sqLiteDatabase.delete("document_attributes","doc_guid='"+guid+"'",null);
    }

    public void saveAttributeValue(String guid, String attribute_guid, String value){

        sqLiteDatabase.delete("document_attributes","doc_guid='"+guid+"' AND attr_guid='"+attribute_guid+"'",null);

        ContentValues contentValues = new ContentValues();
        contentValues.put("doc_guid", guid);
        contentValues.put("attr_guid", attribute_guid);
        contentValues.put("attr_value", value);

        try {
            sqLiteDatabase.insertOrThrow("document_attributes", null, contentValues);
        } catch (SQLiteException e) {
            utils.error("update document attribute: " + e);
        }
    }

    public int getNewOrderNumber() {
        int newNumber = 0;
        Cursor cursor = sqLiteDatabase.query("orders",null,"db_guid='"+ID+"'",null,null,null,"number");
        if (cursor.moveToLast()) {
            int i = cursor.getColumnIndex("number");
            if (i >= 0) newNumber = cursor.getInt(i);
        }
        cursor.close();
        newNumber++;
        return newNumber;
    }

    public DataBaseItem getOrder(long id) {
        DataBaseItem order = new DataBaseItem();
        Cursor cursor = sqLiteDatabase.query("orders",null,"_id="+id,null,null,null,null);
        if (cursor.moveToFirst()) order = new DataBaseItem(cursor);
        cursor.close();
        return order;
    }

    public DataBaseItem getOrder(String guid) {
        DataBaseItem order = new DataBaseItem();
        String[] args = {guid,ID};
        Cursor cursor = sqLiteDatabase.query("orders",null,"guid=? AND db_guid=?",args,null,null,null);
        if (cursor.moveToFirst()) order = new DataBaseItem(cursor);
        cursor.close();
        return order;
    }

    public long addOrder(ContentValues cv){
        try{
            return sqLiteDatabase.insertOrThrow("orders",null,cv);
        }catch (SQLiteException e){
            utils.error("addOrder: "+e);
            return -1;
        }
    }

    public void deleteOrder (long id) {
        ArrayList<DataBaseItem> content = getOrderContent(id);
        if (content.size() > 0) {
            sqLiteDatabase.delete("orders_content","order_id="+id,null);
        }

        DataBaseItem orderData = getOrder(id);
        if (orderData.hasValues()) {
            sqLiteDatabase.delete("orders","_id="+id,null);
        }
    }

    public ArrayList<DataBaseItem> getOrderContent (long orderID) {
        ArrayList<DataBaseItem> content = new ArrayList<>();
        String[] args = {ID,""+orderID,ID};
        String queryText =
                "SELECT " +
                        "orders_content._id as _id, " +
                        "orders_content.item_description as description, " +
                        "orders_content.item_guid as item_guid, " +
                        "goods.code1 as code1, " +
                        "goods.code2 as code2, " +
                        "goods.min_price as min_price, " +
                        "groups.description as groupName, " +
                        "orders_content.unit_code as unit_code, " +
                        "orders_content.quantity as quantity, " +
                        "orders_content.price as price, " +
                        "orders_content.sum as sum, " +
                        "orders_content.sum_discount as sum_discount, " +
                        "orders_content.is_packed as is_packed, " +
                        "orders_content.is_demand as is_demand " +
                        "FROM orders_content " +
                        "LEFT OUTER JOIN goods " +
                        "on goods.guid = orders_content.item_guid " +
                        "LEFT OUTER JOIN (SELECT guid,description FROM goods WHERE is_group=1 AND db_guid=?) AS groups "+
                        "ON goods.group_guid = groups.guid "+
                        "WHERE orders_content.order_id=? AND orders_content.order_id>0 AND goods.db_guid=? "+
                        "ORDER BY orders_content._id ";
        Cursor cursor = sqLiteDatabase.rawQuery(queryText,args);
        while (cursor.moveToNext()){
            content.add(new DataBaseItem(cursor));
        }
        cursor.close();
        return content;
    }

    public ArrayList<DataBaseItem> getOrderPrevious (String client_guid, long orderID) {
        ArrayList<DataBaseItem> content = new ArrayList<>();
        String queryText;
        // 1 - define current order time
        long currentOrderTime=0;
        queryText = "SELECT time FROM orders WHERE _id="+orderID;
        Cursor currentOrderCursor = sqLiteDatabase.rawQuery(queryText,null);
        while (currentOrderCursor.moveToNext()){
            int i = currentOrderCursor.getColumnIndex("time");
            if (i >= 0) currentOrderTime = currentOrderCursor.getLong(i);
        }
        currentOrderCursor.close();
        if (currentOrderTime == 0) {
            currentOrderTime = utils.dateBeginOfToday();
        }else {
            currentOrderTime = utils.dateBeginOfDay(currentOrderTime);
        }

        // 2 - select previous order, to define its time
        long previousOrderTime=0;
        queryText = "SELECT time FROM orders " +
                "WHERE client_guid='"+client_guid+"' " +
                "AND is_return=0 AND time<"+currentOrderTime+" AND is_processed>0 AND db_guid='"+ID+"' "+
                "ORDER BY number DESC LIMIT 1";
        Cursor cursorOrders = sqLiteDatabase.rawQuery(queryText,null);
        while (cursorOrders.moveToNext()){
            int i = cursorOrders.getColumnIndex("time");
            if (i >= 0) previousOrderTime = cursorOrders.getLong(i);
        }
        cursorOrders.close();

        // 3 - select content by date
        long dateBegin = utils.dateBeginOfDay(previousOrderTime);
        long dateEnd = dateBegin + 86399;
        queryText =
                "SELECT " +
                        "orders_content._id as _id, " +
                        "orders_content.item_description as description, " +
                        "goods.code1 as code1, " +
                        "goods.code2 as code2, " +
                        "goods.guid as item_guid, " +
                        "goods.min_price as min_price, " +
                        "groups.description as groupName, " +
                        "orders_content.unit_code as unit_code, " +
                        "orders_content.quantity as quantity, " +
                        "orders_content.price as price, " +
                        "orders_content.sum as sum, " +
                        "orders_content.sum_discount as sum_discount, " +
                        "orders_content.is_packed as is_packed, " +
                        "orders_content.is_demand as is_demand " +
                        "FROM orders_content " +
                        "LEFT OUTER JOIN goods " +
                        "on goods.guid = orders_content.item_guid " +
                        "LEFT OUTER JOIN (SELECT guid,description FROM goods WHERE is_group=1) AS groups "+
                        "ON goods.group_guid = groups.guid "+
                        "WHERE goods.db_guid='"+ID+"' "+
                        " AND orders_content.order_id IN" +
                        " (SELECT _id FROM orders " +
                        "WHERE time>="+dateBegin+" AND time<="+dateEnd+" " +
                        "AND client_guid='"+client_guid+"' "+
                        "AND is_return=0 AND is_processed>0 AND db_guid='"+ID+"') "+
                        "ORDER BY orders_content._id ";
        Cursor cursor = sqLiteDatabase.rawQuery(queryText,null);
        while (cursor.moveToNext()){
            content.add(new DataBaseItem(cursor));
        }
        cursor.close();
        return content;
    }

    public double getOrderTotalPrice (long orderID) {
        if (orderID == 0) return 0.0;
        String queryText =
                "SELECT " +
                        "orders_content.order_id as order_id, " +
                        "sum(orders_content.sum) as sum_total " +
                        "FROM orders_content " +
                        "WHERE orders_content.order_id = " +orderID+" "+
                        "GROUP BY orders_content.order_id ";
        Cursor cursor = sqLiteDatabase.rawQuery(queryText,null);
        double result = 0;
        if (cursor.moveToFirst()) {
            int i = cursor.getColumnIndex("sum_total");
            if (i >= 0) result = cursor.getDouble(i);
        }
        cursor.close();
        return result;
    }

    public double getOrderTotalDiscount (long orderID) {
        if (orderID == 0) return 0.0;
        String queryText =
                "SELECT " +
                        "orders_content.order_id as order_id, " +
                        "sum(orders_content.sum_discount) as sum_total " +
                        "FROM orders_content " +
                        "WHERE orders_content.order_id = " +orderID+" "+
                        "GROUP BY orders_content.order_id ";
        Cursor cursor = sqLiteDatabase.rawQuery(queryText,null);
        double result = 0;
        if (cursor.moveToFirst()) {
            int i = cursor.getColumnIndex("sum_total");
            if (i >= 0) result = cursor.getDouble(i);
        }
        cursor.close();
        return result;
    }

    public double getOrderTotalWeight (long orderID) {
        if (orderID == 0) return 0.0;
        String queryText =
                "SELECT " +
                        "orders_content.order_id as order_id, " +
                        "sum(orders_content.weight) as weight " +
                        "FROM orders_content " +
                        "WHERE orders_content.order_id = " +orderID+" "+
                        "GROUP BY orders_content.order_id ";
        Cursor cursor = sqLiteDatabase.rawQuery(queryText,null);
        double result = 0;
        if (cursor.moveToFirst()) {
            int i = cursor.getColumnIndex("weight");
            if (i >= 0) result = cursor.getDouble(i);
        }
        cursor.close();
        return result;
    }

    public double getOrderTotalQuantity (long orderID) {
        if (orderID == 0) return 0.0;
        String queryText =
                "SELECT " +
                        "orders_content.order_id as order_id, " +
                        "sum(orders_content.quantity) as quantity_total " +
                        "FROM orders_content " +
                        "WHERE orders_content.order_id = " +orderID+" "+
                        "GROUP BY orders_content.order_id ";
        Cursor cursor = sqLiteDatabase.rawQuery(queryText,null);
        double result = 0;
        if (cursor.moveToFirst()) {
            int i = cursor.getColumnIndex("quantity_total");
            if (i >= 0) result = cursor.getDouble(i);
        }
        cursor.close();
        return result;
    }

    public void setGoodsItemInOrder(DataBaseItem props) {

        long orderID = props.getLong("orderID");
        String itemID = props.getString("itemID");
        String unit = props.getString("unit");
        double price = props.getDouble("price");
        double quantity = props.getDouble("quantity");
        double discount = props.getDouble("discount");
        boolean isPacked = props.getBoolean("isPacked");
        boolean isDemand = props.getBoolean("isDemand");

        DataBaseItem document = getOrder(orderID);
        DataBaseItem goodsItem = getGoodsItem(itemID, orderID);

        //set default document's price if its not set et
        if (price == 0){
            String priceType = document.getString("price_type");
            if (!priceType.isEmpty() && !priceType.equals("0")){
                double priceByType = getItemPrice(itemID,priceType);
                if (priceByType >= 0) price = priceByType;
            }
        }

        quantity = utils.round(quantity,3);
        if (quantity < 0) {quantity=0.0;}

        double weight = quantity * goodsItem.getDouble("weight");
        double sum = utils.round(quantity * price, 2);

        double discountValue = 0;
        if (discount > 0){
            double priceWithDiscount = price - utils.round(price * discount/100,2);
            priceWithDiscount = utils.max(priceWithDiscount,goodsItem.getDouble(",min_price"));
            sum = utils.round(priceWithDiscount * quantity, 2);
            discountValue = price*quantity-sum;
        }

        int is_packed = isPacked ? 1:0;
        int is_demand = isDemand ? 1:0;

        ContentValues cv = new ContentValues();
        cv.put("item_id",           goodsItem.getLong("raw_id"));
        cv.put("item_guid",         itemID);
        cv.put("item_description",  goodsItem.getString("description"));
        cv.put("unit_code",         unit);
        cv.put("quantity",          quantity);
        cv.put("weight",            weight);
        cv.put("price",             price);
        cv.put("sum",               sum);
        cv.put("sum_discount",      discountValue);
        cv.put("is_packed",         is_packed);
        cv.put("is_demand",         is_demand);

        String[] args = {""+orderID,itemID};
        int updated = sqLiteDatabase.update("orders_content", cv, "order_id=? AND item_guid=?", args);
        if (updated == 0){
            cv.put("order_id",orderID);
            sqLiteDatabase.insert("orders_content",null,cv);
        }
    }

    public void toggleIsPackedFlag(long orderID, String itemID) {

        DataBaseItem goodsItem = getGoodsItem(itemID, orderID);
        int isPacked;

        if (goodsItem.getInt("is_packed") == 0) {
            isPacked = 1;
        }else{
            isPacked = 0;
        }

        ContentValues cv = new ContentValues();
        cv.put("item_guid",         itemID);
        cv.put("item_description",  goodsItem.getString("description"));
        cv.put("is_packed",         isPacked);

        String[] args = {""+orderID,itemID};
        int updated = sqLiteDatabase.update("orders_content", cv, "order_id=? AND item_guid=?", args);
        if (updated == 0){
            cv.put("order_id",orderID);
            sqLiteDatabase.insert("orders_content",null,cv);
        }

    }

    public void recalcOrderContentDiscount(long orderID, double discount,int priceType){
        if (orderID == 0) return;
        long recordID;
        double quantity;
        double price;
        double sum;
        double discountValue=0;
        ContentValues cv = new ContentValues();

        ArrayList<DataBaseItem> content = getOrderContent(orderID);
        for (DataBaseItem item: content){

            recordID = item.getLong("raw_id");
            quantity = item.getDouble("quantity");
            price = item.getDouble("price");
            if (priceType != 0){
                String itemGUID = item.getString("item_guid");
                double priceByType = getItemPrice(itemGUID,""+priceType);
                if (priceByType >= 0){
                    price = priceByType;
                }
            }
            sum = utils.round(quantity * price, 2);

            if (discount>0){
                double priceWithDiscount = utils.round(price * (1-discount/100), 2);
                double priceMin = item.getDouble("min_price");
                priceWithDiscount = utils.max(priceWithDiscount,priceMin);
                sum = utils.round(quantity * priceWithDiscount, 2);
                discountValue = price*quantity - sum;
            }

            cv.clear();
            cv.put("quantity",          quantity);
            cv.put("price",             price);
            cv.put("sum",               sum);
            cv.put("sum_discount",      discountValue);
            sqLiteDatabase.update("orders_content", cv, "_id="+recordID, null);
        }
    }

    public ArrayList<String> getGroupsInOrderId (long orderID) {
        ArrayList<String> groups = new ArrayList<>();
        String queryText =
                "SELECT " +
                        "goods.group_guid as group_guid, " +
                        "sum(content.quantity) as quantity " +
                        "FROM goods " +
                        "LEFT OUTER JOIN (SELECT * FROM orders_content WHERE order_id = " + orderID + ") AS content " +
                        "ON goods.guid = content.item_guid " +
                        "WHERE goods.db_guid='"+ID+"' "+
                        "GROUP BY goods.group_guid ";
        Cursor cursor = sqLiteDatabase.rawQuery(queryText,null);
        double quantity = 0;
        while (cursor.moveToNext()){
            int i = cursor.getColumnIndex("quantity");
            if (i >= 0) quantity = cursor.getDouble(i);
            if (quantity > 0){
                i = cursor.getColumnIndex("group_guid");
                if (i >= 0) groups.add(cursor.getString(i));
            }
        }
        cursor.close();
        return groups;
    }

    public boolean updateOrder(long orderID, ContentValues cv) {
        try {
            sqLiteDatabase.update("orders", cv, "_id="+orderID, null);
            sqLiteDatabase.delete("orders_content","quantity<=0.0 AND order_id="+orderID,null);
            return true;
        }catch (SQLiteException ex){
            utils.error("updateOrder: "+ex);
            return false;
        }
    }

    /**
     * Generate an JSON object with clients locations data for sending to the server.
     *
     * @return clients data - coordinates, description
     */
    public JSONObject getClientsLocationsForUpload(){
        JSONObject locations = new JSONObject();
        String[] args = {ID,ID};
        String query = "SELECT " +
                "location.client_guid AS client_guid," +
                "location.latitude AS latitude," +
                "location.longitude AS longitude," +
                "client.code2 AS client_id " +
                "FROM clients_locations AS location " +
                "LEFT OUTER JOIN (SELECT code2,guid FROM clients WHERE db_guid=?) AS client " +
                "ON location.client_guid=client.guid " +
                "WHERE location.db_guid=? AND location.modified=1";
        try (Cursor cursor = sqLiteDatabase.rawQuery(query, args)) {
            locations.put("type", "clients_locations");
            JSONArray data = new JSONArray();
            while (cursor.moveToNext()) {
                DataBaseItem dataBaseItem = new DataBaseItem(cursor);
                data.put(dataBaseItem.getAsJSON());
            }
            locations.put("data", data);
            if (data.length() == 0) locations = null;
        } catch (Exception e) {
            utils.error("getClientsLocationsForUpload: " + e);
            locations = null;
        }
        return locations;
    }

    /**
     * Mark all clients locations records as saved, non-modified, after
     * receiving the response from server
     */
    private void markClientLocationsSaved(){
        String[] args = {ID};
        ContentValues cv = new ContentValues();
        cv.put("modified",0);
        sqLiteDatabase.update("clients_locations",cv,"db_guid=?",args);
    }

    /**
     * Mark all records as sent after receiving the server's response
     */
    private void markCompetitorPricesSent(){
        String[] args = {ID};
        ContentValues cv = new ContentValues();
        cv.put("time_stamp",0);
        sqLiteDatabase.update("competitor_price",cv,"db_guid=?",args);
    }

    /**
     * Data set of clients locations for Clients locations activity.
     *
     * @param group_guid group filter
     * @return clients data - coordinates, description, order number
     */
    public ArrayList<DataBaseItem> getClientsLocations(String group_guid){
        ArrayList<DataBaseItem> list = new ArrayList<>();
        String time = ""+utils.dateBeginOfToday();
        String[] args;
        String query;
        if (group_guid == null) {
            args = new String[]{ID,ID,time,ID};
            query =
                    "SELECT " +
                            "clients.description AS description," +
                            "clients.address AS address," +
                            "clients.guid AS guid," +
                            "location.latitude AS latitude," +
                            "location.longitude AS longitude, " +
                            "orders.number AS number " +
                            "FROM clients " +
                            "LEFT OUTER JOIN (SELECT * FROM clients_locations WHERE db_guid=? AND latitude<>0 AND longitude<>0) AS location " +
                            "ON clients.guid=location.client_guid " +
                            "LEFT OUTER JOIN (SELECT number,client_guid FROM orders WHERE db_guid=? AND is_processed>0 AND time>=?) AS orders " +
                            "ON clients.guid=orders.client_guid " +
                            "WHERE clients.db_guid=?";
        }else {
            args = new String[]{ID,ID,time,ID,group_guid};
            query =
                    "SELECT " +
                            "clients.description AS description," +
                            "clients.address AS address," +
                            "clients.guid AS guid," +
                            "location.latitude AS latitude," +
                            "location.longitude AS longitude, " +
                            "orders.number AS number " +
                            "FROM clients " +
                            "LEFT OUTER JOIN (SELECT * FROM clients_locations WHERE db_guid=? AND latitude<>0 AND longitude<>0) AS location " +
                            "ON clients.guid=location.client_guid " +
                            "LEFT OUTER JOIN (SELECT number,client_guid FROM orders WHERE db_guid=? AND is_processed>0 AND time>=?) AS orders " +
                            "ON clients.guid=orders.client_guid " +
                            "WHERE clients.db_guid=? AND clients.group_guid=?";
        }
        Cursor cursor = sqLiteDatabase.rawQuery(query,args);
        while (cursor.moveToNext()){
            list.add(new DataBaseItem(cursor));
        }
        cursor.close();
        return list;
    }

    /**
     * Generate an JSON object with locations data for sending to the server.
     *
     * @param fromTime timestamp for data selection filter
     * @return JSON object
     */
    public JSONObject getLocationsForUpload(long fromTime){
        JSONObject locations = new JSONObject();
        Cursor cursor = sqLiteDatabase.query("locations",null,"time>"+fromTime,null,null,null,"time ASC");
        try {
            locations.put("type","location");
            JSONArray data = new JSONArray();
            while (cursor.moveToNext()){
                DataBaseItem dataBaseItem = new DataBaseItem(cursor);
                if (!licensed) {
                    dataBaseItem.put("latitude",0.0);
                    dataBaseItem.put("longitude",0.0);
                }
                data.put(dataBaseItem.getAsJSON());
            }
            locations.put("data",data);
            if (data.length() == 0) locations = null;
        }catch (Exception e){
            utils.warn("getLocationsForUpload: "+e);
            locations = null;
        }
        cursor.close();
        return locations;
    }

    /**
     * Last record of locations data table.
     *
     * @return data set
     */
    public DataBaseItem lastSavedLocationData(){
        DataBaseItem dataBaseItem = new DataBaseItem();
        Cursor cursor = sqLiteDatabase.query("locations",null,null,null,null,null,"time DESC");
        if (cursor.moveToFirst()) dataBaseItem = new DataBaseItem(cursor);
        cursor.close();
        return dataBaseItem;
    }

    public Location lastSavedLocation(){
        Location location = new Location("fused");
        Cursor cursor = sqLiteDatabase.query("locations",null,null,null,null,null,"time DESC");
        if (cursor.moveToFirst()) {
            DataBaseItem copy = new DataBaseItem(cursor);
            location.setAltitude(copy.getDouble("altitude"));
            location.setLatitude(copy.getDouble("latitude"));
            location.setLongitude(copy.getDouble("longitude"));
        }else {
            location = null;
        }
        cursor.close();
        return location;
    }

    public void saveLocation(Location location, double distance){
        if (location == null) return;
        ContentValues contentValues = new ContentValues();
        contentValues.put("time",location.getTime());
        contentValues.put("accuracy",location.getAccuracy());
        contentValues.put("altitude",location.getAltitude());
        contentValues.put("bearing",location.getBearing());
        contentValues.put("latitude",location.getLatitude());
        contentValues.put("longitude",location.getLongitude());
        contentValues.put("provider",location.getProvider());
        contentValues.put("speed",location.getSpeed());
        contentValues.put("distance",distance);
        contentValues.put("point_name","");
        contentValues.put("extra","");
        try {
            sqLiteDatabase.insertOrThrow("locations", null, contentValues);
        }catch (Exception e){
            utils.warn("Save location: "+e);
        }
    }

    /**
     * Update clients location record with given coordinates.
     *
     * @param guid client GUID
     * @param position coordinates
     */
    public void saveClientLocation(String guid, LatLng position){
        String where = "db_guid=? AND client_guid=?";
        String[] args = {ID,guid};

        ContentValues contentValues = new ContentValues();
        contentValues.put("db_guid",ID);
        contentValues.put("client_guid",guid);
        contentValues.put("latitude",position.latitude);
        contentValues.put("longitude",position.longitude);
        contentValues.put("modified",1);

        int updated = sqLiteDatabase.update("clients_locations", contentValues, where, args);
        if (updated == 0) sqLiteDatabase.insertOrThrow("clients_locations", null, contentValues);
    }

    /**
     * Read a task data from database by task GUID
     *
     * @param guid task GUID
     * @return dataset
     */
    public DataBaseItem getTaskData(String guid){
        DataBaseItem dataBaseItem = new DataBaseItem(Constants.DOCUMENT_TASK);
        if (guid == null || guid.isEmpty()) return dataBaseItem;
        String[] args ={ID,guid};
        Cursor cursor = sqLiteDatabase.query("tasks",null,"db_guid=? AND guid=?",args,null,null,null);
        if (cursor.moveToFirst()) dataBaseItem = new DataBaseItem(cursor);
        cursor.close();
        dataBaseItem.setType(Constants.DOCUMENT_TASK);
        return dataBaseItem;
    }

    /**
     * Save a task data into database; updates or inserts a new record
     *
     * @param guid task GUID
     * @param taskData dataset to be stored
     */
    public void saveTask(String guid, DataBaseItem taskData){
        String[] args = {ID,guid};
        ContentValues contentValues = taskData.getValuesForDataBase();
        contentValues.put("db_guid",ID);

        int updated = sqLiteDatabase.update("tasks", contentValues, "db_guid=? AND guid=?", args);
        if (updated == 0) sqLiteDatabase.insertOrThrow("tasks", null, contentValues);
    }

    /**
     * Helper method to get all available value variants, stored in a single column
     * of the 'goods' table
     *
     * @param column column name
     * @param filterValue filter value
     * @return array of value variants
     */
    public ArrayList<String> getGoodsFilter(String column, String filterValue){
        ArrayList<String> filter = new ArrayList<>();
        String whereClause = "db_guid=?";
        String[] args;
        if (filterValue.isEmpty()) {
            args = new String[]{ID};
        }else{
            whereClause = whereClause+" AND "+column+"=?";
            args = new String[]{ID, filterValue};
        }
        String[] columns = {column};
        Cursor cursor = sqLiteDatabase.query("goods",columns,whereClause,args,column,null,column);
        while (cursor.moveToNext()){
            String value = cursor.getString(0);
            if (value != null && !value.isEmpty()) filter.add(value);
        }
        cursor.close();
        return filter;
    }

    /**
     * Helper method to get all available value variants, stored in a single column
     * of the 'clients_goods' table
     *
     * @param clientGUID client's GUID
     * @param column column name
     * @param filterColumn additional filter column name
     * @param filterValue filter value
     * @return array of value variants
     */
    public ArrayList<String> getClientsGoodsFilter(String clientGUID, String column, String filterColumn, String filterValue){
        ArrayList<String> filter = new ArrayList<>();
        String whereClause = "client_guid=? AND db_guid=?";
        String[] args;
        if (filterValue.isEmpty()) {
            args = new String[]{clientGUID, ID};
        }else{
            whereClause = whereClause+" AND "+filterColumn+"=?";
            args = new String[]{clientGUID, ID, filterValue};
        }
        String[] columns = {column};
        Cursor cursor = sqLiteDatabase.query("clients_goods",columns,whereClause,args,column,null,column);
        while (cursor.moveToNext()){
            String value = cursor.getString(0);
            if (value != null && !value.isEmpty()) filter.add(value);
        }
        cursor.close();
        return filter;
    }

    /**
     * Helper method to get all available value variants, stored in a single column
     * of the 'clients_goods' table with calculated quantity if goods in each variant.
     *
     * @param clientGUID client's GUID
     * @param column column name
     * @return array of value variants
     */
    public ArrayList<DataBaseItem> getClientsGoodsFilter(String clientGUID, String column){
        ArrayList<DataBaseItem> filter = new ArrayList<>();
        String whereClause = "client_guid=? AND db_guid=?";
        String[] args = new String[]{clientGUID, ID};
        String query =
                "SELECT " +
                        column + ","+
                        " COUNT(item_guid) AS item_count" +
                        " FROM clients_goods" +
                        " WHERE "+whereClause+
                        " GROUP BY "+column;
        Cursor cursor = sqLiteDatabase.rawQuery(query, args);
        while (cursor.moveToNext()){
            filter.add(new DataBaseItem(cursor));
        }
        cursor.close();
        return filter;
    }

    /**
     * Read array of goods rest types for current database
     *
     * @return array of rest types
     */
    public ArrayList<String> getRestTypes(){
        ArrayList<String> result = new ArrayList<>();
        String[] args = {ID};
        String[] columns = {"rest_type"};
        Cursor cursor = sqLiteDatabase.query("goods",columns,"db_guid=?",args,"rest_type",null,null);
        while (cursor.moveToNext()){
            result.add(cursor.getString(0));
        }
        cursor.close();
        return result;
    }
}

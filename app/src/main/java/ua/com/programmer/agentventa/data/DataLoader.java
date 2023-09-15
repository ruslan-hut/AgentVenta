package ua.com.programmer.agentventa.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;

import java.util.ArrayList;

import ua.com.programmer.agentventa.utility.Constants;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.settings.AppSettings;
import ua.com.programmer.agentventa.utility.Utils;

/**
 * Utility class to perform database queries for lists
 */
public class DataLoader {

    private static DataBase database;
    private static AppSettings appSettings;
    private static int requestIN;
    //private static int requestOUT;

    /**
     * SQL query to be executed
     */
    private String query;
    private String[] arguments;

    private final ArrayList<DataBaseItem> RESULT = new ArrayList<>();
    private final ArrayList<DataBaseItem> returnResult = new ArrayList<>();
    private final ArrayList<DataBaseItem> EMPTY = new ArrayList<>();

    private final Utils utils = new Utils();
    private final int noKeyElementLimit = 50;

    public interface DataLoaderListener{
        void onDataLoaded(ArrayList<DataBaseItem> items);
    }
    
    private final DataLoaderListener listener;
    
    public DataLoader(Context context, DataLoaderListener listener){
        this.listener = listener;
        database = DataBase.getInstance(context);
        appSettings = database.getAppSettings();
        requestIN = 0;
    }

    /**
     * Prepare string for use in an sql-query as a search filter
     *
     * @param someString some string value
     * @return clean string
     */
    private String cleanString(@NonNull String someString){
        String result = someString.replace("'","");
        result = result.replace("\"","");
        result = result.replace("%","");
        return result;
    }

    public void getGoodsItems(String group, String filter, int priceType, boolean flagRests){
        String ID = appSettings.getConnectionGUID();
        if (filter==null) filter="";

        String sortField = "sorting";
        if (appSettings.sortGoodsByName()){
            sortField = "description_lc";
        }
        if (!appSettings.keyIsActive()) sortField = sortField+" LIMIT "+noKeyElementLimit;

        String whereClause = "goods.is_active=1 AND goods.db_guid='"+ID+"'";
        if (!filter.equals("")) {
            filter = cleanString(filter);
            whereClause = whereClause + " AND (goods.description_lc LIKE '%" + filter + "%' " +
                    "OR goods.code1 LIKE '%" + filter + "%' " +
                    "OR goods.vendor_code LIKE '%" + filter + "%') " +
                    "AND goods.is_group = 0 ";
        }else{
            if (group != null) {
                whereClause = whereClause+" AND goods.group_guid='" + group + "' ";
            }else{
                whereClause = whereClause+" AND goods.group_guid IS NULL ";
            }
        }
        if (flagRests){
            whereClause = whereClause+" AND (goods.quantity>0 OR goods.is_group=1)";
        }
        if (priceType == 0) {
            query =
                    "SELECT " +
                            "goods._id as _id, " +
                            "goods.description as description, " +
                            "groups.description as groupName, " +
                            "goods.guid as guid, " +
                            "images.image_guid as image_guid, " +
                            "images.url as image_url, " +
                            "goods.code1 as code1, " +
                            "goods.code2 as code2, " +
                            "goods.vendor_code as vendor_code, " +
                            "goods.vendor_status as vendor_status, " +
                            "goods.quantity as quantity, " +
                            "goods.price as price, " +
                            "goods.min_price as min_price, " +
                            "goods.weight as weight, " +
                            "goods.unit as unit, " +
                            "goods.rest_type as rest_type, " +
                            "goods.package_value as package_value, " +
                            "goods.group_guid as group_guid, " +
                            "goods.is_group as is_group " +
                            "FROM goods " +
                            "LEFT OUTER JOIN (SELECT guid,description FROM goods WHERE is_group=1 AND db_guid='"+ID+"') AS groups "+
                            "ON goods.group_guid = groups.guid "+
                            "LEFT OUTER JOIN (SELECT item_guid,image_guid,url FROM images WHERE is_default=1 AND db_guid='"+ID+"') AS images "+
                            "ON goods.guid = images.item_guid "+
                            "WHERE "+whereClause+" "+
                            "ORDER BY is_group DESC, goods."+sortField;
        }else{
            query =

                    "SELECT " +
                            "goods._id as _id, " +
                            "goods.guid as guid, " +
                            "goods.description as description, " +
                            "groups.description as groupName, " +
                            "goods.group_guid as group_guid, " +
                            "goods.is_group as is_group, " +
                            "goods.code1 as code1, " +
                            "goods.code2 as code2, " +
                            "goods.vendor_code as vendor_code, " +
                            "goods.vendor_status as vendor_status, " +
                            "goods.quantity as quantity, " +
                            "goods.weight as weight, " +
                            "goods.unit as unit, " +
                            "goods.rest_type as rest_type, " +
                            "goods.package_value as package_value, " +
                            "goods.min_price as min_price, " +
                            "images.image_guid as image_guid, " +
                            "images.url as image_url, " +
                            "prices.price as price " +
                            "FROM goods " +
                            "LEFT OUTER JOIN (SELECT guid,description FROM goods WHERE is_group=1 AND db_guid='"+ID+"') AS groups "+
                            "ON goods.group_guid = groups.guid "+
                            "LEFT OUTER JOIN (SELECT * FROM goods_price WHERE price_type="+priceType+" AND db_guid='"+ID+"') AS prices " +
                            "on goods.guid = prices.item_guid " +
                            "LEFT OUTER JOIN (SELECT item_guid,image_guid,url FROM images WHERE is_default=1 AND db_guid='"+ID+"') AS images "+
                            "ON goods.guid = images.item_guid "+
                            "WHERE " + whereClause + " " +
                            "ORDER BY goods.is_group DESC, goods."+sortField;
        }

        execute();
    }

    public void getAllGoodsWithOrderContent(DataBaseItem parameters) {
        String ID = appSettings.getConnectionGUID();
        String orderID = parameters.getString("orderID");
        String filter = parameters.getString("filter");
        String group = parameters.getString("group");
        String priceType = parameters.getString("priceType");
        String filterCategory = parameters.getString("filterCategory");
        String filterBrand = parameters.getString("filterBrand");
        String filterStatus = parameters.getString("filterStatus");

        double discount = parameters.getDouble("discount");
        boolean restsOnly = parameters.getBoolean("restsOnly");
        boolean clientGoodsOnly = parameters.getBoolean("clientGoodsOnly");
        String clientGUID = parameters.getString("clientGUID");
        boolean sortByName = parameters.getBoolean("sortByName");
        boolean activeOnly = parameters.getBoolean("activeOnly");

        String sortField = "sorting";
        if (sortByName) sortField = "description_lc";
        if (!appSettings.keyIsActive()) sortField = sortField+" LIMIT "+noKeyElementLimit;

        String whereClause = "goods.db_guid='"+ID+"'";
        if (activeOnly) whereClause = whereClause + " AND goods.is_active=1";
        if (!filterStatus.isEmpty()) whereClause = whereClause + " AND (goods.status='"+filterStatus+"' OR goods.is_group=1)";
        if (!filter.isEmpty()) {
            filter = cleanString(filter);
            whereClause = whereClause + " AND (goods.description_lc LIKE '%" + filter + "%' " +
                    "OR goods.code1 LIKE '%" + filter + "%' " +
                    "OR goods.vendor_code LIKE '%" + filter + "%' " +
                    ") AND goods.is_group = 0";
        }else if (group.isEmpty()) {
                whereClause = whereClause+" AND goods.group_guid IS NULL";
        }else{
            whereClause = whereClause+" AND goods.group_guid='"+group+"'";
        }
        if (restsOnly) {
            whereClause = whereClause + " AND ((goods.quantity>0 OR content.quantity>0) OR goods.is_group=1)";
        }
        if (clientGoodsOnly) {

            whereClause = whereClause + " AND (goods.guid IN " +
                    "(SELECT item_guid FROM clients_goods WHERE sort_order>0 AND client_guid='"+clientGUID+"' AND db_guid='"+ID+"'";
            if (!filterCategory.isEmpty()) whereClause = whereClause + " AND category='"+filterCategory+"'";
            if (!filterBrand.isEmpty()) whereClause = whereClause + " AND brand='"+filterBrand+"'";
            whereClause = whereClause + ") ";

            whereClause = whereClause + " OR goods.guid IN " +
                    "(SELECT item_guid FROM clients_goods WHERE sort_order=0 AND client_guid='"+clientGUID+"' AND db_guid='"+ID+"'";
            whereClause = whereClause + ")) ";

        }
        String disc = utils.format((1-discount/100),3);
        if (priceType.isEmpty() || priceType.equals("0")) {
            query =
                    "SELECT " +
                            "goods._id as _id, " +
                            "goods.description as description, " +
                            "goods.guid as guid, " +
                            "groups.description as groupName, " +
                            "goods.group_guid as group_guid, " +
                            "images.image_guid as image_guid, " +
                            "images.url as image_url, " +
                            "goods.is_group as is_group, " +
                            "goods.is_active as is_active, " +
                            "goods.status as status, " +
                            "goods.code1 as code1, " +
                            "goods.code2 as code2, " +
                            "goods.vendor_code as vendor_code, " +
                            "goods.vendor_status as vendor_status, " +
                            "goods.quantity as quantity, " +
                            "goods.price as price, " +
                            "goods.price * "+disc+" as price_discount, " +
                            "goods.weight as weight, " +
                            "goods.unit as unit, " +
                            "goods.rest_type as rest_type, " +
                            "goods.package_value as package_value, " +
                            "goods.min_price as min_price, " +
                            "content.quantity as order_quantity, " +
                            "content.is_packed as is_packed," +
                            "assortment.no_shipment," +
                            "assortment.shipment_date," +
                            "assortment.shipment_quantity " +
                            "FROM goods " +
                            "LEFT OUTER JOIN (SELECT * FROM orders_content WHERE order_id = "+orderID+") AS content " +
                            "ON goods.guid = content.item_guid " +
                            "LEFT OUTER JOIN (SELECT guid,description FROM goods WHERE is_group=1 AND db_guid='"+ID+"') AS groups "+
                            "ON goods.group_guid = groups.guid "+
                            "LEFT OUTER JOIN (SELECT item_guid,image_guid,url FROM images WHERE is_default=1 AND db_guid='"+ID+"') AS images "+
                            "ON goods.guid = images.item_guid "+
                            "LEFT OUTER JOIN (SELECT * FROM clients_goods WHERE client_guid='"+clientGUID+"' AND db_guid='"+ID+"') AS assortment "+
                            "ON goods.guid = assortment.item_guid "+
                            "WHERE "+whereClause+" "+
                            "ORDER BY goods.is_group DESC, goods."+sortField;
        }else{
            query =
                    "SELECT "+
                            "goods._id as _id, " +
                            "goods.description as description, " +
                            "goods.guid as guid, " +
                            "groups.description as groupName, " +
                            "goods.group_guid as group_guid, " +
                            "images.image_guid as image_guid, " +
                            "images.url as image_url, " +
                            "goods.is_group as is_group, " +
                            "goods.is_active as is_active, " +
                            "goods.status as status, " +
                            "goods.code1 as code1, " +
                            "goods.code2 as code2, " +
                            "goods.vendor_code as vendor_code, " +
                            "goods.vendor_status as vendor_status, " +
                            "goods.quantity as quantity, " +
                            "goods.min_price as min_price, " +
                            "goods.weight as weight, " +
                            "goods.unit as unit, " +
                            "goods.rest_type as rest_type, " +
                            "goods.package_value as package_value, " +
                            "prices.price as price, " +
                            "prices.price * "+disc+" as price_discount, " +
                            "content.quantity as order_quantity, " +
                            "content.is_packed as is_packed, " +
                            "assortment.no_shipment," +
                            "assortment.shipment_date," +
                            "assortment.shipment_quantity " +
                            "FROM goods " +
                            "LEFT OUTER JOIN (SELECT * FROM orders_content WHERE order_id="+orderID+") AS content " +
                            "on goods.guid = content.item_guid " +
                            "LEFT OUTER JOIN (SELECT * FROM goods_price WHERE price_type="+priceType+" AND db_guid='"+ID+"') AS prices " +
                            "on goods.guid = prices.item_guid " +
                            "LEFT OUTER JOIN (SELECT guid,description FROM goods WHERE is_group=1 AND db_guid='"+ID+"') AS groups "+
                            "ON goods.group_guid = groups.guid "+
                            "LEFT OUTER JOIN (SELECT item_guid,image_guid,url FROM images WHERE is_default=1 AND db_guid='"+ID+"') AS images "+
                            "ON goods.guid = images.item_guid "+
                            "LEFT OUTER JOIN (SELECT * FROM clients_goods WHERE client_guid='"+clientGUID+"' AND db_guid='"+ID+"') AS assortment "+
                            "ON goods.guid = assortment.item_guid "+
                            "WHERE "+whereClause+" "+
                            "ORDER BY goods.is_group DESC, goods."+sortField;
        }

        execute();
    }

    public void getClientGoodsWithOrderContent(DataBaseItem parameters) {
        String ID = appSettings.getConnectionGUID();
        String orderID = parameters.getString("orderID");
        String filter = parameters.getString("filter");
        String priceType = parameters.getString("priceType");
        double discount = parameters.getDouble("discount");
        boolean restsOnly = parameters.getBoolean("restsOnly");
        String clientGUID = parameters.getString("clientGUID");
        String filterCategory = parameters.getString("filterCategory");
        String filterBrand = parameters.getString("filterBrand");
        String filterStatus = parameters.getString("filterStatus");

        String whereClause = "goods.is_active=1 AND goods.db_guid='"+ID+"'";
        if (!filterStatus.isEmpty()) whereClause = whereClause + " AND (goods.status='"+filterStatus+"' OR goods.is_group=1)";
        if (!filter.isEmpty()) {
            filter = cleanString(filter);
            whereClause = whereClause + " AND (goods.description_lc LIKE '%" + filter + "%' " +
                    "OR goods.code1 LIKE '%" + filter + "%' " +
                    "OR goods.vendor_code LIKE '%" + filter + "%') ";
        }
        if (restsOnly) {
            whereClause = whereClause + " AND (goods.quantity>0 OR content.quantity>0)";
        }
        whereClause = whereClause + " AND goods.is_group=0 AND goods.guid IN " +
                "(SELECT item_guid FROM clients_goods WHERE client_guid='"+clientGUID+"' ";
        if (!filterCategory.isEmpty()) whereClause = whereClause + " AND category='"+filterCategory+"'";
        if (!filterBrand.isEmpty()) whereClause = whereClause + " AND brand='"+filterBrand+"'";
        whereClause = whereClause + " AND db_guid='"+ID+"') ";

        String disc = utils.format((1-discount/100),3);

        if (priceType.isEmpty() || priceType.equals("0")) {
            arguments = new String[]{disc, orderID, ID, ID, clientGUID, ID, whereClause};
            query =
                    "SELECT " +
                            "goods._id as _id, " +
                            "goods.description as description, " +
                            "goods.guid as guid, " +
                            "groups.description as groupName, " +
                            "goods.group_guid as group_guid, " +
                            "images.image_guid as image_guid, " +
                            "images.url as image_url, " +
                            "goods.is_group as is_group, " +
                            "goods.is_active as is_active, " +
                            "goods.status as status, " +
                            "goods.code1 as code1, " +
                            "goods.code2 as code2, " +
                            "goods.vendor_code as vendor_code, " +
                            "goods.vendor_status as vendor_status, " +
                            "goods.quantity as quantity, " +
                            "goods.price as price, " +
                            "goods.price * ? as price_discount, " +
                            "goods.weight as weight, " +
                            "goods.unit as unit, " +
                            "goods.rest_type as rest_type, " +
                            "goods.package_value as package_value, " +
                            "goods.min_price as min_price, " +
                            "content.quantity as order_quantity, " +
                            "content.is_packed as is_packed," +
                            "assortment.sort_order," +
                            "assortment.no_shipment," +
                            "assortment.shipment_date," +
                            "assortment.shipment_quantity " +
                            "FROM goods " +
                            "LEFT OUTER JOIN (SELECT * FROM orders_content WHERE order_id=?) AS content " +
                            "ON goods.guid = content.item_guid " +
                            "LEFT OUTER JOIN (SELECT guid,description FROM goods WHERE is_group=1 AND db_guid=?) AS groups "+
                            "ON goods.group_guid = groups.guid "+
                            "LEFT OUTER JOIN (SELECT item_guid,image_guid,url FROM images WHERE is_default=1 AND db_guid=?) AS images "+
                            "ON goods.guid = images.item_guid "+
                            "LEFT OUTER JOIN (SELECT * FROM clients_goods WHERE client_guid=? AND db_guid=?) AS assortment "+
                            "ON goods.guid = assortment.item_guid "+
                            "WHERE ? "+
                            "ORDER BY assortment.sort_order";
        }else{
            arguments = new String[]{disc, orderID, priceType, ID, ID, ID, clientGUID, ID};
            query =
                    "SELECT "+
                            "goods._id as _id, " +
                            "goods.description as description, " +
                            "goods.guid as guid, " +
                            "groups.description as groupName, " +
                            "goods.group_guid as group_guid, " +
                            "images.image_guid as image_guid, " +
                            "images.url as image_url, " +
                            "goods.is_group as is_group, " +
                            "goods.is_active as is_active, " +
                            "goods.status as status, " +
                            "goods.code1 as code1, " +
                            "goods.code2 as code2, " +
                            "goods.vendor_code as vendor_code, " +
                            "goods.vendor_status as vendor_status, " +
                            "goods.quantity as quantity, " +
                            "goods.min_price as min_price, " +
                            "goods.weight as weight, " +
                            "goods.unit as unit, " +
                            "goods.rest_type as rest_type, " +
                            "goods.package_value as package_value, " +
                            "prices.price as price, " +
                            "prices.price * ? as price_discount, " +
                            "content.quantity as order_quantity, " +
                            "content.is_packed as is_packed, " +
                            "assortment.sort_order," +
                            "assortment.no_shipment," +
                            "assortment.shipment_date," +
                            "assortment.shipment_quantity " +
                            "FROM goods " +
                            "LEFT OUTER JOIN (SELECT * FROM orders_content WHERE order_id=?) AS content " +
                            "on goods.guid = content.item_guid " +
                            "LEFT OUTER JOIN (SELECT * FROM goods_price WHERE price_type=? AND db_guid=?) AS prices " +
                            "on goods.guid = prices.item_guid " +
                            "LEFT OUTER JOIN (SELECT guid,description FROM goods WHERE is_group=1 AND db_guid=?) AS groups "+
                            "ON goods.group_guid = groups.guid "+
                            "LEFT OUTER JOIN (SELECT item_guid,image_guid,url FROM images WHERE is_default=1 AND db_guid=?) AS images "+
                            "ON goods.guid = images.item_guid "+
                            "LEFT OUTER JOIN (SELECT * FROM clients_goods WHERE client_guid=? AND db_guid=?) AS assortment "+
                            "ON goods.guid = assortment.item_guid "+
                            "WHERE "+whereClause+
                            "ORDER BY assortment.sort_order";
        }

        execute();
    }

    public void loadClients(String group, String filter){
        String ID = appSettings.getConnectionGUID();
        if (filter==null) filter="";

        String clause = "clients.db_guid='"+ID+"'";
        if (!filter.isEmpty()) {
            filter = cleanString(filter);
            clause = clause + " AND clients.description_lc LIKE '%" + filter + "%' AND clients.is_group=0";
        }else {
            if (group != null) {
                clause = clause+" AND clients.group_guid='" + group + "' ";
            }else{
                clause = clause+" AND clients.group_guid IS NULL ";
            }
        }
        Utils utils = new Utils();
        String ordersClause = "WHERE db_guid='"+ID+"' AND time>="+utils.dateBeginOfToday()+" AND time<="+utils.dateEndOfToday()+" AND is_return=0";
        query =
                "SELECT " +
                        "clients._id as _id, " +
                        "clients.description as description, " +
                        "groups.description as groupName, " +
                        "clients.group_guid as group_guid, " +
                        "clients.guid as guid, " +
                        "clients.is_group as is_group, " +
                        "clients.code1 as code1, " +
                        "clients.code2 as code2, " +
                        "clients.phone as phone, " +
                        "clients.address as address, " +
                        "orders.guid as orderGUID, " +
                        "debts.sum as debt " +
                        "FROM clients " +
                        "LEFT OUTER JOIN (SELECT * FROM debts WHERE doc_id='' AND db_guid='"+ID+"') AS 'debts' " +
                        "on clients.guid = debts.client_guid " +
                        "LEFT OUTER JOIN (SELECT MAX(guid) AS guid, client_guid FROM orders "+ordersClause+
                        " GROUP BY client_guid) AS 'orders' " +
                        "on clients.guid = orders.client_guid " +
                        "LEFT OUTER JOIN (SELECT guid,description FROM clients WHERE is_group=1 AND db_guid='"+ID+"') AS groups "+
                        "ON clients.group_guid = groups.guid "+
                        "WHERE "+clause+" "+
                        "ORDER BY clients.is_group DESC, clients.description";
        if (!appSettings.keyIsActive()) query = query+" LIMIT "+noKeyElementLimit;

        execute();
    }

    public void loadClientsDirections(String group, String filter){
        String ID = appSettings.getConnectionGUID();
        if (filter==null) filter="";

        String clause = "clients_directions.db_guid='"+ID+"'";
        if (!filter.isEmpty()) {
            filter = cleanString(filter);
            clause = clause + " AND clients.description LIKE '%" + filter + "%' AND clients_directions.is_group=0";
        }else {
            if (group != null) {
                clause = clause + " AND clients_directions.group_guid='" + group + "' ";
            } else {
                clause = clause + " AND clients_directions.group_guid IS NULL ";
            }
        }
        Utils utils = new Utils();
        String ordersClause = "WHERE db_guid='"+ID+"' AND time>="+utils.dateBeginOfToday()+" AND time<="+utils.dateEndOfToday()+" AND is_return=0";
        query =
                "SELECT " +
                        "clients_directions._id as _id, " +
                        "clients_directions.description as description, " +
                        "groups.description as groupName, " +
                        "clients_directions.direction_guid as direction_guid, " +
                        "clients_directions.client_guid as client_guid, " +
                        "clients_directions.is_group as is_group, " +
                        "clients.description as client_description, " +
                        "clients.code1 as code1, " +
                        "clients.code2 as code2, " +
                        "clients.phone as phone, " +
                        "clients.address as address, " +
                        "orders.id as orderID, " +
                        "debts.sum as debt " +
                        "FROM clients_directions " +
                        "LEFT OUTER JOIN (SELECT * FROM clients WHERE is_group=0 AND db_guid='"+ID+"') AS 'clients' " +
                        "on clients_directions.client_guid = clients.guid " +
                        "LEFT OUTER JOIN (SELECT * FROM debts WHERE doc_id='' AND db_guid='"+ID+"') AS 'debts' " +
                        "on clients_directions.client_guid = debts.client_guid " +
                        "LEFT OUTER JOIN (SELECT MAX(_id) AS id,client_guid FROM orders "+ordersClause+
                        " GROUP BY client_guid) AS 'orders' " +
                        "on clients_directions.client_guid = orders.client_guid " +
                        "LEFT OUTER JOIN " +
                        "(SELECT direction_guid,description FROM clients_directions " +
                        "WHERE is_group=1 AND db_guid='"+ID+"') AS groups "+
                        "ON clients_directions.group_guid = groups.direction_guid "+
                        "WHERE "+clause+" "+
                        "ORDER BY clients_directions.is_group DESC, clients_directions.description";
        if (!appSettings.keyIsActive()) query = query+" LIMIT "+noKeyElementLimit;

        execute();
    }

    private void loadOrders(long periodBegin, String filter){
        String ID = appSettings.getConnectionGUID();
        long periodEnd;

        String clause = "WHERE db_guid='"+ID+"'";
        if (periodBegin > 0) {
            periodEnd = periodBegin + 86399;
            clause = clause+" AND time >= "+periodBegin+" AND time<="+periodEnd;
        }
        if (filter != null && !filter.equals("")){
            filter = cleanString(filter);
            clause = clause+" AND client_description LIKE '%"+filter+"%'";
        }
        query = "SELECT * FROM orders "+clause+" ORDER BY time DESC";

        execute();
    }

    private void loadTasks(long periodBegin, String filter){
        String ID = appSettings.getConnectionGUID();
        long periodEnd;

        String clause = "WHERE db_guid='"+ID+"'";
        if (periodBegin > 0) {
            periodEnd = periodBegin + 86399;
            clause = clause+" AND (is_done=0 OR (time >= "+periodBegin+" AND time<="+periodEnd+"))";
        }
        if (filter != null && !filter.equals("")){
            filter = cleanString(filter);
            clause = clause+" AND description LIKE '%"+filter+"%'";
        }
        query = "SELECT * FROM tasks "+clause+" ORDER BY time DESC";

        execute();
    }

    public void loadDocuments(String documentTag, long periodBegin, String filter){
        String ID = appSettings.getConnectionGUID();

        long periodEnd;

        switch (documentTag){
            case Constants.DOCUMENT_ORDER: loadOrders(periodBegin, filter); return;
            case Constants.DOCUMENT_TASK: loadTasks(periodBegin, filter); return;
        }

        String clause = "WHERE db_guid='"+ID+"' AND type='"+documentTag+"'";
        if (periodBegin > 0){
            periodEnd = periodBegin + 86399;
            clause = clause+" AND time >= "+periodBegin+" AND time<="+periodEnd;
        }
        if (filter != null && !filter.equals("")){
            filter = cleanString(filter);
            clause = clause+" AND client_description LIKE '%"+filter+"%'";
        }
        query = "SELECT * FROM documents "+clause+" ORDER BY time DESC";

        execute();
    }

    public void loadClientsLocationsFromOrders(long time){
        String ID = appSettings.getConnectionGUID();

        String clause = "WHERE db_guid='"+ID+"'";
        clause = clause+" AND time >= "+time+" AND time<="+(time+86400);
        clause = clause+" AND latitude<>0 AND longitude<>0 AND is_sent=1";
        query = "SELECT latitude,longitude,client_description,client_guid,number,price FROM orders "+clause+" ORDER BY time";

        execute();
    }

    private void execute(){
        if (appSettings.eraseData()) {
            database.eraseData();
            returnResult(0);
            return;
        }
        requestIN++;
        int counter = requestIN;

        Handler handler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(@NonNull Message msg) {
                returnResult(msg.what);
            }
        };

        Thread thread = new Thread("DATABASE") {
            @Override
            public void run() {
                SQLiteDatabase sqLiteDatabase = database.getDatabaseInstance();
                try {
                    Cursor cursor = sqLiteDatabase.rawQuery(query, arguments);
                    while (cursor.moveToNext()) {
                        DataBaseItem item = new DataBaseItem(cursor);
                        //add current request number to identify items
                        //on result returning
                        item.put("_request_",counter);
                        RESULT.add(item);
                    }
                    cursor.close();
                } catch (Exception cursorException) {
                    utils.log("e", "DataLoader execute: " + cursorException);
                    handler.sendEmptyMessage(0);
                }
                handler.sendEmptyMessage(counter);
            }
        };
        thread.start();

    }

    /**
     * Return loaded data to the listener.
     *
     * @param exit exit code, request number
     */
    private void returnResult(int exit){
        if (listener != null){
            if (exit == 0) listener.onDataLoaded(EMPTY);
            if (exit == requestIN) {
                //due to asynchronous process, RESULT array may contain
                //some items from previous requests, so need to filter
                //proper items by request number
                returnResult.clear();
                for (int i=0; i<RESULT.size(); i++) {
                    DataBaseItem item = RESULT.get(i);
                    if (item != null && item.getInt("_request_") == requestIN) returnResult.add(item);
                }
                RESULT.clear();
                listener.onDataLoaded(returnResult);
            }
        }
    }

    public void deleteOldData(){

        Utils utils = new Utils();

        Thread thread = new Thread("DATABASE"){
            @Override
            public void run() {
                String documentGuid;
                int order_id;
                int deletionCounter;

                long endTimeStamp = utils.dateEndShiftDate(-60);
                SQLiteDatabase sqLiteDatabase = database.getDatabaseInstance();

                try {
                    sqLiteDatabase.beginTransaction();

                    //--------------------------------------------------------------
                    // delete orders - content and header
                    Cursor cursor = sqLiteDatabase.query("orders",null,"time<"+endTimeStamp,null,null,null,null);
                    deletionCounter = cursor.getCount();
                    while (cursor.moveToNext()){
                        int index = cursor.getColumnIndex("_id");
                        if (index >= 0){
                            order_id = cursor.getInt(index);
                            sqLiteDatabase.delete("orders_content","order_id="+order_id,null);
                        }
                    }
                    sqLiteDatabase.delete("orders","time<"+endTimeStamp,null);
                    cursor.close();

                    //--------------------------------------------------------------
                    // delete wrong orders
                    deletionCounter = deletionCounter + sqLiteDatabase.delete("orders","price=0 AND quantity=0 AND client_guid='' AND is_processed=0",null);

                    //--------------------------------------------------------------
                    // delete documents - attributes, content and header
                    Cursor cursorDocuments = sqLiteDatabase.query("documents",null,"time<"+endTimeStamp,null,null,null,null);
                    deletionCounter = deletionCounter + cursorDocuments.getCount();
                    while (cursorDocuments.moveToNext()){
                        int index = cursorDocuments.getColumnIndex("guid");
                        if (index >= 0){
                            documentGuid = cursorDocuments.getString(index);
                            sqLiteDatabase.delete("document_content","doc_guid='"+documentGuid+"'",null);
                            sqLiteDatabase.delete("document_attributes","doc_guid='"+documentGuid+"'",null);
                        }
                    }
                    sqLiteDatabase.delete("documents","time<"+endTimeStamp,null);
                    cursorDocuments.close();

                    //--------------------------------------------------------------
                    // delete locations history
                    deletionCounter = deletionCounter + sqLiteDatabase.delete("locations","time<"+endTimeStamp*1000,null);

                    sqLiteDatabase.setTransactionSuccessful();
                    if (deletionCounter>0) utils.log("i","Deleted documents: "+deletionCounter);
                }catch (Exception ex) {
                    utils.log("e", "DataDeletionTask: " + ex);
                }finally {
                    sqLiteDatabase.endTransaction();
                }
            }
        };
        thread.start();

    }
}


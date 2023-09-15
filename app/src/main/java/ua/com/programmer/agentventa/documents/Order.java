package ua.com.programmer.agentventa.documents;

import android.content.ContentValues;
import android.content.Context;
import android.location.Location;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.UUID;

import ua.com.programmer.agentventa.catalogs.GoodsItem;
import ua.com.programmer.agentventa.data.DataBase;
import ua.com.programmer.agentventa.settings.AppSettings;
import ua.com.programmer.agentventa.utility.Constants;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.Utils;

@Deprecated
public class Order {

    public String date="";
    public String number="";
    public String priceTotal="";
    public int price_type=0;
    public long id=0;
    public boolean isSent;
    public String GUID="";
    public String status="";
    public String deliveryDate="";
    public boolean isReturn;
    public String client="";
    public String client_code2="";
    public double discount;
    public String notes="";
    public String payment="";
    public String nextPayment="";
    public String restType="";

    String client_guid="";
    String discountValue;
    String quantityTotal;
    boolean isCash;
    boolean isProcessed;
    boolean isSaved = false;
    long locationTime=0;
    double latitude=0;
    double longitude=0;
    double distance=0;
    double weightTotal=0;

    private long clientID;
    private long time;
    private long timeSaved;
    private String connectionID;
    private final DataBase db;
    private final Utils utils = new Utils();
    private boolean requireDeliveryDate=false;

    private AppSettings appSettings;
    private final GoodsItem goodsItem;

    public Order (Context context, String guid) {
        db = DataBase.getInstance(context);
        goodsItem = GoodsItem.getInstance(context);
        setDefaults(guid,0);
    }

    public Order (Context context, long orderID) {
        db = DataBase.getInstance(context);
        goodsItem = GoodsItem.getInstance(context);
        setDefaults("",orderID);
    }

    public void createNew(){
        initOrderDefaultValues();
    }

    private void setDefaults(String guid, long orderID){
        appSettings = db.getAppSettings();
        DataBaseItem orderData = new DataBaseItem();

        if (orderID!=0) {
            orderData = db.getOrder(orderID);
        }else if(guid != null && !guid.isEmpty()){
            orderData = db.getOrder(guid);
        }

        if (orderData.hasValues()) initOrderFromDB(orderData);
    }

    private void initOrderDefaultValues(){
        number          = ""+db.getNewOrderNumber();
        discount        = 0.0;
        price_type      = 0;
        priceTotal      = "0.00";
        quantityTotal   = "0.000";
        nextPayment     = "";
        isCash          = false;
        GUID            = UUID.randomUUID().toString();
        time            = utils.currentTime();
        timeSaved       = 0;
        date            = utils.dateLocal(time);
        status          = "";

        requireDeliveryDate = appSettings.getRequireDeliveryDate();
        connectionID = appSettings.getConnectionGUID();

        if (appSettings.getLocationServiceEnabled()){
            DataBaseItem location = db.lastSavedLocationData();
            //save last location into order only if location's time
            //is less than half an hour
            long savedLocationTime = location.getLong("time")/1000;
            long interval = utils.currentTime() - savedLocationTime;
            if (interval <= 1800) {
                latitude = location.getDouble("latitude");
                longitude = location.getDouble("longitude");
                locationTime = savedLocationTime;
            }else {
                utils.debug("high location acquiring time: "+utils.showTime(savedLocationTime, utils.currentTime()));
            }
        }

        ContentValues cv = new ContentValues();
        cv.put("number",number);
        cv.put("guid",GUID);
        cv.put("time",time);
        cv.put("date",date);
        cv.put("db_guid",connectionID);

        id = db.addOrder(cv);
    }

    private void initOrderFromDB(DataBaseItem data){
        isSaved         = true;
        connectionID    = data.getString("db_guid");
        id              = data.getLong("raw_id");
        number          = data.getString("number");
        GUID            = data.getString("guid");
        date            = data.getString("date");
        time            = data.getInt("time");
        timeSaved       = data.getInt("time_saved");
        deliveryDate    = data.getString("delivery_date");
        notes           = data.getString("notes");
        client          = data.getString("client_description");
        client_guid     = data.getString("client_guid");
        client_code2    = data.getString("client_code2");
        clientID        = data.getLong("client_id");
        status          = data.getString("status");
        payment         = data.getString("payment_type");
        isCash          = payment.equals("CASH");
        latitude        = data.getDouble("latitude");
        longitude       = data.getDouble("longitude");
        distance        = data.getDouble("distance");
        locationTime    = data.getLong("location_time");
        weightTotal     = data.getDouble("weight");
        restType        = data.getString("rest_type");

        discount        = data.getDouble("discount");
        price_type      = data.getInt("price_type");

        isReturn        = data.getInt("is_return") == 1;
        isProcessed     = data.getInt("is_processed") >= 1;
        isSent          = data.getInt("is_sent") == 1;

        priceTotal      = utils.format(data.getDouble("price"),2);
        quantityTotal   = utils.formatAsInteger(data.getDouble("quantity"),3);
        discountValue   = utils.format(data.getDouble("discount_value"),2);
        nextPayment     = utils.format(data.getDouble("next_payment"),2);

    }

    public boolean save (boolean processFlag) {

        if (isCash) {payment = "CASH";}else{payment = "CREDIT";}

        int _isreturn = 0;
        if (isReturn) {_isreturn = 1;}
        int _isProcessed = 0;
        if (processFlag) {_isProcessed = 1;}
        int _isSent = 0;
        if (isSent) {
            _isSent = 1;
            _isProcessed = 2;
        }

        if (processFlag) {
            isSaved = false;
            if (client.isEmpty()||client_guid.isEmpty()) return false;
            if (requireDeliveryDate && deliveryDate.isEmpty()) return false;
            DataBaseItem clientData = db.getClient(client_guid);
            client_code2 = clientData.getString("code2");
        }

        updateTotals();

        timeSaved = utils.currentTime();

        ContentValues cv = new ContentValues();
        cv.put("db_guid",connectionID);
        cv.put("date",date);
        cv.put("time",time);
        cv.put("time_saved",timeSaved);
        cv.put("number",number);
        cv.put("delivery_date",deliveryDate);
        cv.put("guid",GUID);
        cv.put("notes",notes);
        cv.put("status",status);
        cv.put("client_id",clientID);
        cv.put("client_guid",client_guid);
        cv.put("client_code2",client_code2);
        cv.put("client_description",client);
        cv.put("discount",discount);
        cv.put("price_type",price_type);
        cv.put("payment_type",payment);
        cv.put("price",Double.parseDouble(priceTotal));
        cv.put("quantity",Double.parseDouble(quantityTotal));
        cv.put("discount_value",Double.parseDouble(discountValue));
        cv.put("next_payment",utils.round(nextPayment,2));
        cv.put("latitude",latitude);
        cv.put("longitude",longitude);
        cv.put("distance",distance);
        cv.put("location_time",locationTime);
        cv.put("weight",weightTotal);
        cv.put("rest_type",restType);
        cv.put("is_return",_isreturn);
        cv.put("is_processed",_isProcessed);
        cv.put("is_sent",_isSent);

        isSaved = db.updateOrder(id, cv);

        isProcessed = processFlag;
        return isSaved;
    }

    void setGoodsItemProperties(DataBaseItem props){
        if (id != 0) {
            props.put("orderID",id);
            props.put("discount",discount);
            db.setGoodsItemInOrder(props);
        }
    }

    public void addItemQuantity(String itemGUID, double quantity){

        goodsItem.initialize(itemGUID,id);
        String unit = Constants.UNIT_DEFAULT;
        quantity = quantity + goodsItem.getOrderQuantity(unit);
        double price = utils.round(goodsItem.getPricePerUnit(""+price_type,unit),2);

        DataBaseItem props = new DataBaseItem(Constants.DATA_GOODS_ITEM);
        props.put("itemID",itemGUID);
        props.put("quantity",quantity);
        props.put("unit",unit);
        props.put("price",price);
        props.put("isPacked",goodsItem.isPacked);
        props.put("isDemand",goodsItem.isDemand);

        setGoodsItemProperties(props);
    }

    public void toggleIsPacked(String itemGUID){
        if (id != 0) db.toggleIsPackedFlag(id,itemGUID);
    }

    public void setClientGUID(String newClientGUID){

        if (newClientGUID != null && !newClientGUID.isEmpty()) {

            DataBaseItem clientData = db.getClient(newClientGUID);

            client = clientData.getString("description");
            clientID = clientData.getLong("raw_id");
            client_guid = newClientGUID;
            client_code2 = clientData.getString("code2");
            discount = 0.0;

            updateDistance();

            if (appSettings.useDiscounts()) {
                discount = clientData.getDouble("discount");
            }

            price_type = clientData.getInt("price_type");
            if (id != 0) db.recalcOrderContentDiscount(id, discount, price_type);

        }else {
            client = "";
            client_guid = "";
            client_code2 = "";
            discount = 0.0;
            price_type = 0;
        }
    }

    public String getClientGUID(){
        return client_guid;
    }

    void delete () {
        if (isSaved){
            db.deleteOrder(id);
        }
    }

    public void updateLocation(Location location){
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        locationTime = utils.currentTime();
        updateDistance();
    }

    void updateDistance(){
        distance = 0;
        Location clientLocation = getClientLocation();
        if (hasLocation() && clientLocation != null) {
            Location location = new Location("fused");
            location.setLatitude(latitude);
            location.setLongitude(longitude);
            distance = utils.round(location.distanceTo(clientLocation),0);
        }
    }

    /**
     * Check presence of location data: latitude and longitude
     *
     * @return true if order has coordinates
     */
    public boolean hasLocation(){
        return latitude != 0 && longitude != 0;
    }

    public Location getClientLocation(){
        DataBaseItem clientData = db.getClient(client_guid);
        double latitude = clientData.getDouble("latitude");
        double longitude = clientData.getDouble("longitude");
        if (latitude != 0 && longitude != 0){
            Location location = new Location("fused");
            location.setLatitude(latitude);
            location.setLongitude(longitude);
            return location;
        }
        return null;
    }

    public String getDistance(){
        if (distance == 0) return "▼";
        return utils.formatDistance(distance);
    }

    /**
     * Get location data: coordinates, distance to a client
     *
     * @return dataset of location data
     */
    public DataBaseItem getLocation(){
        DataBaseItem location = new DataBaseItem(Constants.DATA_LOCATION);
        location.put("latitude",latitude);
        location.put("longitude",longitude);
        location.put("distance",distance);
        location.put("time",locationTime);
        return location;
    }

    public double getWeight(){
        return weightTotal;
    }

    public String getTotalDiscount(){
        return discountValue;
    }

    public String getTotalQuantity(){
        return quantityTotal;
    }

    public void updateTotals(){
        priceTotal = utils.format(db.getOrderTotalPrice(id),2);
        quantityTotal = utils.format(db.getOrderTotalQuantity(id),1);
        discountValue = utils.format(db.getOrderTotalDiscount(id),2);
        weightTotal = db.getOrderTotalWeight(id);
    }

    public ArrayList<String> getItemsGroups(){
        return db.getGroupsInOrderId(id);
    }

    public String getPriceTypeName(){
        return db.priceTypeName(""+price_type);
    }

    public void setPriceByName(String priceName){
        price_type = db.priceTypeCode(priceName);
    }

    public JSONObject getAsJSON(){
        String userID = appSettings.getUserID();

        String mainServerAddress = appSettings.getServerName();
        String mainBaseName = appSettings.getDatabaseName();

        ArrayList<DataBaseItem> orderContent = db.getOrderContent(id);
        JSONObject document = new JSONObject();
        try {
            document.put("connection", connectionID);
            document.put("userID", userID);
            document.put("server", mainServerAddress);
            document.put("base", mainBaseName);
            document.put("type", "order");
            document.put("number", number);
            document.put("date", date);
            document.put("time", time);
            document.put("time_saved", timeSaved);
            document.put("guid",GUID);
            document.put("delivery_date", deliveryDate);
            document.put("notes",notes);
            document.put("status",status);
            document.put("client_id",client_code2);
            document.put("client_guid",client_guid);
            document.put("client_description",client);
            document.put("discount",discount);
            document.put("price_type",price_type);
            document.put("payment_type",payment);
            document.put("price",utils.round(priceTotal,2));
            document.put("quantity",utils.round(quantityTotal,3));
            document.put("discount_value",utils.round(discountValue,2));
            document.put("next_payment",utils.round(nextPayment,2));
            document.put("latitude",latitude);
            document.put("longitude",longitude);
            document.put("distance",distance);
            document.put("location_time",locationTime);
            document.put("weight",weightTotal);
            document.put("is_return",isReturn);
            document.put("is_processed",isProcessed);
            document.put("is_sent",isSent);
            JSONArray content = new JSONArray();
            int lineNumber=0;
            for (DataBaseItem lineData: orderContent){
                JSONObject line = new JSONObject();
                line.put("lineNumber",lineNumber);
                line.put("description",lineData.getString("description"));
                line.put("code1",lineData.getString("code1"));
                line.put("code2",lineData.getString("code2"));
                line.put("item_guid",lineData.getString("item_guid"));
                line.put("quantity",lineData.getDouble("quantity"));
                line.put("weight",lineData.getDouble("weight"));
                line.put("unit_code",lineData.getString("unit_code"));
                line.put("price",lineData.getDouble("price"));
                line.put("sum",lineData.getDouble("sum"));
                line.put("sum_discount",lineData.getDouble("sum_discount"));
                line.put("is_demand",lineData.getDouble("is_demand"));
                line.put("is_packed",lineData.getDouble("is_packed"));
                content.put(line);
                lineNumber++;
            }
            document.put("items",content);
            return document;
        }catch (JSONException jsonEx) {
            utils.log("e", "Order: getAsJSON: " + jsonEx.toString());
        }

        return document;
    }

}

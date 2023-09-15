package ua.com.programmer.agentventa.catalogs;

import android.content.Context;
import java.util.ArrayList;

import ua.com.programmer.agentventa.R;
import ua.com.programmer.agentventa.data.DataBase;
import ua.com.programmer.agentventa.settings.AppSettings;
import ua.com.programmer.agentventa.utility.Constants;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.Utils;

public class GoodsItem {

    private static String UNIT_DEFAULT="";
    private static String UNIT_PACKAGE="";
    private static String UNIT_WEIGHT="";
    private static String CURRENCY="";

    public String description="<...>";
    public String GUID="";
    public long rawID=0;
    public String orderQuantity="";
    public String code1="";
    public boolean isPacked=false;
    public boolean isDemand=false;
    public boolean isIndivisible=false;

    private String price="";
    private double minimalPrice=0;
    private double basePrice=0;
    private double defaultPrice=0;
    private double orderPrice=0;
    private double availableQuantity=0;
    private double packageValue=0;
    String quantity="";
    String groupName="";
    private String unit="";
    private double weight=0;
    public String barcode="";

    private final DataBase database;
    private final Utils utils = new Utils();

    private static GoodsItem goodsItem;

    /**
     * Uninitialized class instance
     *
     * @param context activity context
     */
    private GoodsItem(Context context){
        UNIT_DEFAULT = context.getResources().getString(R.string.unit_default);
        UNIT_PACKAGE = context.getResources().getString(R.string.unit_package);
        UNIT_WEIGHT = context.getResources().getString(R.string.unit_kilo);
        if (CURRENCY == null) CURRENCY = AppSettings.getInstance(context).getCurrencyName();

        database = DataBase.getInstance(context);
    }

    public static GoodsItem getInstance(Context context){
        if (goodsItem == null){
            goodsItem = new GoodsItem(context);
        }
        return goodsItem;
    }

    public GoodsItem initialize(String guid, long orderID){
        setItem(database.getGoodsItem(guid,orderID));
        return goodsItem;
    }

    private void setItem(DataBaseItem dataBaseItem){
        rawID           = dataBaseItem.getLong("raw_id");
        description     = dataBaseItem.getString("description");
        GUID            = dataBaseItem.getString("guid");
        code1           = dataBaseItem.getString("code1");
        groupName       = dataBaseItem.getString("groupName");
        barcode         = dataBaseItem.getString("barcode");
        minimalPrice    = dataBaseItem.getDouble("min_price");
        basePrice       = dataBaseItem.getDouble("base_price");

        packageValue    = dataBaseItem.getDouble("package_value");
        if (packageValue == 1) {
            packageValue = 0;
        }

        weight          = dataBaseItem.getDouble("weight");
        unit            = dataBaseItem.getString("unit",UNIT_DEFAULT);
        isIndivisible   = dataBaseItem.getString("indivisible").equals("1");

        availableQuantity = dataBaseItem.getDouble("quantity");
        quantity        = utils.formatAsInteger(availableQuantity,3);
        orderQuantity   = dataBaseItem.getString("order_quantity");

        isPacked        = dataBaseItem.getString("is_packed","0").equals("1");
        isDemand        = dataBaseItem.getString("is_demand","0").equals("1");

        defaultPrice    = dataBaseItem.getDouble("price");

        orderPrice = 0;
        double itemOrderPrice = dataBaseItem.getDouble("order_price");
        if (itemOrderPrice > 0) {
            orderPrice = itemOrderPrice;
        }
        if (orderPrice > 0) {
            price = utils.format(orderPrice, 2);
        }else {
            price = utils.format(defaultPrice,2);
        }
    }

    public double getAvailableQuantity(){
        return availableQuantity;
    }

    public String getAvailableQuantityText(String unitType) {
        String qty = "";
        switch (unitType){
            case Constants.UNIT_PACKAGE:
                if (packageValue > 0) {
                    int packageQty = (int) (availableQuantity / packageValue);
                    if (packageQty * packageValue != availableQuantity){
                        qty = qty + "~";
                    }
                    qty = qty + packageQty;
                }
                break;
            case Constants.UNIT_WEIGHT:
                qty = utils.format(availableQuantity * weight,3);
                break;
            default:
                qty = utils.formatAsInteger(availableQuantity,3);
        }
        return qty+" "+getUnit(unitType);
    }

    public double getOrderQuantity(String unitType) {
        double qty = utils.round(orderQuantity,3);
        switch (unitType){
            case Constants.UNIT_PACKAGE:
                if (packageValue > 0) {
                    qty = utils.round(qty / packageValue, 0);
                }
                break;
            case Constants.UNIT_WEIGHT:
                qty = utils.round(qty * weight,3);
                break;
        }
        return qty;
    }

    String getPriceInfo(){
        StringBuilder priceLine= new StringBuilder();
        double itemPrice;
        for (int i=1;i<=5;i++) {
            itemPrice = database.getItemPrice(GUID, ""+i);
            if (itemPrice >= 0){
                priceLine.append(database.priceTypeName(""+i)).append(": ").append(utils.format(itemPrice, 2)).append("\n");
            }
        }
        if (priceLine.toString().isEmpty()){
            priceLine = new StringBuilder(utils.format(utils.round(price, 2), 2));
        }
        if (minimalPrice > 0){
            priceLine.append("\nmin ").append(utils.format(minimalPrice, 2));
        }
        return priceLine.toString();
    }

    public double getPrice(String priceType){
        double itemPrice = database.getItemPrice(GUID, priceType);
        //-1 means there's no records in table 'goods_price'
        //if (itemPrice<0) itemPrice = utils.round(price,2);
        itemPrice = Math.max(0,itemPrice);
        return itemPrice;
    }

    public double getBasePrice(){
        return basePrice;
    }

    public double getDefaultPrice(){
        return defaultPrice;
    }

    public String getPricePerUnit(String priceType, String unitType) {
        double result = orderPrice;
        if (result == 0) result = getPrice(priceType);
        if (result == 0) result = defaultPrice;

        result = convertPricePerUnit(result,unitType);
        return utils.format(result,2);
    }

    public double convertPricePerUnit(double price, String unitType){
        double result = price;
        switch (unitType){
            case Constants.UNIT_PACKAGE:
                if (packageValue > 0) {
                    result = result * packageValue;
                }
                break;
            case Constants.UNIT_WEIGHT:
                if (weight > 0) {
                    result = result / weight;
                }
                break;
        }
        return result;
    }

    public double convertPricePerDefaultUnit(double price, String unitType) {
        double result = price;
        switch (unitType){
            case Constants.UNIT_PACKAGE:
                if (packageValue > 0) {
                    result = result / packageValue;
                }
                break;
            case Constants.UNIT_WEIGHT:
                if (weight > 0) {
                    result = result * weight;
                }
                break;
        }
        return result;
    }

    public double convertQuantityPerDefaultUnit(double qty, String unitType) {
        double result = qty;
        switch (unitType){
            case Constants.UNIT_PACKAGE:
                if (packageValue > 0) {
                    result = result * packageValue;
                }
                break;
            case Constants.UNIT_WEIGHT:
                if (weight > 0) {
                    result = result / weight;
                }
                break;
        }
        return result;
    }

    public double getMinimalPrice(){
        return minimalPrice;
    }

    public ArrayList<DataBaseItem> getPriceInformation(){
        ArrayList<DataBaseItem> priceList = new ArrayList<>();
        ArrayList<DataBaseItem> priceTypes = database.getPriceTypesArray();
        for (DataBaseItem type: priceTypes){
            double price = getPrice(type.getString("code"));
            double percent = 0;
            if (price > 0){
                DataBaseItem priceItem = new DataBaseItem(Constants.DATA_PRICE);
                if (basePrice > 0){
                    percent = (price/basePrice - 1)*100;
                }
                priceItem.put("priceType",type.getInt("code"));
                priceItem.put("description",type.getString("description"));
                priceItem.put("priceText",utils.format(price,2));
                priceItem.put("price",price);
                priceItem.put("percent",percent);
                if (percent == 0) {
                    priceItem.put("percentText", "-");
                }else{
                    priceItem.put("percentText", utils.format(percent, 1)+"%");
                }
                priceList.add(priceItem);
            }
        }
        return priceList;
    }

    public ArrayList<DataBaseItem> getCompetitorPrices(String clientGUID){
        ArrayList<DataBaseItem> priceList = new ArrayList<>();
        ArrayList<DataBaseItem> priceTypes = database.getCompetitorPrices();
        for (DataBaseItem type: priceTypes){
            DataBaseItem price = database.getCompetitorPriceData(GUID,clientGUID,type.getString("price_guid"));
            if (price.getString("description").isEmpty()){
                price.put("description",type.getString("description"));
                price.put("price_guid",type.getString("price_guid"));
                price.put("item_guid",GUID);
                price.put("client_guid",clientGUID);
            }
            priceList.add(price);
        }
        return priceList;
    }

    public void saveCompetitorPrice(DataBaseItem priceItem){
        database.saveCompetitorPrice(priceItem);
    }

    public double getPackageValue(){
        return packageValue;
    }

    public double getWeight() { return weight; }

    public String getPackageValueText() {
        return utils.formatAsInteger(packageValue,1)+" "+unit;
    }

    public String getUnit(String type) {
        switch (type){
            case Constants.UNIT_PACKAGE:
                return UNIT_PACKAGE;
            case Constants.UNIT_WEIGHT:
                return UNIT_WEIGHT;
            default:
                return unit;
        }
    }

    public String getCurrency() {
        return CURRENCY;
    }
}

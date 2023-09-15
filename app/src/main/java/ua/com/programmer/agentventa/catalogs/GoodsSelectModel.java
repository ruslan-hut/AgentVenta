package ua.com.programmer.agentventa.catalogs;

import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;

import java.util.ArrayList;

import ua.com.programmer.agentventa.R;
import ua.com.programmer.agentventa.data.DataBase;
import ua.com.programmer.agentventa.documents.Order;
import ua.com.programmer.agentventa.settings.AppSettings;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.Utils;

/**
 * Utility class that holds global parameters for a goods selecting list.
 * Parameters are shared between activities to preserve list behaviour.
 */
public class GoodsSelectModel {

    private boolean flagClientGoods;
    private boolean flagClientGoodsOnly;
    private boolean flagClientGoodsEnabled;
    private boolean flagRestsOnly;
    private boolean loadImages;
    private boolean flagOpenEditor;
    private boolean flagPackageMark;
    private boolean flagHighlightGoods;
    private boolean flagSortByName;

    private int priceType;
    private double discount;
    private double defaultQuantity;
    private String clientGUID;
    private String orderGUID;

    private ArrayList<String> filterCategoryVariants = new ArrayList<>();
    private ArrayList<String> filterBrandVariants = new ArrayList<>();
    private ArrayList<String> filterStatusVariants = new ArrayList<>();
    private String filterCategory="";
    private String filterBrand="";
    private String filterStatus="";

    private ArrayList<String> groupsInOrder = new ArrayList<>();

    private DataBase dataBase;
    private AppSettings appSettings;
    private final Utils utils = new Utils();
    private Order document;

    private static GoodsSelectModel goodsSelectModel;

    private GoodsSelectModel(){}

    static GoodsSelectModel getInstance(){
        if (goodsSelectModel == null) goodsSelectModel = new GoodsSelectModel();
        return goodsSelectModel;
    }

    void setGlobalParameters(Context context, String guid){

        if (orderGUID != null && orderGUID.equals(guid)) return;

        orderGUID = guid;
        document = new Order(context,orderGUID);
        clientGUID = document.getClientGUID();
        priceType = document.price_type;
        discount = document.discount;
        groupsInOrder = document.getItemsGroups();

        appSettings = AppSettings.getInstance(context);
        loadImages = appSettings.loadImages();
        flagOpenEditor = appSettings.openEditor();
        flagPackageMark = appSettings.readKeyBoolean(AppSettings.USE_PACKAGE_MARK);
        flagRestsOnly = appSettings.showGoodsWithRests();
        flagHighlightGoods = appSettings.highlightGoodsInOrder();
        flagSortByName = appSettings.sortGoodsByName();
        flagClientGoodsEnabled = appSettings.getClientGoodsEnabled();
        defaultQuantity = appSettings.getDefaultItemQuantity();
        if (defaultQuantity == 0) defaultQuantity = 1;

        dataBase = DataBase.getInstance(context);
        resetFilters();
    }

    void setOptionsMenuItems(Menu menu){

        MenuItem itemRests = menu.findItem(R.id.show_rests);
        itemRests.setChecked(flagRestsOnly);

        MenuItem itemClientGoods = menu.findItem(R.id.client_goods);
        if (flagClientGoodsEnabled) {
            itemClientGoods.setChecked(flagClientGoods);
        }else {
            itemClientGoods.setVisible(false);
        }

        MenuItem itemSorting = menu.findItem(R.id.sorting);
        itemSorting.setChecked(flagSortByName);

        //======================================================================
        // set submenu: category filter
        MenuItem itemMenuCategory = menu.findItem(R.id.sub_menu_category);
        if (filterCategoryVariants.size() > 0) {
            itemMenuCategory.setVisible(true);
            Menu subMenu = itemMenuCategory.getSubMenu();
            if (subMenu.hasVisibleItems()) subMenu.clear();
            int itemID = 1000;
            for (String category : filterCategoryVariants) {
                subMenu.add(0, itemID, 0, category);
                itemID++;
            }
        }else{
            itemMenuCategory.setVisible(false);
        }

        //======================================================================
        // set submenu: brand filter
        MenuItem itemMenuBrand = menu.findItem(R.id.sub_menu_brand);
        if (filterBrandVariants.size() > 0) {
            itemMenuBrand.setVisible(true);
            Menu subMenu = itemMenuBrand.getSubMenu();
            if (subMenu.hasVisibleItems()) subMenu.clear();
            int itemID = 2000;
            for (String brand : filterBrandVariants) {
                subMenu.add(0, itemID, 0, brand);
                itemID++;
            }
        }else{
            itemMenuBrand.setVisible(false);
        }

        //======================================================================
        // set submenu: status filter
        MenuItem itemMenuStatus = menu.findItem(R.id.sub_menu_status);
        if (filterStatusVariants.size() > 0) {
            itemMenuStatus.setVisible(true);
            Menu subMenu = itemMenuStatus.getSubMenu();
            if (subMenu.hasVisibleItems()) subMenu.clear();
            int itemID = 3000;
            for (String status : filterStatusVariants) {
                subMenu.add(0, itemID, 0, status);
                itemID++;
            }
        }else{
            itemMenuStatus.setVisible(false);
        }
    }

    void setFilterStatus(int variantID){
        variantID = variantID - 3000;
        if (variantID >= 0 && variantID < filterStatusVariants.size()) filterStatus = filterStatusVariants.get(variantID);
    }

    void setFilterCategory(int variantID){
        String category = "";
        variantID = variantID - 1000;
        if (variantID >= 0 && variantID < filterCategoryVariants.size()) category = filterCategoryVariants.get(variantID);
        setFilterCategory(category);
    }

    void setFilterCategory(String category){
        if (category != null) filterCategory = category;
        if (!flagClientGoods) flagClientGoods = !filterCategory.isEmpty();
        filterBrandVariants = dataBase.getClientsGoodsFilter(clientGUID,"brand","category",filterCategory);
    }

    void setFilterBrand(int variantID){
        variantID = variantID - 2000;
        if (variantID >= 0 && variantID < filterBrandVariants.size()) filterBrand = filterBrandVariants.get(variantID);
        if (!flagClientGoods) flagClientGoods = !filterBrand.isEmpty();
        filterCategoryVariants = dataBase.getClientsGoodsFilter(clientGUID,"category","brand",filterBrand);
    }

    String getFilter(){
        String filter = filterCategory;
        if (!filterBrand.isEmpty()) filter = filter+" "+filterBrand;
        if (!filterStatus.isEmpty()) filter = filter+" "+filterStatus;
        return filter;
    }

    void setFlagClientGoodsOnly(boolean flag){flagClientGoodsOnly = flag;}

    String getOrderID(){
        return orderGUID;
    }

    boolean loadImages(){
        return loadImages;
    }

    boolean highlightGoods(){
        return flagHighlightGoods;
    }

    boolean openEditor(){
        return flagOpenEditor;
    }

    boolean packageMark(){
        return flagPackageMark;
    }

    boolean clientGoodsOnly() {return flagClientGoodsOnly;}

    void switchFlagSortByName(){
        flagSortByName = !flagSortByName;
        appSettings.setSortGoodsByName(flagSortByName);
    }

    void switchFlagRestsOnly(){
        flagRestsOnly = !flagRestsOnly;
        appSettings.setShowGoodsWithRestsFlag(flagRestsOnly);
    }

    void switchFlagClientsGoods(){
        flagClientGoods = !flagClientGoods;
        if (!flagClientGoods) resetFilters();
    }

    void resetFilters(){
        filterCategory = "";
        filterBrand = "";
        filterStatus = "";
        filterCategoryVariants = dataBase.getClientsGoodsFilter(clientGUID,"category","","");
        filterBrandVariants = dataBase.getClientsGoodsFilter(clientGUID,"brand","","");
        filterStatusVariants = dataBase.getGoodsFilter("status","");
    }

    void changeItemQuantity(String item,int flag){
        if (document == null) return;
        if (item == null || item.isEmpty()) return;
        if (flag == 2) {
            document.toggleIsPacked(item);
        }else{
            document.addItemQuantity(item,flag * defaultQuantity);
        }
    }

    DataBaseItem getListParameters(){
        DataBaseItem listParameters = new DataBaseItem();
        listParameters.put("priceType",priceType);
        listParameters.put("discount",discount);
        listParameters.put("restsOnly",flagRestsOnly);
        listParameters.put("clientGoodsOnly",flagClientGoods);
        listParameters.put("clientGUID",clientGUID);
        listParameters.put("sortByName",flagSortByName);
        listParameters.put("filterCategory",filterCategory);
        listParameters.put("filterBrand",filterBrand);
        listParameters.put("filterStatus",filterStatus);
        if (document == null) {
            listParameters.put("orderID",0);
            listParameters.put("activeOnly", true);
        }else{
            listParameters.put("orderID",document.id);
            listParameters.put("activeOnly",!document.isReturn);
        }
        return listParameters;
    }

    DataBaseItem getDocumentTotals(){
        DataBaseItem totals = new DataBaseItem();
        if (document != null){
            document.updateTotals();
            totals.put("price",document.priceTotal);
            totals.put("discount",document.getTotalDiscount());
            totals.put("quantity",document.getTotalQuantity());
            totals.put("weight",utils.formatWeight(document.getWeight()));
        }
        return totals;
    }

    ArrayList<String> getGroupsInOrder(){
        return groupsInOrder;
    }

    void onFinish(){
        document = null;
        orderGUID = "";
    }
}

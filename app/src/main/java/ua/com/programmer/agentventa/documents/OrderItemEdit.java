package ua.com.programmer.agentventa.documents;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.InputType;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

import ua.com.programmer.agentventa.R;
import ua.com.programmer.agentventa.catalogs.GoodsItem;
import ua.com.programmer.agentventa.settings.AppSettings;
import ua.com.programmer.agentventa.utility.Constants;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.Utils;

public class OrderItemEdit extends AppCompatActivity {

    private Order order;
    private GoodsItem goodsItem;

    private String currentUnit;
    private CheckBox isPacked;
    private CheckBox isDemand;
    private EditText editQuantity;
    private EditText editPrice;
    private boolean allowPriceTypeChoose;

    private int accentPriceColor;
    private int commonPriceColor;

    private ArrayList<DataBaseItem> competitorPrices = new ArrayList<>();
    private PriceAdapter competitorAdapter;

    private final Utils utils = new Utils();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        accentPriceColor = getResources().getColor(R.color.yellowLight);
        commonPriceColor = getResources().getColor(R.color.white);

        AppSettings appSettings = AppSettings.getInstance(this);
        allowPriceTypeChoose = appSettings.allowPriceTypeChoose();

        boolean fullScreen = !appSettings.useBriefEditorScreen() && appSettings.showAllPrices();

        if (fullScreen) {
            setContentView(R.layout.picker_fragment);
        }else{
            setContentView(R.layout.activity_order_item_edit_alt);
        }

        ActionBar actionBar = getSupportActionBar();
        if (actionBar!=null){
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        setTitle(R.string.order);

        Intent intent = getIntent();
        String orderGUID = intent.getStringExtra("orderGUID");
        String itemGUID = intent.getStringExtra("itemGUID");
        order = new Order(this,orderGUID);
        goodsItem = GoodsItem.getInstance(this).initialize(itemGUID,order.id);
        currentUnit = Constants.UNIT_DEFAULT;

        String defaultPriceUnit = goodsItem.getCurrency();
        String unitText = goodsItem.getUnit(Constants.UNIT_DEFAULT);
        if (!unitText.isEmpty()){
            defaultPriceUnit = defaultPriceUnit+"/"+unitText;
        }

        //==================== DESCRIPTION
        TextView description = findViewById(R.id.item_description);
        description.setText(goodsItem.description);

        //==================== CODE
        TextView code = findViewById(R.id.item_code);
        code.setText(goodsItem.code1);

        //==================== PACKAGE INFO
        TextView packageInfo = findViewById(R.id.package_info);
        if (goodsItem.getPackageValue() > 0) {
            String value = getResources().getString(R.string.attr_per_package);
            value = value+" "+goodsItem.getPackageValueText();
            packageInfo.setVisibility(View.VISIBLE);
            packageInfo.setText(value);
        }else {
            packageInfo.setVisibility(View.GONE);
        }

        //==================== EDIT QUANTITY
        editQuantity = findViewById(R.id.edit_quantity);
        double defaultQuantity = appSettings.getDefaultItemQuantity();
        if (defaultQuantity > 0 && goodsItem.getOrderQuantity(currentUnit) == 0){
            editQuantity.setText(utils.formatAsInteger(defaultQuantity,3));
        }
        editQuantity.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> onEditTextAction(actionId));

        //==================== EDIT PRICE
        editPrice = findViewById(R.id.edit_price);
        editPrice.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> onEditTextAction(actionId));
        if (!appSettings.allowPriceTypeChoose() || goodsItem.getMinimalPrice() == goodsItem.getDefaultPrice()) {
            editPrice.setEnabled(false);
        }else{
            //enable manual changing of markup percent value
            editPrice.setOnLongClickListener((View v) -> onEditPriceLongClick());
        }

        //==================== BASE PRICE
        if (fullScreen){
            if (goodsItem.getBasePrice() > 0) {
                String basePriceText = utils.format(goodsItem.getBasePrice(), 2);
                basePriceText = basePriceText + " " + defaultPriceUnit;
                TextView basePriceView = findViewById(R.id.base_price);
                basePriceView.setText(basePriceText);
            }else {
                LinearLayout basePriceLine = findViewById(R.id.base_price_view);
                basePriceLine.setVisibility(View.GONE);
            }
        }

        //==================== PACKAGE MARK
        isPacked = findViewById(R.id.box_is_packed);
        if (appSettings.readKeyBoolean(AppSettings.USE_PACKAGE_MARK)) {
            isPacked.setChecked(goodsItem.isPacked);
        }else {
            LinearLayout packageMark = findViewById(R.id.item_is_packed);
            packageMark.setVisibility(View.GONE);
        }

        //==================== DEMAND MARK
        isDemand = findViewById(R.id.box_is_demand);
        if (appSettings.readKeyBoolean(AppSettings.USE_DEMANDS)) {
            isDemand.setChecked(goodsItem.isDemand);
        }else {
            LinearLayout demandMark = findViewById(R.id.item_is_demand);
            demandMark.setVisibility(View.GONE);
        }

        //==================== KEYBOARD
        Window window = getWindow();
        if (window != null){
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        editQuantity.requestFocus();

        //==================== PRICE LIST
        if (fullScreen){
            TextView priceListTitleUnit = findViewById(R.id.price_list_title_unit);
            priceListTitleUnit.setText(defaultPriceUnit);

            RecyclerView recyclerView = findViewById(R.id.price_recycler);
            recyclerView.setHasFixedSize(true);
            LinearLayoutManager layoutManager = new LinearLayoutManager(this);
            recyclerView.setLayoutManager(layoutManager);

            PriceAdapter priceAdapter = new PriceAdapter();
            recyclerView.setAdapter(priceAdapter);
            priceAdapter.loadPriceList(goodsItem.getPriceInformation());
            if (priceAdapter.getItemCount() == 0){
                LinearLayout priceListTitle = findViewById(R.id.price_list_title);
                priceListTitle.setVisibility(View.GONE);
            }

            TextView title2 = findViewById(R.id.competitor_price_header);

            if (appSettings.readKeyBoolean(Constants.OPT_COMPETITOR_PRICE)) {
                RecyclerView recycler2 = findViewById(R.id.competitor_price_recycler);
                recycler2.setHasFixedSize(true);
                LinearLayoutManager layoutManager2 = new LinearLayoutManager(this);
                recycler2.setLayoutManager(layoutManager2);

                competitorAdapter= new PriceAdapter();
                competitorAdapter.setLayout(R.layout.price_list_competitor_item);
                recycler2.setAdapter(competitorAdapter);
                competitorPrices = goodsItem.getCompetitorPrices(order.client_guid);
                competitorAdapter.loadPriceList(competitorPrices);

                if (competitorAdapter.getItemCount() == 0) {
                    title2.setVisibility(View.GONE);
                }
            }else{
                title2.setVisibility(View.GONE);
            }
        }

        //==================== BUTTONS (bottom of the screen)
        TextView buttonCancel = findViewById(R.id.button_cancel);
        buttonCancel.setOnClickListener((View v) -> onBackPressed());
        TextView buttonOK = findViewById(R.id.button_yes);
        buttonOK.setOnClickListener((View v) -> saveValuesAndExit());

        onUnitChange();

    }

    void onUnitChange() {
        if (currentUnit.equals(Constants.UNIT_PACKAGE) && goodsItem.getPackageValue() == 0){
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.error_no_unit_convertion)
                    .setPositiveButton(R.string.OK,null);
            AlertDialog dialog = builder.create();
            dialog.show();
            currentUnit = Constants.UNIT_DEFAULT;
        }
        if (currentUnit.equals(Constants.UNIT_WEIGHT) && goodsItem.getWeight() == 0){
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.error_no_unit_weight)
                    .setPositiveButton(R.string.OK,null);
            AlertDialog dialog = builder.create();
            dialog.show();
            currentUnit = Constants.UNIT_DEFAULT;
        }

        String unit = goodsItem.getUnit(currentUnit);
        String priceUnit = goodsItem.getCurrency();
        if (!unit.equals("")){
            priceUnit = priceUnit+"/"+unit;
        }

        //==================== UNIT
        TextView unitText = findViewById(R.id.item_unit);
        unitText.setText(unit);

        //==================== AVAILABLE QUANTITY
        TextView availableQuantity = findViewById(R.id.available_quantity);
        if (goodsItem.getAvailableQuantity() > 0) {
            availableQuantity.setText(goodsItem.getAvailableQuantityText(currentUnit));
        }else {
            availableQuantity.setText(R.string.title_items_qty_is_null);
            TextView availableQuantityTitle = findViewById(R.id.available_quantity_title);
            availableQuantityTitle.setVisibility(View.GONE);
        }

        //==================== PRICE UNIT
        TextView priceUnitText = findViewById(R.id.price_unit);
        priceUnitText.setText(priceUnit);

        //==================== EDIT QUANTITY
        String orderQuantity = utils.formatAsInteger(goodsItem.getOrderQuantity(currentUnit),3);
        editQuantity.setHint(orderQuantity);

        //==================== EDIT PRICE
        editPrice = findViewById(R.id.edit_price);
        editPrice.setText(goodsItem.getPricePerUnit(""+order.price_type,currentUnit));
        //editPrice.setText("");

    }

    boolean onEditPriceLongClick() {
        EditText input = new EditText(this);
        input.setHint("%%");
        input.setInputType(InputType.TYPE_CLASS_NUMBER);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.enter_price_percent)
                .setView(input)
                .setPositiveButton(R.string.OK, (DialogInterface dialog, int which)  -> {
                        String value = input.getText().toString();
                        if (!value.equals("")){
                            double newPrice = goodsItem.getBasePrice() * (1 + utils.round(value,1) / 100);
                            if (newPrice < goodsItem.getMinimalPrice()) {
                                newPrice = goodsItem.getMinimalPrice();
                            }
                            newPrice = goodsItem.convertPricePerUnit(newPrice,currentUnit);
                            editPrice.setText(utils.format(newPrice, 2));
                        }
                });
        AlertDialog dialog = builder.create();
        dialog.show();
        return true;
    }

    boolean onEditTextAction(int actionId){
        if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT){
            saveValuesAndExit();
            return true;
        }
        return false;
    }

    /**
     * Check user-entered quantity for items, marked as indivisible
     *
     * @param qty entered quantity in base units
     * @return true if quantity is invalid
     */
    boolean invalidQuantity(double qty){
        if (goodsItem.isIndivisible){
            if (qty > Math.round(qty)){
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.error_indivisible)
                        .setTitle(R.string.error)
                        .setPositiveButton(R.string.OK, null);
                AlertDialog dialog = builder.create();
                dialog.show();
                return  true;
            }
        }
        return false;
    }

    void saveValuesAndExit(){
        String enteredQuantity = editQuantity.getText().toString();
        String enteredPrice = editPrice.getText().toString();
        final double minPriceValue = goodsItem.getMinimalPrice();

        double quantity = utils.round(goodsItem.orderQuantity,3);

        if (!enteredQuantity.isEmpty()) {
            if(!enteredQuantity.contains(".") && enteredQuantity.charAt(0) == '0'){
                enteredQuantity = "0."+enteredQuantity.substring(1);
            }
            quantity = goodsItem.convertQuantityPerDefaultUnit(
                    utils.round(enteredQuantity,3),
                    currentUnit);
        }

        if (invalidQuantity(quantity)) return;

        double price;
        if (!enteredPrice.isEmpty()) {
            price = goodsItem.convertPricePerDefaultUnit(
                    utils.round(enteredPrice, 2),
                    currentUnit);
        }else{
            price = utils.round(goodsItem.getPricePerUnit(""+order.price_type,currentUnit),2);
            //if (price == 0) price = goodsItem.getDefaultPrice();
        }
        if (price < minPriceValue) price = minPriceValue;

        DataBaseItem props = new DataBaseItem(Constants.DATA_GOODS_ITEM);
        props.put("itemID",goodsItem.GUID);
        props.put("quantity",quantity);
        props.put("unit",currentUnit);
        props.put("price",price);
        props.put("isPacked",isPacked.isChecked());
        props.put("isDemand",isDemand.isChecked());

        order.setGoodsItemProperties(props);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_picker, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) onBackPressed();
        if (id == R.id.save_item) saveValuesAndExit();
        if (id == R.id.switch_unit){
            if (currentUnit.equals(Constants.UNIT_DEFAULT)) {
                currentUnit = Constants.UNIT_PACKAGE;
            }else {
                currentUnit = Constants.UNIT_DEFAULT;
            }
            onUnitChange();
        }
        if (id == R.id.switch_weight){
            if (currentUnit.equals(Constants.UNIT_DEFAULT)) {
                currentUnit = Constants.UNIT_WEIGHT;
            }else {
                currentUnit = Constants.UNIT_DEFAULT;
            }
            onUnitChange();
        }
        return super.onOptionsItemSelected(item);
    }

    void onPriceItemClick(DataBaseItem priceItem){
        if (priceItem.typeOf(Constants.DATA_PRICE)) {

            String priceText = priceItem.getString("priceText");
            if (allowPriceTypeChoose) {
                double price = goodsItem.convertPricePerUnit(utils.round(priceText, 2), currentUnit);
                editPrice.setText(utils.format(price, 2));
            }

        }else if (priceItem.typeOf(Constants.DATA_COMPETITOR_PRICE)){

            openNotesDialog(priceItem);

        }
    }

    private void openNotesDialog (DataBaseItem priceItem) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);

        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.dialog_edit_text,null);

        final EditText editText = view.findViewById(R.id.edit_text);
        editText.setText(priceItem.getString("notes"));

        builder.setView(view);
        builder.setMessage("")
                .setTitle(getResources().getString(R.string.doc_notes))
                .setPositiveButton(getResources().getString(R.string.save), (DialogInterface dialogInterface, int i) ->
                {
                    for (DataBaseItem price: competitorPrices){
                        if (price.getString("price_guid").equals(priceItem.getString("price_guid"))){
                            price.put("notes", editText.getText().toString());
                            goodsItem.saveCompetitorPrice(price);
                        }
                    }
                    competitorAdapter.loadPriceList(competitorPrices);
                })
                .setNegativeButton(getResources().getString(R.string.cancel),null);
        final android.app.AlertDialog dialog = builder.create();
        Window window = dialog.getWindow();
        if (window != null){
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        dialog.show();
    }

    private class PriceAdapter extends RecyclerView.Adapter<PriceHolder>{

        private int layout = R.layout.price_list_item;
        ArrayList<DataBaseItem> priceList = new ArrayList<>();

        public void setLayout(int res){
            layout = res;
        }

        @SuppressLint("NotifyDataSetChanged")
        void loadPriceList(ArrayList<DataBaseItem> list){
            priceList.clear();
            priceList.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public PriceHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
            View view = LayoutInflater.from(viewGroup.getContext())
                    .inflate(layout,viewGroup,false);
            return new PriceHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PriceHolder priceHolder, int i) {
            DataBaseItem item = priceList.get(i);
            try {
                priceHolder.setHolderValues(item);
            }catch (Exception e){
                utils.debug("item: "+item.getAsJSON().toString());
                utils.error(e.toString());
            }
        }

        @Override
        public int getItemCount() {
            return priceList.size();
        }
    }

    private class PriceHolder extends RecyclerView.ViewHolder{

        TextView priceDescription;
        TextView priceValue;
        TextView pricePercent;
        TextView priceNotes;
        CardView holderView;

        PriceHolder(View v){
            super(v);
            holderView = v.findViewById(R.id.root_view);
            priceDescription = v.findViewById(R.id.description);
            priceValue = v.findViewById(R.id.value);
            pricePercent = v.findViewById(R.id.percent);
            priceNotes = v.findViewById(R.id.notes);
        }

        void setHolderValues(DataBaseItem item){
            final String price = item.getString("priceText");
            priceDescription.setText(item.getString("description"));

            if (item.typeOf(Constants.DATA_PRICE)) {
                priceValue.setText(price);
                pricePercent.setText(item.getString("percentText"));
                if (order.price_type == item.getInt("priceType")) {
                    holderView.setCardBackgroundColor(accentPriceColor);
                } else {
                    holderView.setCardBackgroundColor(commonPriceColor);
                }
            }else if (item.typeOf(Constants.DATA_COMPETITOR_PRICE)){
                priceValue.setText(item.getString("price"));
                priceNotes.setText(item.getString("notes"));
            }

            itemView.setOnClickListener((View v) -> onPriceItemClick(item));
        }
    }
}

package ua.com.programmer.agentventa.catalogs;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

import ua.com.programmer.agentventa.data.DataBase;
import ua.com.programmer.agentventa.documents.DialogPriceTypeChoose;
import ua.com.programmer.agentventa.R;
import ua.com.programmer.agentventa.data.DataLoader;
import ua.com.programmer.agentventa.settings.AppSettings;
import ua.com.programmer.agentventa.settings.SettingsActivity;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.ImageLoader;
import ua.com.programmer.agentventa.utility.Utils;

@Deprecated
public class GoodsActivity extends AppCompatActivity implements
        DialogPriceTypeChoose.DialogPriceTypeChooseListener {

    private GoodsAdapter mGoodsAdapter;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private final Utils utils = new Utils();
    private String currentGroup;
    private String filter;
    private boolean flagRestsOnly;
    private Menu optionsMenu;
    private int priceType;
    private Context mContext;
    private AppSettings appSettings;
    private DataLoader dataLoader;

    private ImageLoader imageLoader;
    private boolean loadImages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_goods);

        final Intent intent = getIntent();
        currentGroup = intent.getStringExtra("currentGroup");

        mSwipeRefreshLayout = findViewById(R.id.goods_swipe);
        mSwipeRefreshLayout.setOnRefreshListener(this::doUpdate);

        dataLoader = new DataLoader(this,this::onDataLoaded);

        mContext = this;
        appSettings = AppSettings.getInstance(this);
        loadImages = appSettings.loadImages();
        if (loadImages){
            imageLoader = new ImageLoader(this);
        }

        flagRestsOnly = appSettings.showGoodsWithRests();
        priceType = appSettings.getDefaultPriceType();

        //priceTypes = new DB(mContext).getPriceTypes();
        setTextPriceType();

        Toolbar toolBar = findViewById(R.id.goods_toolbar);
        setSupportActionBar(toolBar);
        setupActionBar();

        EditText editText = findViewById(R.id.edit_search);
        editText.setVisibility(View.GONE);
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter = null;
                if (count > 0 || before > 0) {
                    filter = s.toString();
                    refreshList();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        RecyclerView recyclerView = findViewById(R.id.goods_recycler);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(mContext);
        recyclerView.setLayoutManager(linearLayoutManager);

        mGoodsAdapter = new GoodsAdapter();
        recyclerView.setAdapter(mGoodsAdapter);

    }

    @Override
    protected void onResume() {
        int appPriceType = appSettings.getDefaultPriceType();
        if (appPriceType != priceType) {
            priceType = appPriceType;
            onPriceTypeChange();
        }
        EditText editText = findViewById(R.id.edit_search);
        if (editText.getText().toString().isEmpty()) {
            editText.setVisibility(View.GONE);
            refreshList();
        }else {
            editText.setVisibility(View.VISIBLE);
        }
        super.onResume();
    }

    private void setupActionBar(){
        ActionBar actionBar = getSupportActionBar();
        if(actionBar!=null){
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            if (currentGroup != null) {
                GoodsItem item = GoodsItem.getInstance(this).initialize(currentGroup,0);
                actionBar.setTitle(item.description);
            }else{
                actionBar.setTitle(R.string.header_goods_list);
            }
        }
    }

    private void onPriceTypeChange(){
        setTextPriceType();
        appSettings.setDefaultPriceType(priceType);
    }

    private void setTextPriceType(){
        TextView textView = findViewById(R.id.price_type);
        textView.setText(DataBase.getInstance(this).priceTypeName(""+priceType));
    }

    private void doUpdate(){
        refreshList();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_goods, menu);
        optionsMenu = menu;
        setMenuTitles();
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.action_search) {
            EditText editText = findViewById(R.id.edit_search);
            if (editText.getVisibility() == View.VISIBLE) {
                editText.setText("");
                editText.setVisibility(View.GONE);
            }else{
                editText.setVisibility(View.VISIBLE);
                editText.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null){
                    imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, InputMethodManager.HIDE_IMPLICIT_ONLY);
                }
            }
            return true;
        }

        if (id==R.id.show_rests){
            flagRestsOnly = !flagRestsOnly;
            appSettings.setShowGoodsWithRestsFlag(flagRestsOnly);
            setMenuTitles();
            refreshList();
        }

        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }

        if (id == R.id.sorting) {
            appSettings.setSortGoodsByName(!appSettings.sortGoodsByName());
            setMenuTitles();
            refreshList();
            return true;
        }

        if (id == R.id.select_price_type) {
            DialogPriceTypeChoose dialogPriceTypeChoose = new DialogPriceTypeChoose();
            dialogPriceTypeChoose.show(getSupportFragmentManager(),"Dialogs");
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        EditText editText = findViewById(R.id.edit_search);
        if (editText != null && editText.getVisibility() == View.VISIBLE){
            editText.setText("");
            editText.setVisibility(View.GONE);
        }else if (currentGroup != null){
            finish();
        }else{
            super.onBackPressed();
        }
    }

    private void setMenuTitles(){
        MenuItem itemRests = optionsMenu.findItem(R.id.show_rests);
        itemRests.setChecked(flagRestsOnly);
        MenuItem itemSorting = optionsMenu.findItem(R.id.sorting);
        itemSorting.setChecked(appSettings.sortGoodsByName());
    }

    private void setText(View view, int id, String text){
        TextView textView = view.findViewById(id);
        textView.setText(text);
    }

    private void openItemDialog(String itemID){
        final GoodsItem goodsItem = GoodsItem.getInstance(this).initialize(itemID,0);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.goods_item,null);

        setText(view,R.id.item_name,goodsItem.description);
        setText(view,R.id.item_group,goodsItem.groupName);
        setText(view,R.id.item_code,goodsItem.code1);
        setText(view,R.id.item_barcode,goodsItem.barcode);
        setText(view,R.id.item_price,goodsItem.getPriceInfo());
        setText(view,R.id.item_quantity,goodsItem.quantity);

        builder.setView(view);
        builder.setMessage("")
                .setTitle("")
                .setPositiveButton(getResources().getString(R.string.OK),null);
        final AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void openImage(DataBaseItem imageParameters){
        try {
            Intent intent = new Intent(mContext, GoodsImageActivity.class);
            intent.putExtra("imageGUID",imageParameters.getString("image_guid"));
            intent.putExtra("itemGUID",imageParameters.getString("guid"));
            startActivity(intent);
        } catch (Exception ex) {
            utils.error("Open image: " + ex);
        }
    }

    private void openGroup(String itemGUID){
        //final GoodsItem goodsItem = new GoodsItem(mContext,itemID);
        try {
            Intent intent = new Intent(mContext, GoodsActivity.class);
            intent.putExtra("currentGroup",itemGUID);
            startActivity(intent);
        } catch (Exception ex) {
            utils.error("Open group: " + ex);
        }
    }

    public void refreshList(){
        if (mSwipeRefreshLayout.isRefreshing()){
            mSwipeRefreshLayout.setRefreshing(false);
        }
        TextView dataIndicator = findViewById(R.id.data_indicator);
        dataIndicator.setText(R.string.loading);
        dataIndicator.setVisibility(View.VISIBLE);

        dataLoader.getGoodsItems(currentGroup,filter,priceType,flagRestsOnly);
    }

    public synchronized void onDataLoaded(ArrayList<DataBaseItem> items) {
        TextView dataIndicator = findViewById(R.id.data_indicator);
        if (items.size() == 0) {
            dataIndicator.setText(R.string.title_no_data);
            dataIndicator.setVisibility(View.VISIBLE);
        }else {
            dataIndicator.setVisibility(View.INVISIBLE);
        }
        mGoodsAdapter.loadCursorItems(items);
    }

    @Override
    public void onPriceTypeChoose(int priceType) {
        this.priceType = priceType;
        onPriceTypeChange();
        refreshList();
    }

    private class GoodsAdapter extends RecyclerView.Adapter<GoodsActivity.GoodsViewHolder>{

        ArrayList<DataBaseItem> cursorItems = new ArrayList<>();

        GoodsAdapter(){}

        @SuppressLint("NotifyDataSetChanged")
        void loadCursorItems(ArrayList<DataBaseItem> items){
            cursorItems.clear();
            cursorItems.addAll(items);
            notifyDataSetChanged();
        }

        private DataBaseItem safeGetItem(int position){
            DataBaseItem dataBaseItem;
            try {
                dataBaseItem = cursorItems.get(position);
            }catch (Exception e){
                return new DataBaseItem();
            }
            return dataBaseItem;
        }

        @Override
        public int getItemViewType(int position) {
            DataBaseItem dataBaseItem = safeGetItem(position);
            return dataBaseItem.getInt("is_group");
        }

        @NonNull
        @Override
        public GoodsActivity.GoodsViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, final int i) {
            int viewId = R.layout.goods_list_item;
            if (i==1){
                viewId = R.layout.goods_list_item_group;
            }
            final View view = LayoutInflater.from(viewGroup.getContext()).inflate(viewId,viewGroup,false);
            return new GoodsActivity.GoodsViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull GoodsActivity.GoodsViewHolder holder, int position) {
            DataBaseItem dataBaseItem = safeGetItem(position);
            int isGroup = dataBaseItem.getInt("is_group");
            if (isGroup==0) {
                holder.showItemInfo(dataBaseItem);
            }else{
                holder.showGroupInfo(dataBaseItem);
            }
        }

        @Override
        public int getItemCount() {
            return cursorItems.size();
        }
    }

    class GoodsViewHolder extends RecyclerView.ViewHolder{

        TextView itemCode;
        TextView itemVendorCode;
        TextView itemName;
        TextView itemPrice;
        TextView itemQuantity;
        TextView itemGroup;
        ImageView itemIcon;
        ImageView itemImage;
        //TextView quantityDelimiter;
        TextView quantityTitle;
        LinearLayout packageLine;
        TextView itemPackageValue;
        View holderView;

        GoodsViewHolder(View view){
            super(view);
            holderView = view;
            itemCode = view.findViewById(R.id.item_code);
            itemVendorCode = view.findViewById(R.id.item_vendor_code);
            itemName = view.findViewById(R.id.item_name);
            itemIcon = view.findViewById(R.id.item_icon);
            itemCode = view.findViewById(R.id.item_code);
            itemName = view.findViewById(R.id.item_name);
            itemPrice = view.findViewById(R.id.item_price);
            itemQuantity = view.findViewById(R.id.item_quantity);
            itemGroup = view.findViewById(R.id.item_group);
            //quantityDelimiter = view.findViewById(R.id.quantity_delimiter);
            quantityTitle = view.findViewById(R.id.item_quantity_title);

            packageLine = view.findViewById(R.id.package_line);
            itemPackageValue = view.findViewById(R.id.item_package_value);

            itemImage = view.findViewById(R.id.item_image);
            if (!loadImages && itemImage != null) itemImage.setVisibility(View.GONE);
        }

        void showItemInfo(DataBaseItem dataBaseItem){
            final String itemId = dataBaseItem.getString("guid");

            itemName.setText(dataBaseItem.getString("description"));
            itemGroup.setText(dataBaseItem.getString("groupName"));
            itemCode.setText(dataBaseItem.getString("code1"));
            itemVendorCode.setText(dataBaseItem.getString("vendor_code"));

            double price = dataBaseItem.getDouble("price");
            if (price != 0) {
                itemPrice.setText(utils.format(price, 2));
            } else {
                itemPrice.setText("-.--");
            }

            double quantity = dataBaseItem.getDouble("quantity");
            String unit = dataBaseItem.getString("unit");
            if (quantity != 0) {
                String quantityText = utils.formatAsInteger(quantity,3)+" "+unit;
                itemQuantity.setText(quantityText);
                quantityTitle.setText(R.string.rest);
                //quantityDelimiter.setText("X");
            }else {
                itemQuantity.setText("");
                quantityTitle.setText(R.string.title_items_qty_is_null);
                //quantityDelimiter.setText("");
            }

            double packageValue = dataBaseItem.getDouble("package_value");
            if (packageValue > 0) {
                String packageText = utils.formatAsInteger(packageValue,1) + " " + unit;
                packageLine.setVisibility(View.VISIBLE);
                itemPackageValue.setText(packageText);
            }else {
                packageLine.setVisibility(View.GONE);
            }

            itemView.setOnClickListener((View v) -> openItemDialog(itemId));

            if (loadImages){
                itemImage.setOnClickListener((View v) -> openImage(dataBaseItem));
                imageLoader.load(dataBaseItem,itemImage);
            }

        }

        void showGroupInfo(DataBaseItem dataBaseItem){
            itemName.setText(dataBaseItem.getString("description"));
            itemCode.setText(""); //cursor.getString(cursor.getColumnIndex("code1")));
            final String itemId = dataBaseItem.getString("guid");
            itemView.setOnClickListener((View v) -> openGroup(itemId));
        }
    }
}

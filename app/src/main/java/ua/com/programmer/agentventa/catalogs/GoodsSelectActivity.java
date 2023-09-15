package ua.com.programmer.agentventa.catalogs;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
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
import android.widget.Toast;

import java.util.ArrayList;

import ua.com.programmer.agentventa.documents.OrderItemEdit;
import ua.com.programmer.agentventa.R;
import ua.com.programmer.agentventa.data.DataLoader;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.ImageLoader;
import ua.com.programmer.agentventa.utility.Utils;

@Deprecated
public class GoodsSelectActivity extends AppCompatActivity {

    private static GoodsSelectModel model;

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private GoodsAdapter mGoodsAdapter;
    private DataLoader dataLoader;

    private String currentGroup=null;
    private String filter;

    private String selectedItem="";
    private double selectedPrice=0;

    private final Utils utils = new Utils();
    private final int colorSelected = Color.parseColor("#FFF9C4");
    private final int colorSelectedForEdit = Color.parseColor("#FFCCBC");

    private Menu optionsMenu;
    private ImageLoader imageLoader;

    private final ActivityResultLauncher<Intent> openNextScreen = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            result -> {});

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_goods_select);

        Intent intent = getIntent();
        currentGroup = intent.getStringExtra("currentGroup");

        model = GoodsSelectModel.getInstance();
        model.setGlobalParameters(this,intent.getStringExtra("orderGUID"));
        model.setFlagClientGoodsOnly(intent.getBooleanExtra("clientGoodsOnly",false));

        String category = intent.getStringExtra("categoryFilter");
        model.setFilterCategory(category);

//        Toolbar toolBar = findViewById(R.id.goods_toolbar);
//        setSupportActionBar(toolBar);
//        setupActionBar();

        dataLoader = new DataLoader(this,this::onDataLoaded);

        if (model.loadImages()) imageLoader = new ImageLoader(this);

        if (model.openEditor()) {
            Toolbar bottomBar = findViewById(R.id.goods_bottombar);
            bottomBar.setVisibility(View.GONE);
        }else{
            setupBottomBar();
        }

        RecyclerView recyclerView = findViewById(R.id.goods_recycler);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(linearLayoutManager);

        mGoodsAdapter = new GoodsAdapter();
        recyclerView.setAdapter(mGoodsAdapter);

        mSwipeRefreshLayout = findViewById(R.id.goods_swipe);
        mSwipeRefreshLayout.setOnRefreshListener(this::doUpdate);

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

    }

    @Override
    protected void onResume() {
        EditText editText = findViewById(R.id.edit_search);
        if (editText.getText().toString().isEmpty()) {
            editText.setVisibility(View.GONE);
        }else {
            editText.setVisibility(View.VISIBLE);
        }
        refreshList();
        super.onResume();
    }

    private void setupActionBar(){
        ActionBar actionBar = getSupportActionBar();
        if(actionBar!=null){
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            if (model.clientGoodsOnly()){
                setTitle(R.string.client_goods);
            }else if (currentGroup != null) {
                GoodsItem item = GoodsItem.getInstance(this).initialize(currentGroup,0);
                actionBar.setTitle(item.description);
            }else{
                actionBar.setTitle(R.string.header_goods_list);
            }
        }
    }

    private boolean itemIsSelected(){
        if (selectedItem.equals("")){
            Toast.makeText(GoodsSelectActivity.this, R.string.item_not_selected, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void setupBottomBar() {

        ImageView imageAdd = findViewById(R.id.menu_goods_plus);
        imageAdd.setOnClickListener((View v) -> {
                if (itemIsSelected()) {
                    model.changeItemQuantity(selectedItem,1);
                    refreshList();
            }
        });

        ImageView imageRemove = findViewById(R.id.menu_goods_minus);
        imageRemove.setOnClickListener((View v) -> {
                if (itemIsSelected()) {
                    model.changeItemQuantity(selectedItem,-1);
                    refreshList();
                }
        });

        ImageView imageCheck = findViewById(R.id.menu_goods_packed);
        if (model.packageMark()) {
            imageCheck.setOnClickListener((View v) -> {
                    if (itemIsSelected()) {
                        model.changeItemQuantity(selectedItem,2);
                        refreshList();
                    }
            });
        }else{
            imageCheck.setVisibility(View.INVISIBLE);
        }

        ImageView imageEdit = findViewById(R.id.menu_goods_edit);
        imageEdit.setOnClickListener((View v) -> {
                if (itemIsSelected()) {
                    openItemEditDialog();
                    refreshList();
                }
        });

    }

    private void doUpdate(){
        refreshList();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_goods_select, menu);
        optionsMenu = menu;
        model.setOptionsMenuItems(optionsMenu);
        return super.onCreateOptionsMenu(menu);
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.show_totals) {
            showTotals();
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
                try {
                    assert imm != null;
                    imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, InputMethodManager.HIDE_IMPLICIT_ONLY);
                }catch (Exception ex){
                    utils.log("e","toggleSoftInput:"+ex);
                }

            }
            return true;
        }

        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }

        if (id == R.id.sorting) model.switchFlagSortByName();

        if (id == R.id.show_rests) model.switchFlagRestsOnly();

        if (id == R.id.client_goods) model.switchFlagClientsGoods();

        if (id>=1000 && id<2000) model.setFilterCategory(id);
        if (id>=2000 && id<3000) model.setFilterBrand(id);
        if (id>=3000 && id<4000) model.setFilterStatus(id);

        model.setOptionsMenuItems(optionsMenu);
        refreshList();

        return super.onOptionsItemSelected(item);
    }

    void resetFilters(){
        model.resetFilters();
        model.setOptionsMenuItems(optionsMenu);
        refreshList();
    }

    @Override
    protected void onDestroy() {
        if (currentGroup == null) model.onFinish();
        super.onDestroy();
    }

    public void refreshList(){
        if (mSwipeRefreshLayout.isRefreshing()){
            mSwipeRefreshLayout.setRefreshing(false);
        }
        TextView dataIndicator = findViewById(R.id.data_indicator);
        dataIndicator.setText(R.string.loading);
        dataIndicator.setVisibility(View.VISIBLE);

        String listFilter = model.getFilter();
        TextView filterTextView = findViewById(R.id.filter_text);
        if(listFilter.isEmpty()) {
            filterTextView.setVisibility(View.GONE);
        }else{
            filterTextView.setVisibility(View.VISIBLE);
            filterTextView.setText(listFilter);
            filterTextView.setOnClickListener(v -> resetFilters());
        }

        DataBaseItem listParameters = model.getListParameters();
        listParameters.put("group", currentGroup);
        if (filter != null) listParameters.put("filter", filter);

        if (model.clientGoodsOnly()) {
            dataLoader.getClientGoodsWithOrderContent(listParameters);
        }else{
            dataLoader.getAllGoodsWithOrderContent(listParameters);
        }
    }

    public void onDataLoaded(ArrayList<DataBaseItem> items) {
        TextView dataIndicator = findViewById(R.id.data_indicator);
        if (items.size() == 0) {
            dataIndicator.setText(R.string.title_no_data);
            dataIndicator.setVisibility(View.VISIBLE);
        }else {
            dataIndicator.setVisibility(View.INVISIBLE);
        }
        mGoodsAdapter.loadCursorItems(items);
    }

    private void openItemEditDialog () {
        Intent intent = new Intent(this, OrderItemEdit.class);
        intent.putExtra("orderGUID",model.getOrderID());
        intent.putExtra("itemGUID",selectedItem);
        intent.putExtra("price",selectedPrice);
        startActivity(intent);
    }

    private void openImage(DataBaseItem imageParameters){
        try {
            Intent intent = new Intent(this, GoodsImageActivity.class);
            intent.putExtra("imageGUID",imageParameters.getString("image_guid"));
            intent.putExtra("itemGUID",imageParameters.getString("guid"));
            startActivity(intent);
        } catch (Exception ex) {
            utils.log("e", "Open image: " + ex);
        }
    }

    private void openGroup(String groupGUID){
        try {
            Intent intent = new Intent(this, GoodsSelectActivity.class);
            intent.putExtra("currentGroup",groupGUID);
            intent.putExtra("orderGUID",model.getOrderID());
            openNextScreen.launch(intent);
        } catch (Exception ex) {
            utils.log("e", "Open group: " + ex);
        }
    }

    private void showTotals() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.goods_select_totals_dialog,null);

        DataBaseItem totals = model.getDocumentTotals();

        final TextView tvDiscount = view.findViewById(R.id.total_discount);
        tvDiscount.setText(totals.getString("discount"));
        final TextView tvPrice = view.findViewById(R.id.total_price);
        tvPrice.setText(totals.getString("price"));
        final TextView tvQuantity = view.findViewById(R.id.total_quantity);
        tvQuantity.setText(totals.getString("quantity"));
        final TextView tvWeight = view.findViewById(R.id.total_weight);
        tvWeight.setText(totals.getString("weight"));

        builder.setView(view);
        builder.setMessage("")
                .setTitle("")
                .setPositiveButton(R.string.continue_edit, null)
                .setNegativeButton(R.string.close, (DialogInterface dialog, int which) -> {
                        Intent intent = new Intent();
                        intent.putExtra("finish",true);
                        setResult(RESULT_OK,intent);
                        finish();
                });
        final AlertDialog dialog = builder.create();
        dialog.show();

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data!=null){
            if (currentGroup!=null){
                Intent intent = new Intent();
                intent.putExtra("finish",true);
                setResult(RESULT_OK,intent);
            }
            finish();
        }
        if (filter != null && !filter.isEmpty()) refreshList();
        super.onActivityResult(requestCode, resultCode, data);
    }

    private class GoodsAdapter extends RecyclerView.Adapter<GoodsSelectActivity.GoodsViewHolder>{

        ArrayList<DataBaseItem> cursorItems = new ArrayList<>();
        ArrayList<String> groups = new ArrayList<>();

        GoodsAdapter(){
            groups.addAll(model.getGroupsInOrder());
        }

        @SuppressLint("NotifyDataSetChanged")
        void loadCursorItems(ArrayList<DataBaseItem> items){
            cursorItems.clear();
            cursorItems.addAll(items);
            notifyDataSetChanged();
        }

        private DataBaseItem safeGetItem(int position){
            DataBaseItem dataBaseItem = new DataBaseItem();
            try {
                dataBaseItem = cursorItems.get(position);
            }catch (Exception e){
                utils.debug("GoodsSelectActivity.safeGetItem: "+e);
            }
            if (dataBaseItem == null) dataBaseItem = new DataBaseItem();
            return dataBaseItem;
        }

        @Override
        public int getItemViewType(int position) {
            DataBaseItem dataBaseItem = safeGetItem(position);
            return dataBaseItem.getInt("is_group");
        }

        @NonNull
        @Override
        public GoodsSelectActivity.GoodsViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, final int i) {
            int viewId = R.layout.goods_select_list_item;
            if (i==1){
                viewId = R.layout.goods_list_item_group;
            }
            final View view = LayoutInflater.from(viewGroup.getContext()).inflate(viewId,viewGroup,false);
            return new GoodsSelectActivity.GoodsViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull GoodsSelectActivity.GoodsViewHolder holder, int position) {
            DataBaseItem dataBaseItem = safeGetItem(position);
            int isGroup = dataBaseItem.getInt("is_group");
            if (isGroup==0) {
                holder.showItemInfo(dataBaseItem);
            }else{
                holder.showGroupInfo(dataBaseItem,groups);
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
        TextView itemVendorStatus;
        TextView itemName;
        ImageView itemImage;
        TextView itemPrice;
        TextView itemQuantity;
        TextView itemGroup;
        ImageView itemIcon;
        ImageView iconInactive;
        TextView quantityDelimiter;
        TextView orderQuantity;
        TextView restType;
        LinearLayout itemCard;
        LinearLayout shipmentInfo;
        LinearLayout restTypeLine;
        LinearLayout vendorStatusLine;
        TextView shipmentDays;
        TextView shipmentDate;
        TextView shipmentQuantity;
        View holderView;

        GoodsViewHolder(View view){
            super(view);
            holderView = view;
            itemCard = view.findViewById(R.id.item_card);
            itemCode = view.findViewById(R.id.item_code);
            itemVendorCode = view.findViewById(R.id.item_vendor_code);
            itemVendorStatus = view.findViewById(R.id.item_vendor_status);
            itemName = view.findViewById(R.id.item_name);

            //itemIcon = view.findViewById(R.id.item_icon);
            //iconInactive = view.findViewById(R.id.icon_inactive);
            itemCode = view.findViewById(R.id.item_code);
            itemName = view.findViewById(R.id.item_name);
            itemPrice = view.findViewById(R.id.item_price);
            itemQuantity = view.findViewById(R.id.item_quantity);
            itemGroup = view.findViewById(R.id.item_group);
            quantityDelimiter = view.findViewById(R.id.quantity_delimiter);
            orderQuantity = view.findViewById(R.id.list_item_quantity);

            restType = view.findViewById(R.id.item_rest_type);
            //restTypeLine = view.findViewById(R.id.line_rest_type);
            //vendorStatusLine = view.findViewById(R.id.line_vendor_status);

            //shipmentInfo = view.findViewById(R.id.shipment_info);
            shipmentDays = view.findViewById(R.id.shipment_days);
            shipmentDate = view.findViewById(R.id.shipment_date);
            shipmentQuantity = view.findViewById(R.id.shipment_quantity);

            itemImage = view.findViewById(R.id.item_image);
            if (!model.loadImages() && itemImage != null) itemImage.setVisibility(View.GONE);
        }

        void showItemInfo(DataBaseItem dataBaseItem){

            final String itemId = dataBaseItem.getString("guid");

            itemName.setText(dataBaseItem.getString("description"));
            itemGroup.setText(dataBaseItem.getString("groupName"));
            itemCode.setText(dataBaseItem.getString("code1"));

            if (dataBaseItem.getInt("is_packed")==1) {
                itemIcon.setVisibility(View.VISIBLE);
            }else{
                itemIcon.setVisibility(View.GONE);
            }
            if (dataBaseItem.getInt("is_active")==0) {
                iconInactive.setVisibility(View.VISIBLE);
            }else{
                iconInactive.setVisibility(View.GONE);
            }

            String vendorCode = dataBaseItem.getString("vendor_code");
            if (vendorCode.isEmpty()) {
                itemVendorCode.setVisibility(View.GONE);
            }else {
                itemVendorCode.setVisibility(View.VISIBLE);
                itemVendorCode.setText(vendorCode);
            }

            String vendorStatus = dataBaseItem.getString("vendor_status");
            if (vendorStatus.isEmpty()) {
                vendorStatusLine.setVisibility(View.GONE);
            }else {
                vendorStatusLine.setVisibility(View.VISIBLE);
                itemVendorStatus.setText(vendorStatus);
            }

            String restTypeText = dataBaseItem.getString("rest_type");
            if (restTypeText.isEmpty()) {
                restTypeLine.setVisibility(View.GONE);
            }else{
                restTypeLine.setVisibility(View.VISIBLE);
                restType.setText(restTypeText);
            }

            //double price = dataBaseItem.getDouble("price");
            double price = utils.max(dataBaseItem.getDouble("price_discount"),
                    dataBaseItem.getDouble("min_price"));
            if (price != 0) {
                itemPrice.setText(utils.format(price, 2));
            } else {
                itemPrice.setText("-.--");
            }

            double quantity = dataBaseItem.getDouble("quantity");
            String unit = dataBaseItem.getString("unit");
            if (quantity!=0) {
                String quantityText = utils.formatAsInteger(quantity,3)+" "+unit;
                itemQuantity.setText(quantityText);
                quantityDelimiter.setText("X");
            }else {
                itemQuantity.setText("");
                quantityDelimiter.setText("");
            }

            double quantityInOrder = dataBaseItem.getDouble("order_quantity");
            if (quantityInOrder != 0) {
                String quantityText = "" + quantityInOrder;
                orderQuantity.setText(quantityText);
            }else {
                orderQuantity.setText("");
            }

            if (model.highlightGoods() && quantityInOrder!=0){
                itemCard.setBackgroundColor(colorSelected);
            }else {
                itemCard.setBackgroundColor(Color.WHITE);
            }

            if (itemId.equals(selectedItem) && !model.openEditor()){
                itemCard.setBackgroundColor(colorSelectedForEdit);
            }

            if (model.loadImages()){
                itemImage.setOnClickListener((View v) -> openImage(dataBaseItem));
                imageLoader.load(dataBaseItem,itemImage);
            }

            itemView.setOnClickListener((View v) -> {
                    selectedItem = itemId;
                    selectedPrice = price;
                    if (model.openEditor()) {
                        openItemEditDialog();
                    }else {
                        refreshList();
                    }
            });

            String sDate = dataBaseItem.getString("shipment_date");
            if (sDate.isEmpty()) {
                shipmentInfo.setVisibility(View.GONE);
            }else{
                shipmentInfo.setVisibility(View.VISIBLE);
                shipmentDate.setText(sDate);
                shipmentDays.setText(dataBaseItem.getString("no_shipment"));
                shipmentQuantity.setText(utils.formatAsInteger(dataBaseItem.getDouble("shipment_quantity"),3));
            }
        }

        void showGroupInfo(DataBaseItem dataBaseItem, ArrayList<String> groups){
            itemName.setText(dataBaseItem.getString("description"));
            itemCode.setText(""); //cursor.getString(cursor.getColumnIndex("code1")));

            final String itemGUID = dataBaseItem.getString("guid");

            if (groups.contains(itemGUID) && model.highlightGoods()) {
                itemCard.setBackgroundColor(colorSelected);
            }else{
                itemCard.setBackgroundColor(Color.WHITE);
            }

            itemView.setOnClickListener((View v) -> openGroup(itemGUID));
        }
    }
}

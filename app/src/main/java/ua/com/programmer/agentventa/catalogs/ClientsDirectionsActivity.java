package ua.com.programmer.agentventa.catalogs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;

import ua.com.programmer.agentventa.R;
import ua.com.programmer.agentventa.data.DataLoader;
import ua.com.programmer.agentventa.documents.Order;
import ua.com.programmer.agentventa.documents.OrderActivity;
import ua.com.programmer.agentventa.settings.SettingsActivity;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.Utils;

public class ClientsDirectionsActivity extends AppCompatActivity {

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private ClientsAdapter mClientsAdapter;
    private String currentGroup;
    private String filter;
    private long orderID;
    private final Utils utils = new Utils();
    private Context mContext;
    private DataLoader dataLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clients);

        final Intent intent = getIntent();
        orderID = intent.getLongExtra("orderID",0);
        currentGroup = intent.getStringExtra("currentGroup");
        String groupDescription = intent.getStringExtra("description");

        mContext = this;

        Toolbar toolBar = findViewById(R.id.clients_toolbar);
        setSupportActionBar(toolBar);
        setupActionBar(groupDescription);

        RecyclerView recyclerView = findViewById(R.id.clients_list);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(mContext);
        recyclerView.setLayoutManager(linearLayoutManager);

        mClientsAdapter = new ClientsAdapter();
        recyclerView.setAdapter(mClientsAdapter);

        mSwipeRefreshLayout = findViewById(R.id.clients_swipe);
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

        dataLoader = new DataLoader(this,this::onDataLoaded);
       //list will be refreshed in onResume
    }

    private void setupActionBar(String description){
        ActionBar actionBar = getSupportActionBar();
        if(actionBar!=null){
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            if (currentGroup != null){
                actionBar.setTitle(description);
            }else{
                actionBar.setTitle(R.string.header_clients_list);
            }
        }
    }

    private void doUpdate(){
        refreshList();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_clients, menu);
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
                imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, InputMethodManager.HIDE_IMPLICIT_ONLY);
            }
            return true;
        }

        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        EditText editText = findViewById(R.id.edit_search);
        if (editText != null && editText.getVisibility() == View.VISIBLE){
            if (filter != null) {
                editText.setText("");
            }else{
                editText.setVisibility(View.GONE);
            }
        }else if (currentGroup != null){
            finish();
        }else{
            super.onBackPressed();
        }
    }

    public void refreshList(){
        if (mSwipeRefreshLayout.isRefreshing()){
            mSwipeRefreshLayout.setRefreshing(false);
        }
        dataLoader.loadClientsDirections(currentGroup,filter);
    }

    public synchronized void onDataLoaded(ArrayList<DataBaseItem> items) {
        mClientsAdapter.loadCursorItems(items);
    }

    private void openClient(String currentClientID, long currentOrderID){
        Client client = new Client(mContext, currentClientID);
        Intent intent = new Intent(this, ClientInfoActivity.class);
        intent.putExtra("clientGUID",client.GUID);
        if (currentOrderID != 0){
            intent.putExtra("orderID",currentOrderID);
        }
        startActivityForResult(intent,3);
    }

    private void openGroup(DataBaseItem groupData){
        Intent intent = new Intent(this, ClientsDirectionsActivity.class);
        intent.putExtra("currentGroup", groupData.getString("direction_guid"));
        intent.putExtra("description", groupData.getString("description"));
        startActivityForResult(intent,3);
    }

    private void openOrder(long currentOrderID){
        Intent intent = new Intent(mContext, OrderActivity.class);
        intent.putExtra("orderID", currentOrderID);
        startActivityForResult(intent, 1); //this makes dataset reset on activity result
    }

    private void openDialogForNewOrder(final String currentClientID){
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setMessage(R.string.q_create_new_document)
                .setTitle(R.string.q_title)
                .setPositiveButton(R.string.yes, (DialogInterface dialogInterface, int i) -> {
                        Order newOrder = new Order(mContext,"");
                        newOrder.createNew();
                        newOrder.setClientGUID(currentClientID);
                        newOrder.save(false);
                        Intent intent = new Intent(mContext, OrderActivity.class);
                        intent.putExtra("orderID", newOrder.GUID);
                        startActivityForResult(intent, 1); //this makes dataset reset on activity result
                        Toast.makeText(mContext, R.string.new_document, Toast.LENGTH_LONG).show();
                })
                .setNegativeButton(R.string.no,null);
        final AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void chooseClientAndExit(String currentClientID){
        final Intent intent = getIntent();
        intent.putExtra("clientID", currentClientID);
        intent.putExtra("finish", true);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode==1){
            refreshList();
        }
        if (data!=null && requestCode==2) {      //getting result for chosen client
            chooseClientAndExit(data.getStringExtra("clientID"));
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onResume() {
        EditText editText = findViewById(R.id.edit_search);
        if (editText.getText().toString().isEmpty()) {
            editText.setVisibility(View.GONE);
            refreshList();
        }else {
            editText.setVisibility(View.VISIBLE);
        }
        super.onResume();
    }

    private class ClientsAdapter extends RecyclerView.Adapter<ClientsViewHolder>{

        ArrayList<DataBaseItem> cursorItems = new ArrayList<>();

        ClientsAdapter(){

        }

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
        public ClientsViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, final int i) {
            int viewId = R.layout.clients_list_item;
            if (i==1){
                viewId = R.layout.goods_list_item_group;
            }
            final View view = LayoutInflater.from(viewGroup.getContext()).inflate(viewId,viewGroup,false);
            return new ClientsViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ClientsViewHolder holder, int position) {
            DataBaseItem dataBaseItem = safeGetItem(position);
            int isGroup = dataBaseItem.getInt("is_group");
            if (isGroup==0) {
                holder.showClientInfo(dataBaseItem);
            }else{
                holder.showGroupInfo(dataBaseItem);
            }
        }

        @Override
        public int getItemCount() {
            return cursorItems.size();
        }
    }

    class ClientsViewHolder extends RecyclerView.ViewHolder{

        TextView itemCode;
        TextView itemName;
        TextView itemDebt;
        TextView itemGroup;
        ImageView itemIcon;

        ClientsViewHolder(View view){
            super(view);
            itemCode = view.findViewById(R.id.item_code);
            itemName = view.findViewById(R.id.item_name);

            itemIcon = view.findViewById(R.id.item_icon);
            itemCode = view.findViewById(R.id.item_code);
            itemName = view.findViewById(R.id.item_name);
            itemDebt = view.findViewById(R.id.item_debt);
            itemGroup = view.findViewById(R.id.item_group);
        }

        void showClientInfo(DataBaseItem dataBaseItem){

            final String clientId = dataBaseItem.getString("client_guid");
            final long currentOrderId = dataBaseItem.getLong("orderID");

            if (currentOrderId != 0) {
                itemIcon.setBackgroundColor(Color.GREEN);
            }else {
                itemIcon.setBackgroundColor(Color.WHITE);
            }

            String address = utils.getString(dataBaseItem.getString("address"));
            itemGroup.setText(address);

            itemCode.setText(dataBaseItem.getString("code1"));
            itemName.setText(dataBaseItem.getString("client_description"));

            double debt = dataBaseItem.getDouble("debt");
            if (debt != 0) {
                itemDebt.setText(utils.format(debt, 2));
            } else {
                itemDebt.setText("");
            }

            itemView.setOnClickListener((View v) -> {
                    try {
                        if (orderID != 0) {
                            chooseClientAndExit(clientId);
                        } else {
                            openClient(clientId,currentOrderId);
                        }
                    } catch (Exception clientEx) {
                        utils.log("e", "Client directions onClick: " + clientEx.toString());
                    }
            });

            itemView.setOnLongClickListener((View v) -> {
                    if (currentOrderId!=0) {
                        openOrder(currentOrderId);
                    }else{
                        openDialogForNewOrder(clientId);
                    }
                    return true;
            });

        }

        void showGroupInfo(DataBaseItem dataBaseItem){
            itemName.setText(dataBaseItem.getString("description"));
            itemCode.setText(""); //cursor.getString(cursor.getColumnIndex("code1")));

            itemView.setOnClickListener((View v) -> {
                    try {
                        openGroup(dataBaseItem);
                    } catch (Exception e) {
                        utils.log("e", "Directions open group: " + e.toString());
                    }
            });
        }
    }
}

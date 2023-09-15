package ua.com.programmer.agentventa.catalogs;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import ua.com.programmer.agentventa.data.DataBase;
import ua.com.programmer.agentventa.documents.DebtDocument;
import ua.com.programmer.agentventa.documents.DocumentActivity;
import ua.com.programmer.agentventa.documents.Order;
import ua.com.programmer.agentventa.documents.OrderActivity;
import ua.com.programmer.agentventa.R;
import ua.com.programmer.agentventa.catalogs.locations.LocationPickupActivity;
import ua.com.programmer.agentventa.utility.Constants;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.Utils;

@Deprecated
public class ClientInfoActivity extends AppCompatActivity{

    private Client client;
    private Utils utils;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.client_fragment);

        utils = new Utils();
        DataBase db = DataBase.getInstance(this);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar!=null){
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        final Intent intent = getIntent();
        String clientGUID = intent.getStringExtra("clientGUID");

        client = new Client(this,clientGUID);
        if (client.GUID.isEmpty()){
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show();
            finish();
        }

        setTextInView(client.description,R.id.item_name);
        setTextInView(client.groupName,R.id.item_group);
        setTextInView(client.address,R.id.item_address);
        setTextInView(client.notes,R.id.item_info);
        setTextInView(client.phone,R.id.item_phone);
        setTextInView(client.code1,R.id.item_code);
        setTextInView(utils.format(client.getDebt(),2),R.id.item_debt);
        setTextInView(utils.format(client.discount,1),R.id.item_discount);
        setTextInView(db.priceTypeName(""+client.priceType),R.id.item_price);

        if (client.bonus == 0) {
            LinearLayout lineBonus = findViewById(R.id.line_bonus);
            lineBonus.setVisibility(View.GONE);
        }else{
            setTextInView(utils.format(client.bonus,2),R.id.item_bonus);
        }

        if (client.notes.equals("")){
            TextView tvNotes = findViewById(R.id.item_info);
            tvNotes.setVisibility(View.GONE);
        }

        ImageView dialIcon = findViewById(R.id.dial_icon);
        if (!client.phone.equals("")) {
            dialIcon.setOnClickListener((View v) ->
            {
                Intent intentPhone = new Intent(Intent.ACTION_DIAL);
                intentPhone.setData(Uri.parse("tel:" + client.phone));
                try {
                    startActivity(intentPhone);
                }catch (Exception dialEx){
                    Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show();
                    utils.log("w","Dial intent: "+dialEx);
                }
            });
        }else{
            dialIcon.setVisibility(View.INVISIBLE);
        }

        //============== new documents creation buttons ===============
        TextView buttonNewOrder = findViewById(R.id.add_order);
        if (client.isBanned) {
            buttonNewOrder.setVisibility(View.GONE);
        }else{
            buttonNewOrder.setOnClickListener((View v) -> {
                Order newOrder = new Order(this,"");
                newOrder.createNew();
                newOrder.setClientGUID(clientGUID);
                newOrder.save(false);
                Intent newOrderIntent = new Intent(this, OrderActivity.class);
                newOrderIntent.putExtra("orderGUID", newOrder.GUID);
                startActivity(newOrderIntent);
                Toast.makeText(this, R.string.new_document, Toast.LENGTH_LONG).show();
            });
        }

        TextView buttonNewCash = findViewById(R.id.add_cash);
        buttonNewCash.setOnClickListener((View v) -> {
            Intent newCashIntent = new Intent(this, DocumentActivity.class);
            newCashIntent.putExtra("setClientID", client.GUID);
            newCashIntent.putExtra("guid","");
            newCashIntent.putExtra("tag", Constants.DOCUMENT_CASH);
            startActivity(newCashIntent);
            Toast.makeText(this, R.string.new_document, Toast.LENGTH_LONG).show();
        });

        TextView emptyView = findViewById(R.id.empty);
        RecyclerView recyclerView = findViewById(R.id.client_doc_list);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        ListAdapter listAdapter = new ListAdapter();
        recyclerView.setAdapter(listAdapter);

        ArrayList<DataBaseItem> documentsList = db.getDebtDocuments(clientGUID);
        if (documentsList.size() == 0) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        }else{
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            listAdapter.loadListItems(documentsList);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        client = new Client(this, client.GUID);
        ImageView addressIcon = findViewById(R.id.item_address_icon);
        if (client.getCoordinates() == null) addressIcon.setVisibility(View.INVISIBLE);
        else{
            addressIcon.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_client_info, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.pickup_location) {
            Intent intent = new Intent(this, LocationPickupActivity.class);
            intent.putExtra("client_guid",client.GUID);
            startActivity(intent);
            return true;
        }

        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setTextInView(String str, int id) {
        TextView textView = findViewById(id);
        textView.setText(str);
    }

    private void onDocumentClick(DataBaseItem item){
        Intent intent = new Intent(this, DebtDocument.class);
        intent.putExtra("doc_guid",item.getString("doc_guid"));
        intent.putExtra("title",item.getString("doc_id"));
        startActivity(intent);
    }

    private class ListAdapter extends RecyclerView.Adapter<ClientInfoActivity.ListViewHolder>{

        private final ArrayList<DataBaseItem> list = new ArrayList<>();

        @NonNull
        @Override
        public ClientInfoActivity.ListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.debt_list_item,parent,false);
            return new ClientInfoActivity.ListViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ClientInfoActivity.ListViewHolder holder, int position) {
            DataBaseItem item = list.get(position);
            holder.setValues(item);
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        @SuppressLint("NotifyDataSetChanged")
        void loadListItems(ArrayList<DataBaseItem> items){
            list.clear();
            list.addAll(items);
            notifyDataSetChanged();
        }
    }

    private class ListViewHolder extends RecyclerView.ViewHolder{

        private final View holderView;
        private final int colorAccent = getResources().getColor(R.color.yellowLight);
        private final int colorNormal = getResources().getColor(R.color.white);

        ListViewHolder(@NonNull View itemView) {
            super(itemView);
            holderView = itemView;
        }

        void setValues(DataBaseItem item){
            TextView icon = holderView.findViewById(R.id.icon);
            if (item.getInt("has_content") == 1) {
                icon.setVisibility(View.VISIBLE);
                holderView.setOnClickListener(v -> onDocumentClick(item));
            }else{
                icon.setVisibility(View.INVISIBLE);
            }

            TextView description = holderView.findViewById(R.id.item_name);
            description.setText(item.getString("doc_id"));

            TextView sum = holderView.findViewById(R.id.item_price);
            String textSum = utils.format(item.getString("sum"),2);
            sum.setText(textSum);
            if (textSum.contains("-")) holderView.setBackgroundColor(colorAccent);
            else{
                holderView.setBackgroundColor(colorNormal);
            }

            LinearLayout balanceLine = holderView.findViewById(R.id.balance_line);
            double sumIn = item.getDouble("sum_in");
            double sumOut = item.getDouble("sum_out");
            if (sumIn+sumOut == 0) {
                balanceLine.setVisibility(View.GONE);
            }else {
                balanceLine.setVisibility(View.VISIBLE);
                TextView textSumIn = holderView.findViewById(R.id.sum_in);
                textSumIn.setText(utils.format(sumIn, 2));
                TextView textSumOut = holderView.findViewById(R.id.sum_out);
                textSumOut.setText(utils.format(sumOut, 2));
            }
        }
    }
}

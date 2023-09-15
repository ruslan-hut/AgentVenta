package ua.com.programmer.agentventa.documents;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import ua.com.programmer.agentventa.R;
import ua.com.programmer.agentventa.utility.DataBaseItem;

public class DebtDocument extends AppCompatActivity implements DebtDocumentModel.DebtDocumentListener {

    private static DebtDocumentModel model;
    private ListAdapter listAdapter;
    private String DOC_GUID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.debt_fragment);
        ActionBar actionBar = getSupportActionBar();
        if(actionBar!=null){
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        model = new DebtDocumentModel(this);

        Intent intent = getIntent();
        String title = intent.getStringExtra("title");
        setText(R.id.title, title);

        RecyclerView recyclerView = findViewById(R.id.items_list);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        listAdapter = new ListAdapter();
        recyclerView.setAdapter(listAdapter);

        DOC_GUID = intent.getStringExtra("doc_guid");

    }

    @Override
    protected void onResume() {
        model.findByGUID(DOC_GUID);
        super.onResume();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) onBackPressed();
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDataLoaded() {
        showDocumentData();
    }

    private void setText(int viewID, String text){
        TextView textView = findViewById(viewID);
        if (textView != null)
            if (text == null || text.isEmpty()) {
                textView.setVisibility(View.GONE);
            }else{
                textView.setText(text);
            }
    }

    private void showDocumentData(){

        ImageView icon = findViewById(R.id.status_icon);
        if (icon != null)
            if (model.get("is_processed").equals("1")) {
                icon.setVisibility(View.VISIBLE);
            }else{
                icon.setVisibility(View.GONE);
            }

        setText(R.id.title, model.get("title"));
        setText(R.id.company, model.get("company"));
        setText(R.id.warehouse, model.get("warehouse"));
        setText(R.id.contractor, model.get("contractor"));
        setText(R.id.total, model.get("total"));
        setText(R.id.error_text, model.get("error"));

        listAdapter.loadListItems(model.getItems());
    }

    private static class ListAdapter extends RecyclerView.Adapter<DebtDocument.ListViewHolder>{

        private final ArrayList<DataBaseItem> list = new ArrayList<>();

        @NonNull
        @Override
        public DebtDocument.ListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.debt_document_item,parent,false);
            return new ListViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DebtDocument.ListViewHolder holder, int position) {
            DataBaseItem item = list.get(position);
            holder.setValues(item);
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        void loadListItems(ArrayList<DataBaseItem> items){
            list.clear();
            list.addAll(items);
            notifyDataSetChanged();
        }
    }

    private static class ListViewHolder extends RecyclerView.ViewHolder{

        private final View holderView;

        ListViewHolder(@NonNull View itemView) {
            super(itemView);
            holderView = itemView;
        }

        void setText(int id, String text){
            TextView textView = holderView.findViewById(id);
            if (textView != null) textView.setText(text);
        }

        void setValues(DataBaseItem item){
            //TextView icon = holderView.findViewById(R.id.icon);
            setText(R.id.item_name,item.getString("item"));
            setText(R.id.item_code,item.getString("code"));
            setText(R.id.item_unit,item.getString("unit"));
            setText(R.id.item_quantity,item.getString("quantity"));
            setText(R.id.item_price,item.getString("price"));
            setText(R.id.item_sum,item.getString("sum"));
        }
    }
}
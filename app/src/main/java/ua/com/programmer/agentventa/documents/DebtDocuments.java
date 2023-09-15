package ua.com.programmer.agentventa.documents;

import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import ua.com.programmer.agentventa.R;
import ua.com.programmer.agentventa.data.DataBase;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.Utils;

public class DebtDocuments extends AppCompatActivity {

    private final Utils utils = new Utils();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debt_documents);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar!=null){
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        setTitle(R.string.attr_parent_document);

        final Intent intent = getIntent();
        String clientGuid = intent.getStringExtra("client_guid");
        if (clientGuid == null || clientGuid.equals("")){
            Toast.makeText(this, R.string.error_no_client, Toast.LENGTH_SHORT).show();
            finish();
        }

        RecyclerView recyclerView = findViewById(R.id.list_recycler);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(linearLayoutManager);

        ListAdapter listAdapter = new ListAdapter();
        recyclerView.setAdapter(listAdapter);

        DataBase db = DataBase.getInstance(this);
        listAdapter.loadCursorItems(db.getDebtDocuments(clientGuid));

        if (listAdapter.getItemCount() > 0){
            TextView noDataText = findViewById(R.id.data_indicator);
            noDataText.setVisibility(View.GONE);
        }

        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipe_layout);
        swipeRefreshLayout.setEnabled(false);
    }

    @Override
    public void onBackPressed() {
        finish();
        super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void onListItemClick(DataBaseItem item){
        Intent intent = getIntent();
        intent.putExtra("doc_id", item.getString("doc_id"));
        setResult(RESULT_OK, intent);
        finish();
    }

    private class ListAdapter extends RecyclerView.Adapter<DebtDocuments.ListViewHolder>{

        ArrayList<DataBaseItem> cursorItems = new ArrayList<>();

        ListAdapter(){

        }

        void loadCursorItems(ArrayList<DataBaseItem> items){
            cursorItems.clear();
            cursorItems.addAll(items);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public DebtDocuments.ListViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, final int i) {
            int viewId = R.layout.debt_document_list_item;
            final View view = LayoutInflater.from(viewGroup.getContext()).inflate(viewId,viewGroup,false);
            return new DebtDocuments.ListViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DebtDocuments.ListViewHolder holder, int position) {
            DataBaseItem dataBaseItem = cursorItems.get(position);
            holder.showItem(dataBaseItem);
        }

        @Override
        public int getItemCount() {
            return cursorItems.size();
        }
    }

    class ListViewHolder extends RecyclerView.ViewHolder{

        TextView itemTitle;
        TextView itemSum;

        ListViewHolder(View view){
            super(view);
            itemTitle = view.findViewById(R.id.item_title);
            itemSum = view.findViewById(R.id.item_sum);
        }

        void showItem(DataBaseItem dataBaseItem){

            itemTitle.setText(dataBaseItem.getString("doc_id"));
            itemSum.setText(utils.format(dataBaseItem.getDouble("sum"), 2));

            itemView.setOnClickListener((View v) -> onListItemClick(dataBaseItem));

        }
    }
}

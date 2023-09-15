package ua.com.programmer.agentventa.documents;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import ua.com.programmer.agentventa.MainNavigationActivity;
import ua.com.programmer.agentventa.R;
import ua.com.programmer.agentventa.settings.AppSettings;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.Utils;

@AndroidEntryPoint
public class DocumentsListFragment extends Fragment {

    @Inject AppSettings appSettings;
    @Inject Utils utils;
    private Context mContext;
    private View mFragmentView;
    private OnFragmentInteractionListener mListener;
    private SwipeRefreshLayout swipeRefreshLayout;
    private DocumentsAdapter documentsAdapter;
    private long dateBegin;
    private String filter;
    private FloatingActionButton floatingActionButton;

    public interface OnFragmentInteractionListener {
        void onDataUpdateRequest(long periodBegin, String filter, MainNavigationActivity.FragmentLoaderListener listener);
        void onSendDataRequest();
        void onListItemClick(String guid);
    }

    public DocumentsListFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dateBegin = appSettings.getCurrentDate();
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mFragmentView = inflater.inflate(R.layout.documents_list, container, false);

        swipeRefreshLayout = mFragmentView.findViewById(R.id.documents_swipe);
        swipeRefreshLayout.setOnRefreshListener(this::sendData);
        swipeRefreshLayout.setRefreshing(true);

        RecyclerView recyclerView = mFragmentView.findViewById(R.id.documents_recycler);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(mContext);
        recyclerView.setLayoutManager(linearLayoutManager);
        documentsAdapter = new DocumentsAdapter();
        recyclerView.setAdapter(documentsAdapter);

        floatingActionButton = mFragmentView.findViewById(R.id.fab);
        floatingActionButton.setOnClickListener((View v) -> onListItemClick(""));

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                onListScrolled(floatingActionButton, dy);
            }
        });

        EditText editText = mFragmentView.findViewById(R.id.edit_search);
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
                    updateList();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        return mFragmentView;
    }

    private void onListScrolled(FloatingActionButton floatingActionButton, int dy) {
        if (dy > 0 && floatingActionButton.getVisibility() == View.VISIBLE) {
            floatingActionButton.hide();
        }else if (dy < 0 && floatingActionButton.getVisibility() != View.VISIBLE){
            floatingActionButton.show();
        }
    }

    @Override
    public void onResume() {
        if (floatingActionButton != null){
            if (appSettings.getAllowCreateCashDocuments()) {
                floatingActionButton.show();
            }else {
                floatingActionButton.hide();
            }
        }
        updateList();
        super.onResume();
    }

    private void sendData(){
        if(mListener != null){
            mListener.onSendDataRequest();
        }
    }

    private void updateList(){
        appSettings.saveCurrentDate(dateBegin);
        if(mListener != null){
            mListener.onDataUpdateRequest(dateBegin, filter, this::loadListData);
        }
    }

    private void pickDate (){
        final Calendar calendar = new GregorianCalendar();
        int Y = calendar.get(Calendar.YEAR);
        int M = calendar.get(Calendar.MONTH);
        int D = calendar.get(Calendar.DATE);
        AlertDialog dialog = new DatePickerDialog(mContext, (view, year, month, dayOfMonth) -> {
            Calendar cal = new GregorianCalendar();
            cal.set(year,month,dayOfMonth,0,0);
            dateBegin = cal.getTimeInMillis()/1000;
            updateList();
        },Y,M,D);
        dialog.show();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.periodToday){
            dateBegin = utils.dateBeginOfToday();
            updateList();
        }
        if (id == R.id.periodYesterday){
            dateBegin = utils.dateBeginShiftDate(-1);
            updateList();
        }
        if (id == R.id.periodNoLimits){
            dateBegin = 0;
            updateList();
        }
        if (id == R.id.periodChoose) pickDate();

        if (id == R.id.action_search){
            EditText editText = mFragmentView.findViewById(R.id.edit_search);
            if (editText.getVisibility() == View.VISIBLE) {
                editText.setText("");
                editText.setVisibility(View.GONE);
            }else{
                editText.setVisibility(View.VISIBLE);
                editText.requestFocus();
                InputMethodManager imm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, InputMethodManager.HIDE_IMPLICIT_ONLY);
            }
        }

        return super.onOptionsItemSelected(item);
    }

    public synchronized void loadListData(ArrayList<DataBaseItem> items){
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
        if (documentsAdapter != null) documentsAdapter.loadListItems(items);
        showTotals(items);
    }

    private void showTotals(ArrayList<DataBaseItem> items) {
        if (mFragmentView == null) return;

        int documentsQuantity = items.size();

        LinearLayout emptyView = mFragmentView.findViewById(R.id.title_no_data);
        if (documentsQuantity == 0) {
            emptyView.setVisibility(View.VISIBLE);
        }else{
            emptyView.setVisibility(View.INVISIBLE);
        }

        String setTextDocuments = ""+documentsQuantity+" ";
        TextView tvDocQty = mFragmentView.findViewById(R.id.documents_qty);
        tvDocQty.setText(setTextDocuments);

        ImageView returnIcon = mFragmentView.findViewById(R.id.return_icon);
        returnIcon.setVisibility(View.GONE);
        TextView tvDocReturn = mFragmentView.findViewById(R.id.documents_return);
        tvDocReturn.setVisibility(View.GONE);

        TextView tvDocSum = mFragmentView.findViewById(R.id.documents_sum);
        tvDocSum.setText("");

        double totalIncome = 0.0;
        double totalOutcome = 0.0;
        double sum;
        //have a chance to get dataset modification
        //during iterations, so need to check possibility
        //of getting next element (see ConcurrentModificationException)
        for (int i = 0; i < documentsQuantity; i++) {
            if (items.size()<i) {
                utils.log("w","array is modified while iterated");
                return;
            }
            DataBaseItem item = items.get(i);
            sum = item.getDouble("sum");
            if (sum >= 0) {
                totalIncome = totalIncome + sum;
            }else {
                totalOutcome = totalOutcome + sum;
            }
        }
        String sumText;
        if (totalIncome != 0 && totalOutcome != 0) {
            sumText = "+" + utils.format(totalIncome, 2) + "/-" + utils.format(totalOutcome, 2);
        }else {
            sumText = utils.format(totalIncome+totalOutcome, 2);
        }
        tvDocSum.setText(sumText);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    private void onListItemClick(String guid){
        if (mListener != null) {
            mListener.onListItemClick(guid);
        }
    }

    class DocumentViewHolder extends RecyclerView.ViewHolder{

        private final TextView tvClient;
        private final TextView tvNumber;
        private final TextView tvDate;
        private final TextView tvPrice;
        private final TextView tvQuantity;
        private final TextView tvQuantityHeader;
        private final TextView tvNote;
        private final TextView tvStatus;
        private final ImageView iconStatus;
        private final ImageView iconReturn;
        private final ImageView iconCash;

        DocumentViewHolder(View view){
            super(view);
            iconStatus = view.findViewById(R.id.icon_upload);
            iconReturn = view.findViewById(R.id.icon_return);
            iconCash = view.findViewById(R.id.icon_cash);
            tvClient = view.findViewById(R.id.list_item_client);
            tvNumber = view.findViewById(R.id.list_item_number);
            tvDate = view.findViewById(R.id.list_item_date);
            tvPrice = view.findViewById(R.id.list_item_price);
            tvQuantity = view.findViewById(R.id.list_item_quantity);
            tvQuantityHeader = view.findViewById(R.id.list_item_quantity_header);
            tvNote = view.findViewById(R.id.list_item_note);
            tvStatus = view.findViewById(R.id.list_item_status);
        }

        public void setNumber(String str) {
            this.tvNumber.setText(str);
        }

        public void setDate(String str) {
            this.tvDate.setText(str);
        }

        public void setQuantity(String str) {
            if (str.isEmpty()){
                tvQuantityHeader.setText("");
            }
            this.tvQuantity.setText(str);
        }

        void setNote(String str) {
            this.tvNote.setText(str);
        }

        void setContractor(String str) {
            this.tvClient.setText(str);
        }

        public void setSum(String str) {
            this.tvPrice.setText(str);
        }

        public void setStatus(String str) {
            this.tvStatus.setText(str);
        }

        void setIconStatus(int processedFlag) {
            if (processedFlag==2) {
                iconStatus.setImageResource(R.drawable.baseline_cloud_done_24);
            }else if(processedFlag==1) {
                iconStatus.setImageResource(R.drawable.baseline_cloud_upload_24);
            }else if(processedFlag>=10) {
                iconStatus.setImageResource(R.drawable.baseline_cloud_download_24);
            }else{
                iconStatus.setImageResource(R.drawable.baseline_cloud_queue_24);
            }

            if (processedFlag == 10) {
                tvPrice.setTextColor(getResources().getColor(R.color.pinkDark));
            }else {
                tvPrice.setTextColor(getResources().getColor(R.color.secondaryText));
            }
        }

        void setIconReturn() {
            iconReturn.setVisibility(View.INVISIBLE);
        }

        void setIconCash() {
            iconCash.setVisibility(View.INVISIBLE);
        }
    }

    class DocumentsAdapter extends RecyclerView.Adapter<DocumentViewHolder>{

    private final ArrayList<DataBaseItem> listItems = new ArrayList<>();

    @SuppressLint("NotifyDataSetChanged")
    void loadListItems(ArrayList<DataBaseItem> values){
        listItems.clear();
        listItems.addAll(values);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
        public DocumentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.documents_list_item,parent,false);
            return new DocumentViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DocumentViewHolder holder, int position) {
            DataBaseItem dataBaseItem = listItems.get(position);

            holder.setDate(dataBaseItem.getString("date"));
            holder.setContractor(dataBaseItem.getString("client_description"));
            holder.setSum(utils.format(dataBaseItem.getDouble("sum"), 2));
            holder.setQuantity("");
            holder.setNote(dataBaseItem.getString("notes"));

            holder.setIconStatus(dataBaseItem.getInt("is_processed"));
            holder.setIconReturn();
            holder.setIconCash();

            if (dataBaseItem.getInt("is_sent") < 0) {
                holder.setNumber(dataBaseItem.getString("status"));
                holder.setStatus("");
            }else {
                holder.setNumber(dataBaseItem.getString("number"));
                holder.setStatus(dataBaseItem.getString("status"));
            }

            holder.itemView.setOnClickListener((View v) -> onListItemClick(dataBaseItem.getString("guid")));
        }

        @Override
        public int getItemCount() {
            return listItems.size();
        }
    }
}

package ua.com.programmer.agentventa.documents;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.graphics.Paint;
import android.os.Bundle;
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

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

import ua.com.programmer.agentventa.MainNavigationActivity;
import ua.com.programmer.agentventa.R;
import ua.com.programmer.agentventa.settings.AppSettings;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.Utils;

public class TasksListFragment extends Fragment {

    private Context mContext;
    private AppSettings appSettings;

    private View mFragmentView;
    private OnFragmentInteractionListener mListener;
    private SwipeRefreshLayout swipeRefreshLayout;
    private DocumentsAdapter documentsAdapter;
    private final Utils utils = new Utils();
    private long dateBegin = 0;
    private String filter;
    private FloatingActionButton floatingActionButton;

    public interface OnFragmentInteractionListener {
        void onDataUpdateRequest(long periodBegin, String filter, MainNavigationActivity.FragmentLoaderListener listener);
        //void onSendDataRequest();
        void onListItemClick(String guid);
    }

    public TasksListFragment(){}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        dateBegin = appSettings.getCurrentDate();
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
        updateList();
        super.onResume();
    }

    private void sendData(){
//        if(mListener != null){
//            mListener.onSendDataRequest();
//        }
        updateList();
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
        appSettings = AppSettings.getInstance(context);
        mContext = context;
        mListener = (OnFragmentInteractionListener) context;
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


    static class DocumentViewHolder extends RecyclerView.ViewHolder{

        private final TextView tvClient;
        private final TextView tvDescription;
        private final TextView tvDate;
        private final TextView tvNote;
        private final ImageView iconStatus;

        DocumentViewHolder(View view){
            super(view);
            tvClient = view.findViewById(R.id.client);
            tvDescription = view.findViewById(R.id.description);
            tvDate = view.findViewById(R.id.list_item_date);
            tvNote = view.findViewById(R.id.list_item_note);
            iconStatus = view.findViewById(R.id.icon_status);
        }

        void setValues(DataBaseItem item){
            tvDescription.setText(item.getString("description"));
            tvDate.setText(item.getString("date"));
            tvNote.setText(item.getString("notes"));

            if (item.getInt("is_done") == 1) {
                iconStatus.setImageResource(R.drawable.ic_done);
                tvDescription.setPaintFlags(tvDescription.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                tvNote.setVisibility(View.GONE);
            }else {
                iconStatus.setImageResource(R.drawable.baseline_text_snippet_24);
                tvDescription.setPaintFlags(0);
                tvNote.setVisibility(View.VISIBLE);
            }

            String clientDescription = item.getString("client_description");
            if (clientDescription.isEmpty()) {
                tvClient.setVisibility(View.GONE);
            }else{
                tvClient.setText(clientDescription);
                tvClient.setVisibility(View.VISIBLE);
            }
        }

    }

    class DocumentsAdapter extends RecyclerView.Adapter<TasksListFragment.DocumentViewHolder>{

        private final ArrayList<DataBaseItem> listItems = new ArrayList<>();

        void loadListItems(ArrayList<DataBaseItem> values){
            listItems.clear();
            listItems.addAll(values);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public TasksListFragment.DocumentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.task_list_item,parent,false);
            return new DocumentViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TasksListFragment.DocumentViewHolder holder, int position) {
            DataBaseItem dataBaseItem = listItems.get(position);
            holder.setValues(dataBaseItem);

            String itemGUID = dataBaseItem.getString("guid");
            holder.itemView.setOnClickListener((View v) -> onListItemClick(itemGUID));
        }

        @Override
        public int getItemCount() {
            return listItems.size();
        }
    }
}

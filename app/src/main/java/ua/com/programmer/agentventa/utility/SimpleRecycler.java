package ua.com.programmer.agentventa.utility;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import ua.com.programmer.agentventa.R;

public class SimpleRecycler extends RecyclerView.Adapter<SimpleViewHolder> {

    private final ArrayList<DataBaseItem> items = new ArrayList<>();
    private EventsListener<DataBaseItem> listener;

    public interface EventsListener<DataBaseItem>{
        void onListItemClick(DataBaseItem itemData);
    }

    private void onListItemClick(DataBaseItem itemData){
        if (listener !=null) listener.onListItemClick(itemData);
    }

    public void attachListener(EventsListener<DataBaseItem> listener){
        this.listener = listener;
    }

    @NonNull
    @Override
    public SimpleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.simple_list_element, parent, false);
        return new SimpleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SimpleViewHolder holder, int position) {
        holder.attachListener(this::onListItemClick);
        holder.setValues(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void loadListItems(ArrayList<DataBaseItem> newItems){
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void clearList(){
        items.clear();
        notifyDataSetChanged();
    }
}

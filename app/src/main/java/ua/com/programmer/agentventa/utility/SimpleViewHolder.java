package ua.com.programmer.agentventa.utility;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import ua.com.programmer.agentventa.R;

public class SimpleViewHolder extends RecyclerView.ViewHolder {

    private final View view;
    private EventsListener<DataBaseItem> listener;

    public interface EventsListener<DataBaseItem>{
        void onListItemClick(DataBaseItem itemData);
    }

    SimpleViewHolder(@NonNull View view){
        super(view);
        this.view = view;
    }

    public void attachListener(EventsListener<DataBaseItem> listener){
        this.listener = listener;
    }

    private void setText(int viewId, String text){
        TextView textView = view.findViewById(viewId);
        if (textView != null) textView.setText(text);
    }

    public void setValues(DataBaseItem data){

        setText(R.id.element_1, data.getString("element_1"));
        setText(R.id.element_2, data.getString("element_2"));

        if (listener != null) view.setOnClickListener(v -> listener.onListItemClick(data));
    }

}

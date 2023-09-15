package ua.com.programmer.agentventa.utility;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import ua.com.programmer.agentventa.R;

public class SimplePlaceholderFragment extends Fragment {

    private SimpleRecycler listAdapter;
    private RecyclerView recyclerView;

    public SimplePlaceholderFragment(){}

    public SimplePlaceholderFragment setAdapter(SimpleRecycler listAdapter){
        this.listAdapter = listAdapter;
        if (recyclerView != null) recyclerView.setAdapter(listAdapter);
        return this;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.simple_recycler, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        recyclerView = view.findViewById(R.id.list_recycler);
        if (recyclerView != null){
            recyclerView.setHasFixedSize(true);
            LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
            recyclerView.setLayoutManager(layoutManager);
            if (listAdapter != null) recyclerView.setAdapter(listAdapter);
        }
    }
}

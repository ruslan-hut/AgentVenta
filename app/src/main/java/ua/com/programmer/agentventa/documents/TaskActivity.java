package ua.com.programmer.agentventa.documents;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import ua.com.programmer.agentventa.R;
import ua.com.programmer.agentventa.utility.Constants;
import ua.com.programmer.agentventa.utility.Utils;

@AndroidEntryPoint
public class TaskActivity extends AppCompatActivity implements TaskModel.TaskListener {

    private static TaskModel model;
    private String guid;

    @Inject Utils utils;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.task_fragment);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar!=null){
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        setTitle(utils.getPageTitleID(Constants.DOCUMENT_TASK));

        model = new TaskModel(this);

        Intent intent = getIntent();
        guid = intent.getStringExtra("guid");
    }

    @Override
    protected void onResume() {
        model.findByGUID(guid);
        super.onResume();
    }

    @Override
    public void onDataLoaded() {

        EditText description = findViewById(R.id.edit_description);
        description.setText(model.get("description"));
        EditText notes = findViewById(R.id.edit_notes);
        notes.setText(model.get("notes"));

        TextView date = findViewById(R.id.date_created);
        date.setText(model.get("date"));

        SwitchCompat isDone = findViewById(R.id.is_done);
        isDone.setChecked(model.getBoolean("is_done"));
        isDone.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) saveTask();
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_document, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) onBackPressed();
        //if (id == R.id.delete_document) deleteDocument();
        //if (id == R.id.edit_document) {}
        if (id == R.id.save_document) saveTask();
        return super.onOptionsItemSelected(item);
    }

    private void saveTask(){
        EditText description = findViewById(R.id.edit_description);
        model.set("description",description.getText().toString());
        EditText notes = findViewById(R.id.edit_notes);
        model.set("notes",notes.getText().toString());
        SwitchCompat isDone = findViewById(R.id.is_done);
        model.setBoolean("is_done",isDone.isChecked());
        model.save();
        onBackPressed();
    }

}
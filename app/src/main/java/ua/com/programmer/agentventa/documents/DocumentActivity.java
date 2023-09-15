package ua.com.programmer.agentventa.documents;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import ua.com.programmer.agentventa.R;
import ua.com.programmer.agentventa.catalogs.Client;
import ua.com.programmer.agentventa.catalogs.ClientsActivity;
import ua.com.programmer.agentventa.data.DataBase;
import ua.com.programmer.agentventa.settings.AppSettings;
import ua.com.programmer.agentventa.utility.Constants;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.Utils;

@AndroidEntryPoint
public class DocumentActivity extends AppCompatActivity {

    @Inject Utils utils;
    @Inject DataBase database;
    private DataBaseItem documentItem;
    private boolean fiscalPayments;
    private String guid;
    private String parentDocument="";

    private final ActivityResultLauncher<Intent> openClientsList = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Intent intent = result.getData();
                String clientID;
                if (intent != null) {
                    clientID = intent.getStringExtra("clientID");
                    onClientSelected(clientID);
                }
                showDocument();
            }
    );

    private final ActivityResultLauncher<Intent> openDebtsList = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Intent intent = result.getData();
                String docID;
                if (intent != null) {
                    docID = intent.getStringExtra("doc_id");
                    if (docID != null && !docID.isEmpty()) parentDocument = docID;
                }
                showDocument();
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cash_fragment);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar!=null){
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        Intent intent = getIntent();
        guid = intent.getStringExtra("guid");
        String setClientID = intent.getStringExtra("setClientID");

        database = DataBase.getInstance(this);
        documentItem = database.getDocumentByGUID(guid);
        if (documentItem.getString("guid").isEmpty()) {
            guid = UUID.randomUUID().toString();
            documentItem.put("type", intent.getStringExtra("tag"));
            Date currentDate = new Date();
            documentItem.put("time", Integer.parseInt(String.format("%ts", currentDate)));
            documentItem.put("date", String.format(Locale.getDefault(), "%1$td-%1$tm-%1$tY %1$tH:%1$tM", currentDate));
            documentItem.put("number", database.getNewDocumentNumber());
        }else {
            parentDocument = database.readAttributeValue(guid, "parent_document");
        }

        AppSettings appSettings = AppSettings.getInstance(this);
        fiscalPayments = appSettings.readKeyBoolean(Constants.OPT_FISCAL_PAYMENTS);

        onClientSelected(setClientID);

        setTitle(utils.getPageTitleID(documentItem.getString("type")));

        showDocument();
    }

    @Override
    public void onBackPressed() {
        if (documentItem.getInt("is_processed") == 0) {
            saveDocumentState(false);
        }
        finishActivity();
        super.onBackPressed();
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
        if (id == R.id.delete_document) deleteDocument();
        if (id == R.id.edit_document) {
            if (documentItem.getInt("is_sent") == 0){
                documentItem.put("is_processed", 0);
                showDocument();
            }
        }
        if (id == R.id.save_document){
            if (documentItem.getInt("is_sent") == 0){
                saveDocumentState(true);
                if (documentItem.getInt("is_processed") == 1){
                    finishActivity();
                }
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void saveDocumentState(boolean process) {
        if (fiscalPayments){
            SwitchCompat isFiscalSwitch = findViewById(R.id.is_fiscal);
            documentItem.put("is_fiscal",isFiscalSwitch.isChecked());
        }

        if (process){
            if (documentItem.getString("client_guid").isEmpty()){
                openAlertDialog(getResources().getString(R.string.error), getResources().getString(R.string.error_no_client));
                return;
            }
            documentItem.put("is_processed", 1);
        }

        String newGuid = database.updateDocument(guid, documentItem.getValues());
        if (!newGuid.isEmpty()){
            database.saveAttributeValue(newGuid,"parent_document",parentDocument);
        }
    }

    private void finishActivity(){
        Intent intent = new Intent();
        intent.putExtra("guid", guid);
        setResult(RESULT_OK, intent);
        finish();
    }

    private void showDocument(){
        boolean isSent = documentItem.getInt("is_sent") == 1;
        int processedFlag = documentItem.getInt("is_processed");

        boolean isEditable = !isSent & processedFlag == 0;

        ImageView docIcon = findViewById(R.id.doc_icon);
        if (docIcon!=null){
            if (isSent) {
                docIcon.setImageResource(R.drawable.baseline_cloud_done_24);
            }else if (processedFlag >= 10) {
                docIcon.setImageResource(R.drawable.baseline_cloud_download_24);
            }else if (processedFlag == 1) {
                docIcon.setImageResource(R.drawable.baseline_cloud_upload_24);
            }else{
                docIcon.setImageResource(R.drawable.baseline_cloud_queue_24);
            }
        }

        TextView docNumber = findViewById(R.id.doc_number);
        docNumber.setText(documentItem.getString("number"));
        TextView docDate = findViewById(R.id.doc_date);
        docDate.setText(documentItem.getString("date"));
        TextView docClient = findViewById(R.id.doc_client);
        docClient.setText(documentItem.getString("client_description"));
        TextView docSum = findViewById(R.id.doc_total_price);
        docSum.setText(utils.format(documentItem.getDouble("sum"),2));
        TextView docNotes = findViewById(R.id.doc_notes);
        docNotes.setText(documentItem.getString("notes"));
        TextView docParent = findViewById(R.id.doc_parent_document);
        docParent.setText(parentDocument);

        LinearLayout fiscalLine = findViewById(R.id.fiscal);
        SwitchCompat isFiscalSwitch = findViewById(R.id.is_fiscal);
        if (fiscalPayments) {
            fiscalLine.setVisibility(View.VISIBLE);
            isFiscalSwitch.setChecked(documentItem.getBoolean("is_fiscal"));
            String fiscalNumber = documentItem.getString("fiscal_number");
            isFiscalSwitch.setText(fiscalNumber);
            if (!fiscalNumber.isEmpty()) isFiscalSwitch.setEnabled(false);
        }else{
            fiscalLine.setVisibility(View.GONE);
        }

        if (isEditable) {
            docNotes.setOnClickListener((View v) -> openNotesEditDialog());
            docClient.setOnClickListener((View v) -> openClients());
            docSum.setOnClickListener((View v) -> openSumEditDialog());
            docParent.setOnClickListener((View v) -> openDebtDocuments());
        }else{
            docNotes.setOnClickListener((View v) -> openNotesDialog());
        }

        if (processedFlag == 10) {
            docSum.setTextColor(getResources().getColor(R.color.pinkDark));
        }else if (processedFlag == 11){
            docSum.setTextColor(getResources().getColor(R.color.secondaryText));
        }
    }

    private void openClients () {
        Intent intent = new Intent(this, ClientsActivity.class);
        intent.putExtra("orderGUID", guid);
        openClientsList.launch(intent);
    }

    private void openDebtDocuments(){
        Intent intent = new Intent(this, DebtDocuments.class);
        intent.putExtra("client_guid", documentItem.getString("client_guid"));
        openDebtsList.launch(intent);
    }

    private void openSumEditDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.dialog_edit_sum,null);

        final EditText editText = view.findViewById(R.id.edit_text);
        String sumText = utils.format(documentItem.getDouble("sum"), 2);
        if (!sumText.equals("0.00")){
            editText.setText(sumText);
        }

        builder.setView(view);
        builder.setMessage("")
                .setTitle(getResources().getString(R.string.sum))
                .setPositiveButton(getResources().getString(R.string.save), (DialogInterface dialogInterface, int i) -> {
                    documentItem.put("sum", utils.round(editText.getText().toString(), 2));
                    showDocument();
                })
                .setNegativeButton(getResources().getString(R.string.cancel), null);

        final AlertDialog dialog = builder.create();
        Objects.requireNonNull(dialog.getWindow()).setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.show();
    }

    private void openNotesEditDialog () {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.dialog_edit_text,null);

        final EditText editText = view.findViewById(R.id.edit_text);
        editText.setText(documentItem.getString("notes"));

        builder.setView(view);
        builder.setMessage("")
                .setTitle(getResources().getString(R.string.doc_notes))
                .setPositiveButton(getResources().getString(R.string.save), (dialogInterface, i) -> {
                    documentItem.put("notes", editText.getText().toString());
                    showDocument();
                })
                .setNegativeButton(getResources().getString(R.string.cancel), null);
        final AlertDialog dialog = builder.create();
        Objects.requireNonNull(dialog.getWindow()).setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.show();
    }

    private void openNotesDialog () {
        openAlertDialog(getResources().getString(R.string.doc_notes), documentItem.getString("notes"));
    }

    private void openAlertDialog (String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message)
                .setTitle(title)
                .setPositiveButton(getResources().getString(R.string.OK), null);
        final AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void deleteDocument(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.delete_data)
                .setTitle(R.string.delete_order)
                .setPositiveButton(getResources().getString(R.string.OK), (DialogInterface dialogInterface, int i) -> {
                    String guid = documentItem.getString("guid");
                    if (guid != null){
                        database.deleteDocument(guid);
                        finish();
                    }
                })
                .setNegativeButton(R.string.cancel, null);
        final AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void onClientSelected(String clientID){
        if (clientID != null && !clientID.equals("")) {
            Client client = new Client(this, clientID);
            documentItem.put("client_guid", client.GUID);
            documentItem.put("client_description", client.description);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data != null){
            //------------------------- client chosen ---------------
            if (requestCode == 1) {
                try {
                    String clientID = data.getStringExtra("clientID");
                    onClientSelected(clientID);
                }catch (Exception getResultException){
                    utils.log("e","DocumentActivity.onActivityResult (1): "+getResultException);
                }
            }
            //------------------------- debt document chosen ---------------
            if (requestCode == 2) {
                try {
                    String docID = data.getStringExtra("doc_id");
                    if (docID != null && !docID.equals("")) {
                        parentDocument = docID;
                    }
                }catch (Exception getResultException){
                    utils.log("e","DocumentActivity.onActivityResult (2): "+getResultException);
                }
            }
        }
        showDocument();
        super.onActivityResult(requestCode, resultCode, data);
    }
}

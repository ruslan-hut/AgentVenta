package ua.com.programmer.agentventa.notifications;

import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;

import ua.com.programmer.agentventa.data.DataBase;
import ua.com.programmer.agentventa.data.DataConnector;
import ua.com.programmer.agentventa.R;
import ua.com.programmer.agentventa.utility.DataBaseItem;

public class MessageReadingActivity extends AppCompatActivity {

    private String document_guid = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message_reading);

        Notifications notifications = new Notifications(this);
        notifications.setContentTitle(R.string.notification_text_sending_message);

        String title = "";
        String body = "";

        Intent intent = getIntent();
        if (intent != null) {
            title = intent.getStringExtra("title");
            TextView msgTitle = findViewById(R.id.msg_title);
            msgTitle.setText(title);
            body = intent.getStringExtra("body");
            TextView msgBody = findViewById(R.id.msg_body);
            msgBody.setText(body);
            document_guid = intent.getStringExtra("document_guid");
            notifications.setContentText(body);
        }

        if (document_guid.equals("service_message")){

            processServiceMessage(body);

            TextView yesButton = findViewById(R.id.button_yes);
            yesButton.setText(R.string.OK);
            yesButton.setOnClickListener((View v) -> sendResponse("received"));
            TextView noButton = findViewById(R.id.button_no);
            noButton.setVisibility(View.GONE);

        }else if (!document_guid.equals("")) {

            TextView yesButton = findViewById(R.id.button_yes);
            yesButton.setOnClickListener((View v) -> sendResponse("approve"));
            TextView noButton = findViewById(R.id.button_no);
            noButton.setOnClickListener((View v) -> sendResponse("reject"));

        }else {
            LinearLayout buttonsLayout = findViewById(R.id.buttons);
            buttonsLayout.setVisibility(View.GONE);
        }

        if (title == null && body == null){
            finish();
        }

    }

    private void processServiceMessage(String body){

        DataBaseItem messageData;
        try {
            messageData = new DataBaseItem(new JSONObject(body));
        }catch (Exception e){
            messageData = new DataBaseItem();
        }

        if (messageData.hasValues()){

            TextView msgBody = findViewById(R.id.msg_body);
            msgBody.setText(messageData.getString("message"));

            String guid = messageData.getString("guid");
            String db_server = messageData.getString("db_server");

            if (!guid.isEmpty() && !db_server.isEmpty()){

                DataBaseItem connection = new DataBaseItem();
                connection.put("guid", guid);
                connection.put("description", messageData.getString("description"));
                connection.put("data_format", messageData.getString("data_format"));
                connection.put("db_server", db_server);
                connection.put("db_name", messageData.getString("db_name"));
                connection.put("db_user", messageData.getString("db_user"));
                connection.put("db_password", messageData.getString("db_password"));
                connection.put("license", "");

                DataBase dataBase = DataBase.getInstance(this);
                dataBase.saveConnectionParameters(connection);
            }

        }

    }

    private void sendResponse(String response){
        LinearLayout buttons = findViewById(R.id.buttons);
        buttons.setVisibility(View.INVISIBLE);

        ProgressBar progressBar = findViewById(R.id.progress);
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        DataConnector connector = DataConnector.getInstance(this);
        connector.addListener(this::onDataConnectorResult);
        connector.sendConfirmationResponse(response, document_guid);
    }

    private void onDataConnectorResult(DataBaseItem result){
        finish();
    }

}

package ua.com.programmer.agentventa.notifications;

import android.content.Intent;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

import ua.com.programmer.agentventa.settings.AppSettings;

public class MessageService extends FirebaseMessagingService {

    @Override
    public void onNewToken(@NonNull String s) {

        AppSettings appSettings = AppSettings.getInstance(getApplicationContext());
        appSettings.setMessageServiceToken(s);

        super.onNewToken(s);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {

        Map<String, String> messageData = remoteMessage.getData();

        if (messageData.size() > 0){

            if (!messageData.containsKey("title")) return;
            if (!messageData.containsKey("body")) return;
            if (!messageData.containsKey("document_guid")) return;

            Intent intent = new Intent(getApplicationContext(), MessageReadingActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("title", messageData.get("title"));
            intent.putExtra("body", messageData.get("body"));
            intent.putExtra("document_guid", messageData.get("document_guid"));
            startActivity(intent);

        }

        super.onMessageReceived(remoteMessage);
    }
}

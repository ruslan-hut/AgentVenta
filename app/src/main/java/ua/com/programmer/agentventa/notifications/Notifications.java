package ua.com.programmer.agentventa.notifications;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import java.util.Set;

import ua.com.programmer.agentventa.MainNavigationActivity;
import ua.com.programmer.agentventa.R;
import ua.com.programmer.agentventa.utility.Constants;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.Utils;

public class Notifications {

    private final static String LOCATIONS_CHANNEL = "locations";
    private final static String DATALOAD_CHANNEL = "dataLoader";

    private final Context context;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;

    private final String channelName;
    private String contentTitle;
    private String contentText;
    private int smallIcon;
    private final Intent notificationIntent;

    private static int notificationId;

    public Notifications(Context context){
        this.context = context;

        notificationId++;

        //default values
        channelName = DATALOAD_CHANNEL;
        contentTitle = context.getString(R.string.app_name);
        contentText = "";
        smallIcon = R.drawable.sharp_message_24;
        notificationIntent = new Intent();

        setupAllChannels();
    }

    private void setupAllChannels(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            setupNotificationChannel(LOCATIONS_CHANNEL,R.string.notification_channel_locations);
            setupNotificationChannel(DATALOAD_CHANNEL,R.string.notification_channel_data_loader);
        }
    }

    @TargetApi(26)
    private void setupNotificationChannel(String channelID,int title){
        NotificationChannel channel = new NotificationChannel(channelID,
                context.getString(title),
                android.app.NotificationManager.IMPORTANCE_DEFAULT);
        channel.setLightColor(Color.GREEN);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        getNotificationManager().createNotificationChannel(channel);
    }

    private NotificationManager getNotificationManager(){
        if (notificationManager == null){
            notificationManager = (NotificationManager) context.getSystemService(
                    Context.NOTIFICATION_SERVICE);
        }
        return notificationManager;
    }

    public void setContentTitle(int res){
        contentTitle = context.getString(res);
        updateNotification();
    }

    public void setContentText(String text){
        contentText = text;
        updateNotification();
    }

    /**
     * Compose a notification message from a data set of codes;
     * Data set contains pairs "code:number", where "code" - data type code,
     * as defined in {@link ua.com.programmer.agentventa.utility.Constants}
     * and "number" - is a number of loaded items
     *
     * @param data data set
     */
    public void setContentText(@NonNull DataBaseItem data){
        StringBuilder text = new StringBuilder();
        String packageName = context.getPackageName();
        ContentValues values = data.getValuesForDataBase();

        String timeText = "";
        long timeBegin = data.getLong(Constants.DATA_TIME_STAMP);
        if (timeBegin > 0){
            Utils utils = new Utils();
            timeText = utils.showTime(timeBegin,utils.currentTime());
        }

        Set<String> keys = values.keySet();
        String keyName;
        for (String key: keys){
            if (key.equals(Constants.DATA_TIME_STAMP)) continue;
            //if (key.equals(Constants.DATA_CLIENT_LOCATION)) continue;
            if (key.equals(Constants.DATA_CLIENT_DIRECTION)) continue;
            if (key.equals(Constants.DATA_IMAGE)) continue;

            @SuppressLint("DiscouragedApi") int resID = context.getResources().getIdentifier("data_type_"+key,"string",packageName);

            if (resID > 0) {
                keyName = context.getString(resID);
            }else {
                keyName = key;
            }

            text.append(keyName).append(": ").append(values.getAsString(key)).append("\n");
        }
        if (!timeText.isEmpty()) text.append(context.getString(R.string.time_counter)).append(": ").append(timeText);
        setContentText(text.toString());
    }

    public void setSmallIcon(int res){
        smallIcon = res;
    }

    private void updateNotification(){
        if (notificationBuilder != null){
            notificationBuilder.setContentTitle(contentTitle);
            notificationBuilder.setContentText(contentText);
            getNotificationManager().notify(notificationId, notificationBuilder.build());
        }
    }

    public void show(){
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addParentStack(MainNavigationActivity.class);
        stackBuilder.addNextIntent(notificationIntent);

        PendingIntent notificationPendingIntent;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }else{
            notificationPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        notificationBuilder = new NotificationCompat.Builder(context,channelName)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
                .setSmallIcon(smallIcon)
                .setAutoCancel(true)
                .setContentIntent(notificationPendingIntent);
        getNotificationManager().notify(notificationId, notificationBuilder.build());
    }

    public void setProgress(int max, int current){
        if (notificationBuilder != null){
            notificationBuilder.setProgress(max, current, max == 0);
            updateNotification();
        }
    }

    public void dismissProgress(){
        if (notificationBuilder != null){
            notificationBuilder.setProgress(0,0,false);
            updateNotification();
        }
    }

    public void dismiss(){
        if (notificationId > 0){
            getNotificationManager().cancel(notificationId);
        }
    }
}

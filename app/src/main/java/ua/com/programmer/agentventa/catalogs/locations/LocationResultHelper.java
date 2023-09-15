package ua.com.programmer.agentventa.catalogs.locations;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ua.com.programmer.agentventa.MainNavigationActivity;
import ua.com.programmer.agentventa.R;
import ua.com.programmer.agentventa.data.DataBase;
import ua.com.programmer.agentventa.settings.AppSettings;
import ua.com.programmer.agentventa.utility.Constants;

public class LocationResultHelper {

    private static Location lastSavedLocation;

    final private static String PRIMARY_CHANNEL = "locations";

    private final Context context;
    private final List<Location> locations;
    private final ArrayList<Location> savedLocations = new ArrayList<>();

    private NotificationManager notificationManager;
    private final DataBase db;

    //private long timeStampToday;
    private final String userID;
    private final FirebaseFirestore firestore;

    private final boolean enableNotifications;

    public LocationResultHelper(Context context, List<Location> locations){
        this.context = context;
        this.locations = locations;

        db = DataBase.getInstance(context);
        AppSettings appSettings = db.getAppSettings();
        userID = appSettings.getUserID();

        firestore = FirebaseFirestore.getInstance();

        enableNotifications = appSettings.notifyAboutLocationsUpdates();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            setupNotificationChannel();
        }
    }

    @TargetApi(26)
    private void setupNotificationChannel(){
        NotificationChannel channel = new NotificationChannel(PRIMARY_CHANNEL,
                context.getString(R.string.notification_channel_locations),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setLightColor(Color.GREEN);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        getNotificationManager().createNotificationChannel(channel);
    }

    private void setLastSavedLocation(Location location){
        lastSavedLocation.setLongitude(location.getLongitude());
        lastSavedLocation.setLatitude(location.getLatitude());
        lastSavedLocation.setAltitude(location.getAltitude());
    }

    private void saveLocationToFirestore(Location location, double distance){
        long time = location.getTime();
        long beginOfDay = time / 86400000;
        beginOfDay = beginOfDay * 86400000;

        Map<String,Object> document = new HashMap<>();
        document.put("userID",userID);
        document.put("point",new GeoPoint(location.getLatitude(),location.getLongitude()));
        document.put("date",new Date(time));
        document.put("altitude",location.getAltitude());
        document.put("latitude",location.getLatitude());
        document.put("longitude",location.getLongitude());
        document.put("speed",location.getSpeed());
        document.put("accuracy",location.getAccuracy());
        document.put("bearing",location.getBearing());
        document.put("time",time);
        document.put("distance",distance);

        firestore.collection("locations")
                .document(userID)
                .collection("time-"+beginOfDay)
                .document("time-"+time)
                .set(document);
    }

    public void saveResults(){
        if (lastSavedLocation == null){
            lastSavedLocation = db.lastSavedLocation();
        }
        for (Location location : locations){
            if (location.getAccuracy() < Constants.LOCATION_MIN_ACCURACY){
                if (lastSavedLocation != null) {

                    double distance = location.distanceTo(lastSavedLocation);

                    if (distance >= Constants.LOCATION_MIN_DISTANCE) {
                        //local database
                        db.saveLocation(location, distance);
                        savedLocations.add(location);
                        setLastSavedLocation(location);
                        //cloud database
                        saveLocationToFirestore(location, distance);
                    }

                }else {
                    db.saveLocation(location,0);
                    lastSavedLocation = location;
                    saveLocationToFirestore(location,0);
                }
            }
        }
    }

    private String getLocationResultText(){
        StringBuilder sb = new StringBuilder();
        sb.append(context.getString(R.string.location_current));
        sb.append(": ");
        if (lastSavedLocation != null) {
            sb.append(lastSavedLocation.getLatitude());
            sb.append(",");
            sb.append(lastSavedLocation.getLongitude());
        }else{
            sb.append(context.getString(R.string.location_unknown));
        }
        sb.append("\n");
        return sb.toString();
    }

    private NotificationManager getNotificationManager(){
        if (notificationManager == null){
            notificationManager = (NotificationManager) context.getSystemService(
                    Context.NOTIFICATION_SERVICE);
        }
        return notificationManager;
    }

    private Intent getNotificationIntent(){
        if (lastSavedLocation != null){
            //intent for Google Maps
            String point = lastSavedLocation.getLatitude()+","+lastSavedLocation.getLongitude();
            String uriString = "geo:"+point+"?q="+point;
            Uri uri = Uri.parse(uriString);
            Intent notificationIntent = new Intent(Intent.ACTION_VIEW,uri);
            notificationIntent.setPackage("com.google.android.apps.maps");
            if (notificationIntent.resolveActivity(context.getPackageManager()) != null){
                return notificationIntent;
            }
        }
        //default - open main activity
        return new Intent(context, MainNavigationActivity.class);
    }

    @TargetApi(26)
    private void showNotificationApi26(){
        Intent notificationIntent = getNotificationIntent();

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addParentStack(MainNavigationActivity.class);
        stackBuilder.addNextIntent(notificationIntent);

        PendingIntent notificationPendingIntent =
                stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder notificationBuilder = new Notification.Builder(context,PRIMARY_CHANNEL)
                .setContentTitle(context.getString(R.string.title_location_updates))
                .setContentText(getLocationResultText())
                .setSmallIcon(R.drawable.sharp_place_24)
                .setAutoCancel(true)
                .setContentIntent(notificationPendingIntent);

        getNotificationManager().notify(0, notificationBuilder.build());
    }

    private void showNotificationLowApi(){
        Intent notificationIntent = getNotificationIntent();

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addParentStack(MainNavigationActivity.class);
        stackBuilder.addNextIntent(notificationIntent);

        PendingIntent notificationPendingIntent =
                stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context,PRIMARY_CHANNEL)
                .setContentTitle(context.getString(R.string.title_location_updates))
                .setContentText(getLocationResultText())
                .setSmallIcon(R.drawable.sharp_place_24)
                .setAutoCancel(true)
                .setContentIntent(notificationPendingIntent);

        getNotificationManager().notify(0, notificationBuilder.build());
    }

    void showNotification(){
        if (savedLocations.size() == 0){
            return;
        }
        if (enableNotifications){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                showNotificationApi26();
            }else {
                showNotificationLowApi();
            }
        }
    }
}

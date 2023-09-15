package ua.com.programmer.agentventa;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import ua.com.programmer.agentventa.data.DataBase;
import ua.com.programmer.agentventa.settings.AppSettings;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.Utils;

@Deprecated
class StartUp {

    static void appLaunchAction(Context context){

        Thread thread = new Thread(){
            @Override
            public void run() {
                DataBase dataBase = DataBase.getInstance(context);
                AppSettings appSettings = dataBase.getAppSettings();
                ArrayList<DataBaseItem> list = dataBase.getConnections();
                if (list.size()==0) {
                    String dbName = appSettings.getDatabaseName();
                    String description = dbName;
                    String server = appSettings.getServerName();
                    String port = appSettings.getServerPort();
                    if (server.equals("demo")) {
                        dbName = "demo";
                        description = context.getResources().getString(R.string.demo_mode);
                    } else {
                        if (!server.contains(":")) server = server + ":" + port;
                    }

                    DataBaseItem connection = new DataBaseItem();
                    connection.put("guid", UUID.randomUUID().toString());
                    connection.put("description", description);
                    connection.put("data_format", appSettings.getSyncFormat());
                    connection.put("db_server", server);
                    connection.put("db_name", dbName);
                    connection.put("db_user", appSettings.getUserName());
                    connection.put("db_password", appSettings.getUserPassword());
                    connection.put("license", "");

                    dataBase.saveConnectionParameters(connection);
                    appSettings.setConnectionParameters(connection);
                    dataBase.updateDataWithConnectionID();
                }else{
                    DataBaseItem settings = dataBase.getCurrentConnectionParameters();
                    appSettings.initialiseCurrentOptions(settings.getString("options"));
                }
            }
        };
        thread.start();

    }

    static void sendLogs(Context context){

        Thread thread = new Thread(){
            @Override
            public void run() {

                String logContent = new Utils().readLogs().toString();
                if (logContent.isEmpty()) return;

                AppSettings appSettings = AppSettings.getInstance(context);

                Map<String,Object> document = new HashMap<>();
                document.put("logTime",new Date());
                document.put("baseName",appSettings.getDatabaseName());
                document.put("syncFormat",appSettings.getSyncFormat());
                document.put("serverAddress",appSettings.getServerName());
                document.put("serverUser",appSettings.getUserName());
                document.put("appVersion",BuildConfig.VERSION_NAME);
                document.put("log",logContent);

                FirebaseFirestore firestore = FirebaseFirestore.getInstance();
                firestore.collection("dumps")
                        .document(appSettings.getUserID())
                        .set(document);
            }
        };
        thread.start();

    }

    static void cloudMaintenance(Context context){

        AppSettings appSettings = AppSettings.getInstance(context);
        Utils utils = new Utils();

        //saved timestamp of last maintenance
        long llt = appSettings.getLastLoginTime();
        //maintenance only once per day
        if (utils.dateBeginOfToday() <= utils.dateBeginOfDay(llt)) return;

        utils.debug("Let's do some cleanup...");
        Thread thread = new Thread(){
            @Override
            public void run() {
                FirebaseFirestore firestore = FirebaseFirestore.getInstance();

                appSettings.setLastLoginTime(utils.currentTime());
                appSettings.setDirectionsRequestsCounter(0);

                long timeToDelete = utils.dateBeginShiftDate(-60); //timestamp in seconds
                Date dateEnd = new Date(timeToDelete*1000);

                //*********************************************************
                // delete inactive user IDs (no login for past 60 days)
                //*********************************************************
                final CollectionReference collection = firestore.collection("users");
                collection.whereLessThanOrEqualTo("loginTime",dateEnd)
                        .get()
                        .addOnCompleteListener((@NonNull Task<QuerySnapshot> task) -> {
                            if (task.isSuccessful() && task.getResult() != null){
                                int counter = 0;
                                for (QueryDocumentSnapshot documentSnapshot: task.getResult()){
                                    documentSnapshot.getReference().delete();
                                    counter++;
                                }
                                if (counter > 0) {
                                    utils.debug("deleting inactive IDs: "+counter+" records deleted");
                                }
                            }
                        }).addOnFailureListener((@NonNull Exception e) ->
                        utils.warn("delete inactive IDs error: "+e));

                //*********************************************************
                // delete old log dumps
                //*********************************************************
                final CollectionReference dumps = firestore.collection("dumps");
                dumps.whereLessThanOrEqualTo("logTime",dateEnd)
                        .get()
                        .addOnCompleteListener((@NonNull Task<QuerySnapshot> task) -> {
                            if (task.isSuccessful() && task.getResult() != null){
                                int counter = 0;
                                for (QueryDocumentSnapshot documentSnapshot: task.getResult()){
                                    documentSnapshot.getReference().delete();
                                    counter++;
                                }
                                if (counter > 0) {
                                    utils.debug("deleting old dumps: "+counter+" records deleted");
                                }
                            }
                        }).addOnFailureListener((@NonNull Exception e) ->
                                utils.warn("delete dumps error: "+e));

                //*********************************************************
                // delete old locations records
                //*********************************************************
                if (appSettings.getLocationServiceEnabled()){
                    final DocumentReference documentReference = firestore.collection("locations").document(appSettings.getUserID());
                    for (int i=0; i<300; i++){
                        long shift = i * 86400;
                        String collectionName = "time-"+(timeToDelete - shift)+"000";
                        CollectionReference dayCollection = documentReference.collection(collectionName);
                        dayCollection.get()
                                .addOnCompleteListener((@NonNull Task<QuerySnapshot> task) -> {
                                    if (task.isSuccessful() && task.getResult() != null){
                                        int counter=0;
                                        for (QueryDocumentSnapshot documentSnapshot: task.getResult()){
                                            documentSnapshot.getReference().delete();
                                            counter++;
                                        }
                                        if (counter>0){
                                            utils.debug("deleting locations: "+counter+" records deleted");
                                        }
                                    }
                                }).addOnFailureListener((@NonNull Exception e) ->
                                utils.warn("delete locations error: "+e));
                    }
                }
            }
        };
        thread.start();

    }

}

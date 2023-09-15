package ua.com.programmer.agentventa.catalogs.locations;

import android.content.Context;
import android.location.Location;

import androidx.annotation.NonNull;

import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import org.json.JSONObject;

import ua.com.programmer.agentventa.settings.AppSettings;
import ua.com.programmer.agentventa.utility.Constants;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

class DirectionsHelper {

    private static final String DIRECTIONS_API_URL = "https://maps.googleapis.com/maps/api/directions/json";

    private final Context context;
    private final String mapsApiKey;
    private static final ArrayList<DataBaseItem> locations = new ArrayList<>();
    private static final ArrayList<DataBaseItem> route = new ArrayList<>();

    private final Utils utils = new Utils();
    private final JsonParser jsonParser = new JsonParser();
    private static String userID;
    private static long time;
    private int pointIndex;
    private boolean onlyLocations;
    private boolean disableAPIRequests;

    private final AppSettings appSettings;
    private long requestsCounter;
    private CollectionReference collectionReference;
    private RequestQueue requestQueue;

    private final DirectionsHelperListener listener;

    DirectionsHelper(Context context, String key){
        this.context = context;
        listener = (DirectionsHelperListener) context;
        mapsApiKey = key;
        appSettings = AppSettings.getInstance(context);
        requestsCounter = appSettings.getDirectionsRequestsCounter();
        disableAPIRequests = appSettings.disableDirectionsAPI();
    }

    public interface DirectionsHelperListener{
        void onRouteLoaded(ArrayList<DataBaseItem> route);
        void onCalculationProgressUpdate(int progress);
        void onAPILimitWarn();
    }

    private void setCollectionReference(){
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        collectionReference = firestore.collection("locations")
                .document(userID)
                .collection("time-"+time);
    }

    private void returnResult(){
        if (requestsCounter > 0) {
            appSettings.setDirectionsRequestsCounter(requestsCounter);
            disableAPIRequests = appSettings.disableDirectionsAPI();
        }

        if (listener != null){

            if (route.size() == 0 && locations.size() > 0) {

                listener.onRouteLoaded(locations);

            }else {

                listener.onRouteLoaded(route);
            }

        }
    }

    private void returnLocations(){
        if (listener != null){
            listener.onRouteLoaded(locations);
        }
    }

    private void loadRouteData(){

        collectionReference
                .document("route")
                .get()
                .addOnCompleteListener((@NonNull Task<DocumentSnapshot> task) ->{
                    boolean validDataReceived = false;

                    if (task.isSuccessful() && task.getResult() != null) {

                        DocumentSnapshot document = task.getResult();
                        if (document.contains("encoded")){
                            try {
                                JSONObject routeData = new JSONObject(Objects.requireNonNull(document.getString("encoded")));
                                if (jsonParser.validDirectionsResponse(routeData)) {
                                    validDataReceived = true;
                                    route.addAll(jsonParser.decodeDirectionsResponse(routeData));
                                    returnResult();
                                }
                            }catch (Exception jsonException){
                                utils.error("DirectionsHelper.loadRouteData: get json data: "+jsonException.toString());
                            }
                        }

                    }

                    if (!validDataReceived) loadPointsData();

                })
                .addOnFailureListener((@NonNull Exception e) -> loadPointsData());

    }

    private void loadPointsData(){

        collectionReference
                .orderBy("time",Query.Direction.ASCENDING)
                .get()
                .addOnCompleteListener((@NonNull Task<QuerySnapshot> task) -> {
                    if (task.isSuccessful() && task.getResult() != null){
                        for (QueryDocumentSnapshot document: task.getResult()){
                            DataBaseItem item = new DataBaseItem();
                            item.put("time", document.getLong("time"));
                            item.put("latitude", document.getDouble("latitude"));
                            item.put("longitude", document.getDouble("longitude"));
                            item.put("distance", document.getDouble("distance"));
                            item.put("speed", document.getDouble("speed"));
                            item.put("date", utils.dateLocal(item.getLong("time")/1000));
                            item.put("selected",1);
                            locations.add(item);
                        }
                    }
                    if (onlyLocations) {
                        returnLocations();
                    }else {
                        calculateRoute();
                    }
                })
                .addOnFailureListener((@NonNull Exception e) -> returnResult());

    }

    private void calculateRoute(){
        if (locations.size() == 0 || disableAPIRequests){
            returnResult();
            return;
        }

        ArrayList<DataBaseItem> waypoints = new ArrayList<>();
        Location lastLocation = new Location("fused");
        int lastIndex = locations.size()-1;

        if (pointIndex < lastIndex){

            for (int i=pointIndex; i<=lastIndex; i++){
                DataBaseItem pointData = locations.get(i);

                if (i==pointIndex || i==lastIndex) {
                    //always add first and last points
                    waypoints.add(pointData);
                    lastLocation = pointData.getLocation();
                }else{
                    Location currentLocation = pointData.getLocation();
                    if (currentLocation.distanceTo(lastLocation) > Constants.WAYPOINTS_MIN_DISTANCE) {
                        waypoints.add(pointData);
                        lastLocation = pointData.getLocation();
                    }
                }

                if (waypoints.size() > Constants.WAYPOINTS_QUANTITY || i==lastIndex) {
                    pointIndex = i;
                    break;
                }
            }

        }

        if (waypoints.size() > 1) {

            //show progress and make calculation request

            if (listener != null) {
                int progress = pointIndex * 100 / locations.size();
                listener.onCalculationProgressUpdate(progress);
            }
            requestRouteData(waypoints);
            return;

        }else {
            route.add(locations.get(lastIndex));
        }

        utils.debug("Calculated route: points: "+locations.size()+"; counter: "+requestsCounter);

        if (route.size() > 0) saveRoute();
        returnResult();

    }

    /**
     * Entrance point for calling Directions API helper
     *
     * @param user user GUID
     * @param timeValue time for a day to be loaded
     */
    void recalculateRoute(String user, long timeValue){
        onlyLocations = false;

        if (userID == null) userID = user;
        if (time == 0) time = timeValue;

        userID = user;
        time = timeValue;
        setCollectionReference();

        locations.clear();
        route.clear();
        pointIndex = 0;

        if (disableAPIRequests) {
            if (listener != null) listener.onAPILimitWarn();
            //load without recalculation
            loadRouteData();
        }else{
            loadPointsData();
        }

    }

    void getRoute(String user, long timeValue){
        onlyLocations = false;
        if (userID == null) userID = user;
        if (time == 0) time = timeValue;

        if (listener != null && disableAPIRequests) listener.onAPILimitWarn();

        if (userID.equals(user) && time == timeValue && route.size() > 0) {
            returnResult();
        }else{
            userID = user;
            time = timeValue;
            setCollectionReference();

            locations.clear();
            route.clear();
            pointIndex = 0;

            loadRouteData();
        }

    }

    void getLocations(String user, long timeValue){
        onlyLocations = true;
        if (userID == null) userID = user;
        if (time == 0) time = timeValue;

        if (userID.equals(user) && time == timeValue && locations.size() > 0) {
            returnLocations();
        }else {
            userID = user;
            time = timeValue;
            setCollectionReference();

            locations.clear();
            route.clear();
            pointIndex = 0;

            loadPointsData();
        }
    }

    private void requestRouteData(ArrayList<DataBaseItem> waypoints){
        if (requestQueue == null){
            requestQueue = Volley.newRequestQueue(context);
        }

        DataBaseItem originItem = waypoints.get(0);
        DataBaseItem destinationItem = waypoints.get(waypoints.size()-1);

        String url = DIRECTIONS_API_URL;
        url= url+"?origin=";
        url= url+originItem.getDouble("latitude")+",";
        url= url+originItem.getDouble("longitude");
        url= url+"&destination=";
        url= url+destinationItem.getDouble("latitude")+",";
        url= url+destinationItem.getDouble("longitude");

        if (waypoints.size() > 2){
            StringBuilder builder = new StringBuilder();
            builder.append("&waypoints=");
            for (int i=0; i<waypoints.size(); i++){
                DataBaseItem item = waypoints.get(i);
                builder.append(item.getDouble("latitude")).append(",");
                builder.append(item.getDouble("longitude"));
                if (i < waypoints.size()-1) builder.append("|");
            }
            url = url+builder.toString();
        }

        url= url+"&key="+mapsApiKey;

        JsonObjectRequest request = new JsonObjectRequest(url,
                null,
                (JSONObject response) ->{
                    //utils.debug(response.toString());
                    if (jsonParser.validDirectionsResponse(response)){
                        route.addAll(jsonParser.decodeDirectionsResponse(response));
                    }
                    calculateRoute();
                },
                (VolleyError error) -> {
                    utils.error("DirectionsHelper.requestRouteData: "+error.toString());
                    route.clear();
                    returnResult();
                });
        requestQueue.add(request);
        requestsCounter = requestsCounter + 1;
    }

    private void saveRoute(){
        JSONObject document = jsonParser.encodeRouteData(route);
        if (document != null){
            Map<String, Object> saveDocument = new HashMap<>();
            saveDocument.put("encoded",document.toString());
            collectionReference.document("route").set(saveDocument);
        }
    }
}

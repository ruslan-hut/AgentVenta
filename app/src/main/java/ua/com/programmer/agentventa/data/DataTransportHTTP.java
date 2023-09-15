package ua.com.programmer.agentventa.data;

import android.util.Base64;

import com.android.volley.AuthFailureError;
import com.android.volley.Cache;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Network;
import com.android.volley.NetworkError;
import com.android.volley.NetworkResponse;
import com.android.volley.NoConnectionError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import ua.com.programmer.agentventa.documents.Order;
import ua.com.programmer.agentventa.utility.Constants;
import ua.com.programmer.agentventa.utility.ContextHolder;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.Utils;

/**
 * Utility class for performing http requests
 */
public class DataTransportHTTP {

    /** Volley queue */
    private static RequestQueue requestQueue;

    /** parameters for http service connection */
    private DataBaseItem connectionParameters;

    /** array with received user permissions and app parameters */
    private DataBaseItem options = new DataBaseItem();

    /** current transport state, if not empty - error occurred */
    private String errorCode;

    private final Listener<ArrayList<DataBaseItem>> listener;
    private final ErrorListener errorListener;
    private final Utils utils = new Utils();
    private final ContextHolder holder;

    private String baseURL = "";
    private String TOKEN = "";
    private int tokenRequestCounter;
    private int requestCounter = 0;
    private String updateMode;
    private long time;
    private boolean makeDiffRequest = false;

    private void onRequestFinished(Request<?> request, int event) {

        if (event == RequestQueue.RequestEvent.REQUEST_QUEUED) requestCounter++;
        if (event == RequestQueue.RequestEvent.REQUEST_FINISHED) requestCounter--;

        //make differential request when all documents are sent and results are received
        if (requestCounter <=0 && makeDiffRequest){
            makeDataRequest("diff",0);
            makeDiffRequest = false;
            return;
        }
        //send request is finished only if there was no error
        if (requestCounter <= 0 && listener != null && errorCode.isEmpty()){
            requestQueue.stop();
            utils.debug("Request finished; working time: "+utils.showTime(time, utils.currentTime()));
            ArrayList<DataBaseItem> result = new ArrayList<>();
            result.add(new DataBaseItem(Constants.DATA_REQUEST_FINISHED));
            listener.onResult(result);
        }
    }

    /** Callback interface for delivering parsed responses. */
    public interface Listener<T>{
        void onResult(T result);
    }

    /** Callback interface for delivering error responses. */
    public interface ErrorListener{
        void onError(String error);
    }

    public DataTransportHTTP(ContextHolder holder, Listener<ArrayList<DataBaseItem>> listener, ErrorListener errorListener){
        this.holder = holder;

        Network network = new BasicNetwork(new HurlStack());
        Cache cache = new DiskBasedCache(holder.getContext().getCacheDir(), 1024*1024*50);
        requestQueue = new RequestQueue(cache,network);
        requestQueue.addRequestEventListener(this::onRequestFinished);
        requestQueue.getCache().clear();
        requestQueue.start();

        connectionParameters = new DataBaseItem();

        this.listener = listener;
        this.errorListener = errorListener;

        tokenRequestCounter = 0;
        errorCode = "";
    }

    /**
     * Headers for http requests
     * @return formed authentication headers
     */
    private HashMap<String, String> authHeaders(){
        HashMap<String, String> headers = new HashMap<>();
        String credentials = connectionParameters.getString("db_user").trim()+":"+connectionParameters.getString("db_password").trim();
        String auth = "Basic " + Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
        headers.put("Authorization", auth);
        return headers;
    }

    /**
     * Setting connection parameters
     * @param parameters set of parameters
     */
    public void setConnectionParameters(DataBaseItem parameters){
        connectionParameters = parameters;
        baseURL = connectionParameters.getString("url")+"hs/dex";
    }

    /**
     * Stops request and sends error to a listener
     * @param error string error code
     */
    void stopQueueAndExit(String error){
        requestQueue.stop();
        utils.debug("Request stopped on error; working time: "+utils.showTime(time, utils.currentTime()));
        errorCode = error;
        errorListener.onError(error);
    }

    /**
     * Logging of Volley errors
     * @param url request url
     * @param type type of request that gives an error
     * @param error Volley error
     */
    void parseError(String url, String type, VolleyError error){
        String message = error.getLocalizedMessage();
        String errorCode = "";
        if (error instanceof ServerError) {
            message = "server error; "+message;
            errorCode = Constants.CONNECTION_ERROR;
        }else if (error instanceof NoConnectionError){
            message = "no connection; "+message;
            errorCode = Constants.CONNECTION_ERROR;
        }else if (error instanceof NetworkError){
            message = "network error; "+message;
            errorCode = Constants.CONNECTION_ERROR;
        }else if (error instanceof AuthFailureError){
            message = "authentication failed; user: "+connectionParameters.getString("db_user");
            errorCode = Constants.ACCESS_DENIED;
        }else if (error instanceof TimeoutError){
            message = "connection timed out";
            errorCode = Constants.CONNECTION_ERROR;
        }
        utils.error("Request: "+type+" "+url);
        utils.error("Volley error: "+message);
        if (!errorCode.isEmpty()) stopQueueAndExit(errorCode);
    }

    /**
     * Public method to perform data updates
     * @param mode mode of updates - update all, send documents
     */
    public void update(String mode){
        time = utils.currentTime();
        utils.debug("Request started; mode: "+mode);
        options.clear();
        updateMode = mode;
        if (updateMode.equals(Constants.UPDATE_CONFIRM)) {
            makeConfirmationRequest(connectionParameters.getString("confirmResponse"),
                    connectionParameters.getString("confirmGUID"));
        }else if (updateMode.equals(Constants.UPDATE_PRINT)) {
            getPrintingForm(connectionParameters.getString("confirmGUID"));
        }else {
            makeTokenRequest();
        }
    }

    /**
     * Sending user response on push message.
     *
     * @param responseCode code of response
     * @param guid document GUID
     */
    void makeConfirmationRequest(String responseCode, String guid){
        final String url = baseURL + "/confirm/" + responseCode + "/" + guid;
        final JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                (JSONObject response) -> {},
                (VolleyError error) -> parseError(url,"CONFIRM",error)) {
            @Override
            public Map<String, String> getHeaders() {
                return authHeaders();
            }
        };
        requestQueue.add(request);
    }

    void getPrintingForm(String guid) {
        final String url = baseURL + "/print/" + guid;
        final JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                this::readPrintData,
                (VolleyError error) -> parseError(url,"PRINT",error)) {
            @Override
            public Map<String, String> getHeaders() {
                return authHeaders();
            }
        };
        requestQueue.add(request);
    }

    /**
     * Request new token for current userID
     */
    private void makeTokenRequest(){
        tokenRequestCounter++;
        if (tokenRequestCounter > 5) {
            stopQueueAndExit(Constants.CONNECTION_ERROR);
        }else{
            final String url = baseURL + "/check/" + connectionParameters.getString("userID");
            final JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                    this::readTokenResponse,this::onTokenRequestError)
            {
                @Override
                public Map<String, String> getHeaders() {
                    return authHeaders();
                }
            };
            request.setRetryPolicy(new DefaultRetryPolicy(5000,3,1.5f));
            requestQueue.add(request);
        }
    }

    /**
     * Analyse error on token request
     * @param error Volley error
     */
    private void onTokenRequestError(VolleyError error){
        if (error != null) {
            final NetworkResponse response = error.networkResponse;
            if (response != null) {
                final int statusCode = response.statusCode;
                if (statusCode == HttpURLConnection.HTTP_MOVED_PERM || statusCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                    if (response.headers != null) {
                        final String newUrl = response.headers.get("Location");
                        utils.debug("Redirected to: "+newUrl);
                    }
                    stopQueueAndExit(Constants.CONNECTION_ERROR);
                    return;
                }
            }
            parseError(baseURL,"CHECK",error);
        }
        if (error instanceof TimeoutError || error instanceof NetworkError) {
            //retry if timeout or some network issues
            makeTokenRequest();
        }else {
            //exit on permanent failure
            stopQueueAndExit(Constants.CONNECTION_ERROR);
        }
    }

    /**
     * Reading user permissions and app parameters from server.
     * Available parameters in response:
     *  token
     *  write
     *  read
     *  locations
     *  clients_locations
     *  clients_directions
     *  clients_goods
     *  currency
     *  lastLocationTime
     *  requireDeliveryDate
     *  watchList
     *  license
     *  checkOrderLocation
     *  allowPriceTypeChoose
     *  showClientPriceOnly
     *  setClientPrice
     *  loadImages
     *  WARNING: After reading response, continues or stops queue
     *
     * @param response JSON object with received parameters
     */
    void readTokenResponse(JSONObject response){
        options = new DataBaseItem(response);
        TOKEN = options.getString("token");
        if (!options.getBoolean("read") || TOKEN.isEmpty()) {
            stopQueueAndExit(Constants.ACCESS_DENIED);
        }else{

            //return JSON options set to a listener for saving 'em in database
            if (listener != null){
                DataBaseItem optionsData = new DataBaseItem(Constants.DATA_OPTIONS);
                optionsData.put("options",response.toString());
                ArrayList<DataBaseItem> result = new ArrayList<>();
                result.add(optionsData);
                listener.onResult(result);
            }

            continueDataRequest();
        }
    }

    /**
     * Continues request after receiving token response
     */
    void continueDataRequest(){
        if (!errorCode.isEmpty()) return;

        ArrayList<String> queue = new ArrayList<>();
        queue.add("goods");
        queue.add("clients");
        queue.add("debts");
        if (options.getBoolean("clients_locations")) queue.add("clients_locations");
        if (options.getBoolean("clients_directions")) queue.add("clients_directions");
        if (options.getBoolean("clients_goods")) queue.add("clients_goods");
        if (options.getBoolean("loadImages")) queue.add("images");
        if (options.getBoolean(Constants.OPT_COMPETITOR_PRICE)) queue.add(Constants.DATA_COMPETITOR_PRICE);

        if (updateMode.equals(Constants.UPDATE_ALL)) {
            sendDocument();
            for (String dataType : queue) {
                makeDataRequest(dataType, 0);
            }
        }else if (updateMode.equals(Constants.UPDATE_SEND_DOCUMENTS)){
            sendDocument();
            makeDiffRequest = options.getBoolean("differentialUpdates");
        }

    }

    /**
     * Performs GET request for a given data type
     *
     * @param dataType code of requested data type
     * @param elementNumber element number for sequential loading
     */
    void makeDataRequest(String dataType, int elementNumber){

        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(baseURL);
        urlBuilder.append("/get/");
        urlBuilder.append(dataType);
        urlBuilder.append("/");
        urlBuilder.append(TOKEN);

        if (elementNumber > 0) urlBuilder.append("-more").append(elementNumber);

        final String url = urlBuilder.toString();

        final JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                (JSONObject response) -> {
                    try {
                        if (response.has("data")){
                            JSONArray array = response.getJSONArray("data");
                            readJSONArray(array);
                        }
                        if (response.has("more")){
                            int nextElement = response.getInt("more");
                            if (nextElement > elementNumber) {
                                makeDataRequest(dataType, nextElement);
                            }else{
                                utils.debug("wrong 'more' parameter in response; "+dataType+"; after "+elementNumber+" got "+nextElement);
                            }
                        }
                    } catch (JSONException ex) {
                        utils.error("GET "+dataType+"; wrong JSON; "+ex);
                    }
                },
                (VolleyError error) -> parseError(url,"GET "+dataType,error)) {
            @Override
            public Map<String, String> getHeaders() {
                return authHeaders();
            }
        };
        request.setRetryPolicy(new DefaultRetryPolicy(20000,3,1.5f));
        requestQueue.add(request);
    }

    /**
     * GET request for a debt document content
     *
     * @param documentType type of a document
     * @param documentGUID document GUID
     */
    void makeDocumentRequest(String documentType, String documentGUID){

        final String url = baseURL+"/document/"+documentType+"/"+documentGUID+"/"+TOKEN;

        final JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                (JSONObject response) -> {
                    try {
                        if (response.has("content")) {
                            DataBaseItem document = new DataBaseItem();
                            document.put("type",Constants.DATA_DEBT_DOCUMENT);
                            document.put("guid",documentGUID);
                            document.put("content",response.getString("content"));
                            ArrayList<DataBaseItem> result = new ArrayList<>();
                            result.add(document);
                            listener.onResult(result);
                        }
                    }catch (JSONException ex){
                        utils.error("GET Document "+documentType+"; wrong JSON; "+ex);
                    }

                },
                (VolleyError error) -> parseError(url,"GET Document "+documentType,error)) {
            @Override
            public Map<String, String> getHeaders() {
                return authHeaders();
            }
        };
        request.setRetryPolicy(new DefaultRetryPolicy(10000,3,1.5f));
        requestQueue.add(request);
    }

    /**
     * Parsing JSON array to result array that returns to a listener
     *
     * @param array array to parse
     * @throws JSONException thrown exception
     */
    void readJSONArray(JSONArray array) throws JSONException{
        ArrayList<DataBaseItem> result = new ArrayList<>();

        for (int i=0; i < array.length(); i++){
            JSONObject item = array.getJSONObject(i);
            DataBaseItem dataBaseItem = new DataBaseItem(item);

            // if we got a debt document, try to get it's content
            if (dataBaseItem.getString("value_id").equals("debt") && dataBaseItem.getString("has_content").equals("1")){
                makeDocumentRequest(dataBaseItem.getString("doc_type"),dataBaseItem.getString("doc_guid"));
            }

            result.add(dataBaseItem);
            if (result.size() >= 5000){
                listener.onResult(result);
                result.clear();
            }
        }
        if (result.size() > 0){
            listener.onResult(result);
        }
    }

    /**
     * Send given documents to a data server
     */
    private void sendDocument(){
        if (!options.getBoolean("write")) return;
        DataBase dataBase = DataBase.getInstance(holder.getContext());

        ArrayList<DataBaseItem> ordersData = dataBase.getOrdersForUpload();
        for (DataBaseItem orderData: ordersData){
            orderData.setType(Constants.DOCUMENT_ORDER);
            Order order = new Order(holder.getContext(),orderData.getString("guid"));
            makePostRequest(order.getAsJSON(),orderData);
        }

        ArrayList<DataBaseItem> documents = dataBase.getDocumentsForUpload();
        for (DataBaseItem documentData: documents){
            documentData.setType(Constants.DOCUMENT_CASH);
            JSONObject jsonObject = documentData.getAsJSON();
            String parentDocument = dataBase.readAttributeValue(documentData.getString("guid"),"parent_document");
            try {
                jsonObject.put("parent_document", parentDocument);
            }catch (Exception ex){
                utils.warn("parent document json: "+ex);
            }
            makePostRequest(jsonObject, documentData);
        }

        if (options.getBoolean("editLocations")){
            JSONObject clientsLocations = dataBase.getClientsLocationsForUpload();
            if (clientsLocations != null)
                makePostRequest(clientsLocations, new DataBaseItem(Constants.DATA_CLIENT_LOCATION));
        }

        long lastTimeSentLocation = options.getLong("lastLocationTime");
        if (options.getBoolean("locations") && lastTimeSentLocation > 0){
            JSONObject locations = dataBase.getLocationsForUpload(lastTimeSentLocation);
            if (locations != null)
                makePostRequest(locations, new DataBaseItem(Constants.DATA_LOCATION));
        }

        if (options.getBoolean(Constants.OPT_COMPETITOR_PRICE)){
            JSONObject list = dataBase.getCompetitorPricesForUpload();
            if (list != null)
                makePostRequest(list, new DataBaseItem(Constants.DATA_COMPETITOR_PRICE));
        }

        if (options.getBoolean("sendPushToken")){
            String pushToken = dataBase.getAppSettings().getMessageServiceToken();
            if (pushToken != null && !pushToken.isEmpty()){
                DataBaseItem item = new DataBaseItem(Constants.DATA_PUSH_TOKEN);
                item.put("data", pushToken);
                item.put("token", TOKEN);
                makePostRequest(item.getAsJSON(), item);
            }
        }

    }

    /**
     * Send data to a server with POST request
     * @param jsonObject object to send
     * @param item DataBaseItem with object data
     */
    private void makePostRequest(JSONObject jsonObject, DataBaseItem item){
        final String url = baseURL + "/post/" + TOKEN;

        final JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, jsonObject,
                (JSONObject response) -> {

                    DataBaseItem responseData = new DataBaseItem(response);
                    String result = responseData.getString("result");
                    String status = responseData.getString("status");
                    String error = responseData.getString("error");

                    if (!result.equals("ok") && !result.equals("error")) {
                        utils.warn("Unexpected value in \"result\": " + result);
                    }else{
                        DataBaseItem document = new DataBaseItem();
                        document.put("type", Constants.DATA_DOCUMENT_SENDING_RESULT);
                        document.put("saved_data_type", item.type());
                        document.put("guid", item.getString("guid"));
                        document.put("result", result);
                        document.put("status", status);
                        document.put("error", error);
                        ArrayList<DataBaseItem> resultArray = new ArrayList<>();
                        resultArray.add(document);
                        listener.onResult(resultArray);
                        if (!error.isEmpty() && !error.equals("null")) utils.warn("Document sending error: "+error+"; type: "+item.type());
                    }

                }, (VolleyError error) -> parseError(url,"POST "+item.type(),error))
        {
            @Override
            public Map<String, String> getHeaders() {
                return authHeaders();
            }
        };
        request.setRetryPolicy(new DefaultRetryPolicy(10000,3,1.5f));
        requestQueue.add(request);
    }

    private void readPrintData(JSONObject response) {
        DataBaseItem responseData = new DataBaseItem(response);
        if (responseData.getString("result").equals("OK")) {

            String fileName = responseData.getString("doc_guid")+".pdf";
            FileOutputStream fileOutputStream;
            String fileData = responseData.getString("data");

            File cacheDir = holder.getContext().getCacheDir();

            try {
                File[] listFiles = cacheDir.listFiles();
                if (listFiles != null) {
                    for (File pdfFile : listFiles) {
                        String cacheFileName = pdfFile.getName();
                        if (pdfFile.isFile() && cacheFileName.contains(".pdf")) {
                            if (pdfFile.delete()) {
                                utils.debug("Deleted temporary file: "+cacheFileName);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                utils.error("Cache file delete error; "+e);
            }

            try {

                File printFile = new File(cacheDir, fileName);

                fileOutputStream = new FileOutputStream(printFile.getAbsolutePath());
                fileOutputStream.write(Base64.decode(fileData, Base64.DEFAULT));
                fileOutputStream.close();

                DataBaseItem document = new DataBaseItem();
                document.put("type",Constants.DATA_PRINT);
                document.put("file",fileName);
                ArrayList<DataBaseItem> result = new ArrayList<>();
                result.add(document);
                listener.onResult(result);

            }catch (Exception e){
                utils.error("Print file save error; "+e);
            }

        }
    }
}

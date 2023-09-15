package ua.com.programmer.agentventa.data;

import android.content.Context;

import java.util.ArrayList;

import ua.com.programmer.agentventa.settings.AppSettings;
import ua.com.programmer.agentventa.utility.Constants;
import ua.com.programmer.agentventa.utility.ContextHolder;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.Utils;

/**
 * Connecting to external sources to receive and send data
 */
public class DataConnector {

    private static DataConnector connector;
    private static AppSettings appSettings;
    private static DataBase dataBase;

    private final ArrayList<DataBaseItem> queue = new ArrayList<>();
    private final ContextHolder holder;
    private final Utils utils = new Utils();
    private final DataBaseItem resultData = new DataBaseItem(Constants.DATA_REQUEST_RESULT);
    private long timeStamp = 0;
    private String updateMode = "";

    private String confirmResponse = "";
    private String confirmGUID = "";

    private ResultListener listener;
    private Progress progress;

    public interface ResultListener{
        void onResult(DataBaseItem result);
    }
    public interface Progress{
        void onProgressUpdate(int max, int current);
    }

    private DataConnector(Context context){
        holder = new ContextHolder(context);
    }

    public static DataConnector getInstance(Context context){
        if (connector == null) connector = new DataConnector(context);
        if (dataBase == null) dataBase = DataBase.getInstance(context);
        if (appSettings == null) appSettings = AppSettings.getInstance(context);

        return connector;
    }

    public void addListener(ResultListener listener){
        this.listener = listener;
    }

    public void addProgressListener(Progress progress){
        this.progress = progress;
    }

    /**
     * Convenient way to receive and send all possible data
     */
    public void updateAll(){
        updateMode = Constants.UPDATE_ALL;
        callTransportForData();
    }

    /**
     * Convenient way to send data to a server
     */
    public void sendData(){
        updateMode = Constants.UPDATE_SEND_DOCUMENTS;
        callTransportForData();
    }

    /**
     * Convenient way to send a user response on push message.
     */
    public void sendConfirmationResponse(String response, String guid){
        confirmResponse = response;
        confirmGUID = guid;
        updateMode = Constants.UPDATE_CONFIRM;
        callTransportForData();
    }

    public void getDocumentPrintingForm(String documentId) {
        confirmGUID = documentId;
        updateMode = Constants.UPDATE_PRINT;
        callTransportForData();
    }

    /**
     * Call preferred transport class for doing data transfer operations
     */
    private void callTransportForData(){

        if (appSettings.getSyncIsActive()){
            if (listener != null) {
                DataBaseItem errorResult = new DataBaseItem();
                errorResult.put(Constants.DATA_ERROR_CODE,Constants.ERROR_SYNC_IS_ACTIVE);
                listener.onResult(errorResult);
            }
            return;
        }

        appSettings.setSyncIsActive(true);

        if (progress != null) progress.onProgressUpdate(0,0);
        resultData.clear();
        queue.clear();
        timeStamp = utils.currentTime();

        DataBaseItem connectionParameters = dataBase.getCurrentConnectionParameters();
        String syncFormat = connectionParameters.getString("data_format");

        connectionParameters.put("userID",appSettings.getUserID());
        connectionParameters.put("confirmResponse",confirmResponse);
        connectionParameters.put("confirmGUID",confirmGUID);

        switch (syncFormat) {
            case Constants.SYNC_FORMAT_FTP:

                DataTransportFTP ftp = new DataTransportFTP(holder, this::onDataReceived, this::onError);
                ftp.setConnectionParameters(connectionParameters);
                ftp.update(updateMode);

                break;
            case Constants.SYNC_FORMAT_HTTP:

                DataTransportHTTP http = new DataTransportHTTP(holder, this::onDataReceived, this::onError);
                connectionParameters.put("url", appSettings.getBaseUrl());
                http.setConnectionParameters(connectionParameters);
                http.update(updateMode);

                break;
            case Constants.SYNC_FORMAT_WEB:

//                DataTransportSOAP soap = new DataTransportSOAP(holder, this::onDataReceived, this::onError);
//                connectionParameters.put("url", appSettings.getBaseUrl());
//                soap.setConnectionParameters(connectionParameters);
//                soap.update(updateMode);

                break;
            default:

                utils.log("w","Connection format has unsupported type: "+syncFormat);
                onError(Constants.CONNECTION_ERROR);

                break;
        }
    }

    /**
     * Listener to receive an error message from transport.
     * Switches sync state to false.
     *
     * @param error error code
     */
    private void onError(String error){
        utils.error("Data connector: "+error);
        appSettings.setSyncIsActive(false);
        appSettings.sendLog();
        DataBaseItem errorResult = new DataBaseItem();
        errorResult.put(Constants.DATA_ERROR_CODE,error);
        if (listener != null) listener.onResult(errorResult);
    }

    /**
     * Listener for a data set received by transport
     * @param result dataset
     */
    private void onDataReceived(ArrayList<DataBaseItem> result){
        if (result.size() > 0) {
            String type = result.get(0).type();

            if (type.equals(Constants.DATA_REQUEST_FINISHED)) {

                appSettings.setSyncIsActive(false);
                resultData.put(Constants.DATA_TIME_STAMP,timeStamp);

                if (updateMode.equals(Constants.UPDATE_ALL)){
                    //notify about successful update, only for total update mode
                    DataBaseItem dataTimeStamp = new DataBaseItem(Constants.DATA_TIME_STAMP);
                    dataTimeStamp.put(Constants.DATA_TIME_STAMP,timeStamp);
                    queue.add(dataTimeStamp);
                }

                if (queue.size() > 0) backgroundDatabaseUpdate(queue);

                if (listener != null) listener.onResult(resultData);
                if (progress != null) progress.onProgressUpdate(0,-1);

                return;
            }else if (type.equals(Constants.DATA_OPTIONS)){
                dataBase.saveDataItem(result.get(0));
                return;
            }

            //composing result message about loaded
            int count = result.size() + resultData.getInt(type);
            resultData.put(type,count);

            if (result.size() < 100) {
                queue.addAll(result);
                if (queue.size() > 1000){
                    backgroundDatabaseUpdate(queue);
                    queue.clear();
                }
            }else{
                backgroundDatabaseUpdate(result);
            }
        }
    }

    /**
     * Starts an update of the database in a separate thread
     * @param result array of data sets to be saved
     */
    private void backgroundDatabaseUpdate(ArrayList<DataBaseItem> result){
        ArrayList<DataBaseItem> arrayList = new ArrayList<>(result);

        Runnable runnable = () -> separator(arrayList);
        Thread thread = new Thread(runnable,"BACKGROUND");
        thread.start();
    }

    /**
     * Recursively separates data sets with identical types to
     * save them in the database
     * @param arrayList array of mixed types data sets
     */
    private void separator(ArrayList<DataBaseItem> arrayList){
        String type = arrayList.get(0).type();
        ArrayList<DataBaseItem> otherItems = new ArrayList<>();
        ArrayList<DataBaseItem> selectedItems = new ArrayList<>();
        for (DataBaseItem item: arrayList){
            item.put("time_stamp",timeStamp);
            if (item.typeOf(type)) {
                selectedItems.add(item);
            }else {
                otherItems.add(item);
            }
        }
        dataBase.saveDataItemsSet(selectedItems);
        if (otherItems.size() > 0) separator(otherItems);
    }
}

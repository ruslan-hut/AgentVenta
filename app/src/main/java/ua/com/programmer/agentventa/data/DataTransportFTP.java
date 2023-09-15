package ua.com.programmer.agentventa.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import ua.com.programmer.agentventa.catalogs.Client;
import ua.com.programmer.agentventa.documents.Order;
import ua.com.programmer.agentventa.utility.Constants;
import ua.com.programmer.agentventa.utility.ContextHolder;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.Utils;

import static android.content.Context.MODE_PRIVATE;

/**
 * Utility class for performing ftp requests
 */
public class DataTransportFTP {

    /** filenames for data send and receive operations */
    private final String FILE_USER_SEND = "user.out";
    private final String FILE_USER_DOWNLOAD = "user.in";

    /** holder fo context, needed for file stream operations */
    private final ContextHolder holder;

    private DataBaseItem connectionParameters;
    private final Listener<ArrayList<DataBaseItem>> listener;
    private final ErrorListener errorListener;
    //private final FTPClient ftpClient;
    private final Utils utils = new Utils();

    private String userID;
    private String updateMode;

    /** current transport state, if not empty - error occurred */
    private String errorCode;

    /** receives error codes from the thread and sends them to the listener */
    private final Handler errorHandler;

    /** receives queue stages results, continues queue or sends data to the listener */
    private final Handler resultHandler;

    private final ArrayList<DataBaseItem> responses = new ArrayList<>();

    /** Callback interface for delivering parsed responses. */
    public interface Listener<T>{
        void onResult(T result);
    }

    /** Callback interface for delivering error responses. */
    public interface ErrorListener{
        void onError(String error);
    }

    public DataTransportFTP(ContextHolder holder, Listener<ArrayList<DataBaseItem>> listener, ErrorListener errorListener){
        connectionParameters = new DataBaseItem();
        errorCode = "";

//        ftpClient = new FTPClient();
//        ftpClient.setDefaultTimeout(5000);

        this.holder = holder;
        this.listener = listener;
        this.errorListener = errorListener;

        resultHandler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (msg.what == 1) continueDataRequest();
                if (msg.what == 2) handleResultData(msg.obj.toString());
                if (msg.what == 3) onRequestFinished();
                if (msg.what == 4) handleResponseResult();
            }
        };

        errorHandler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(@NonNull Message msg) {
                switch (msg.what){
                    case 1: stopQueueAndExit(Constants.CONNECTION_ERROR); break;
                    case 2: stopQueueAndExit(Constants.ACCESS_DENIED); break;
                    case 3: stopQueueAndExit(Constants.ERROR_DATA_READING); break;
                }
            }
        };
    }

    /**
     * Prepare a line of input data for saving in the database, convert it to a data set,
     * add missing fields. Prepared data is sending to the listener.
     *
     * @param textLine line of input data
     */
    private void handleResultData(String textLine){
        ArrayList<DataBaseItem> result = new ArrayList<>();
        DataBaseItem dataBaseItem = new DataBaseItem();
        try {
            dataBaseItem = new DataBaseItem(new JSONObject(textLine));
        }catch (Exception e){
            utils.log("w","handleResultData: "+e.toString());
        }
        if (dataBaseItem.hasValues()){

            String type = dataBaseItem.type();

            if (type.equals("option") || type.equals(Constants.DATA_OPTIONS)) {

                DataBaseItem resultItem = new DataBaseItem(Constants.DATA_OPTIONS);
                resultItem.put("options", textLine);
                result.add(resultItem);

            }else {

                if (type.equals(Constants.DATA_GOODS_ITEM) || type.equals(Constants.DATA_CLIENT)){
                    if (dataBaseItem.getString("code1").isEmpty())
                        dataBaseItem.put("code1",dataBaseItem.getString("id"));
                    if (dataBaseItem.getString("code2").isEmpty())
                        dataBaseItem.put("code2",dataBaseItem.getString("id"));
                }

                if (type.equals("debtdoc")) dataBaseItem.setType(Constants.DATA_DEBT);

                result.add(dataBaseItem);
            }
            if (listener != null) listener.onResult(result);
        }
    }

    private void handleResponseResult(){
        if (listener != null) listener.onResult(responses);
    }

    /**
     * Setting connection parameters
     * @param parameters set of parameters
     */
    public void setConnectionParameters(DataBaseItem parameters){
        connectionParameters = parameters;
        userID = connectionParameters.getString("userID");
    }

    /**
     * Public method to perform data updates
     * @param mode mode of updates - update all, send documents
     */
    public void update(String mode){
        updateMode = mode;

        Runnable runnable = this::connect;

        Thread thread = new Thread(runnable,"NETWORK");
        thread.start();
    }

    /**
     * Stops request and sends error to a listener
     * @param error string error code
     */
    void stopQueueAndExit(String error){
        errorCode = error;
//        try {
//            if (ftpClient.isConnected()) ftpClient.disconnect();
//        }catch (Exception e){
//            utils.debug("FTP disconnect: "+e.getLocalizedMessage());
//        }
        if (errorListener != null) errorListener.onError(error);
    }

    /**
     * Establish connection, authorise, call continuation if no errors,
     * or send error code and stop exchange
     */
    private void connect(){

        String user = connectionParameters.getString("db_user");
        String pass = connectionParameters.getString("db_password");
        String path = connectionParameters.getString("db_name");

        String server = connectionParameters.getString("db_server");
        int port = 21;

        int colon = server.indexOf(":");
        if (colon > 0){
            port = utils.getInteger(server.substring(colon+1));
            server = server.substring(0,colon);
        }

//        try {
//            ftpClient.connect(server, port);
//            if (!ftpClient.login(user, pass)) {
//                stopQueueAndExit(Constants.ACCESS_DENIED);
//            }else{
//                ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
//                ftpClient.enterLocalPassiveMode();
//                ftpClient.changeWorkingDirectory("/"+path);
//            }
//        }catch (Exception e) {
//            utils.log("e", e.toString());
//            errorHandler.sendEmptyMessage(1);
//        }

        resultHandler.sendEmptyMessage(1);
    }

    /**
     * Continue data exchange process, send and receive data according to the update mode
     */
    private void continueDataRequest(){
        if (!errorCode.isEmpty()) return;

        Runnable runnable = () -> {

            sendSystemFile();

            if (updateMode.equals(Constants.UPDATE_ALL)) {
                sendDocuments();
                downloadData();
            }else if (updateMode.equals(Constants.UPDATE_SEND_DOCUMENTS)){
                sendDocuments();
            }

            resultHandler.sendEmptyMessage(3);
        };

        Thread thread = new Thread(runnable,"NETWORK");
        thread.start();

    }

    /**
     * Disconnecting ftp client and sending notification to the listener.
     * All data transfer operations must be done at this point.
     */
    private void onRequestFinished(){
//        try {
//            if (ftpClient.isConnected()) ftpClient.disconnect();
//        }catch (Exception e){
//            utils.debug("FTP disconnect: "+e.getLocalizedMessage());
//        }
        if (errorCode.isEmpty() && listener != null){
            ArrayList<DataBaseItem> result = new ArrayList<>();
            result.add(new DataBaseItem(Constants.DATA_REQUEST_FINISHED));
            listener.onResult(result);
        }
    }

    /**
     * SIC: Works in a Thread.
     * Sending some info to the server, it helps to see user requests.
     */
    private void sendSystemFile(){
        Context context = holder.getContext();
        String sysFile = "sys.out";

        try {
            FileOutputStream fileToRead = context.openFileOutput(FILE_USER_DOWNLOAD,MODE_PRIVATE);
            fileToRead.write(("").getBytes());
            fileToRead.close();

            FileOutputStream fileOutputStream = context.openFileOutput(sysFile, MODE_PRIVATE);
            String text = utils.readLogs().toString();
            fileOutputStream.write(text.getBytes());
            fileOutputStream.close();
        }catch (Exception e1) {
            utils.log("e","sendSystemFile#1: "+e1.toString());
            errorHandler.sendEmptyMessage(1);
        }

//        try {
//            InputStream inputStream = context.openFileInput(sysFile);
//            ftpClient.storeFile(userID + ".req", inputStream);
//        }catch (Exception e2){
//            utils.log("e","sendSystemFile#2: "+e2.toString());
//            errorHandler.sendEmptyMessage(1);
//        }
    }

    /**
     * SIC: Works in a Thread.
     * Sending documents data to the server.
     */
    private void sendDocuments(){
        Context context = holder.getContext();
        DataBase dataBase = DataBase.getInstance(holder.getContext());
        responses.clear();

        String currentDate = utils.dateLocal(utils.currentTime());

        ArrayList<DataBaseItem> ordersData = dataBase.getOrdersForUpload();
        for (DataBaseItem document: ordersData){

            try {
                String guid = document.getString("guid");

                writeOrderIntoFile(new Order(context, guid));

                String fileNameToSend = guid+".msg";

//                InputStream input = context.openFileInput(FILE_USER_SEND);
//                boolean success = ftpClient.storeFile(fileNameToSend, input);
//
//                DataBaseItem response = new DataBaseItem(Constants.DATA_DOCUMENT_SENDING_RESULT);
//                response.put("saved_data_type",Constants.DOCUMENT_ORDER);
//                response.put("guid",guid);
//                response.put("result",success ? "ok" : "error");
//                response.put("status",currentDate);
//                responses.add(response);

            }catch (Exception e){
                utils.log("e","sendDocuments Orders: "+e.toString());
            }

        }

        ArrayList<DataBaseItem> documentsData = dataBase.getDocumentsForUpload();
        for (DataBaseItem document: documentsData){

            try {
                String guid = document.getString("guid");

                writeDocumentIntoFile(document);

                String fileNameToSend = guid+".msg";

//                InputStream input = context.openFileInput(FILE_USER_SEND);
//                ftpClient.storeFile(fileNameToSend, input);
//
//                DataBaseItem response = new DataBaseItem(Constants.DATA_DOCUMENT_SENDING_RESULT);
//                response.put("saved_data_type",Constants.DOCUMENT_CASH);
//                response.put("guid",guid);
//                response.put("status",currentDate);
//                responses.add(response);

            }catch (Exception e){
                utils.log("e","sendDocuments Documents: "+e.toString());
            }

        }

        //sending message to inform about results of documents transferring
        if (responses.size() > 0) resultHandler.sendEmptyMessage(4);
    }

    /**
     * SIC: Works in a Thread.
     * Utility method to add a line of text to a file stream.
     *
     * @param fileOutputStream file writing stream
     * @param line text data to add
     * @throws IOException file operations errors
     */
    private void addLine (FileOutputStream fileOutputStream, String line) throws IOException{
        fileOutputStream.write((line+"\n").getBytes());
    }

    /**
     * SIC: Works in a Thread.
     * Write a document data to a text file.
     *
     * @param order data set of document parameters
     * @throws IOException file operations errors
     */
    private void writeOrderIntoFile(Order order) throws IOException {
        Context context = holder.getContext();
        DataBase dataBase = DataBase.getInstance(context);
        String str;
        FileOutputStream fileOutputStream;

        fileOutputStream = context.openFileOutput(FILE_USER_SEND, MODE_PRIVATE);
        fileOutputStream.write(("").getBytes());

        addLine(fileOutputStream,"agent;"+userID);
        addLine(fileOutputStream,"doc_type;order");
        addLine(fileOutputStream,"doc_begin;"+order.GUID);
        addLine(fileOutputStream,"debug;"+order.number+";"+order.date+";"+order.client+";"+order.client_code2+";"+order.priceTotal+";");
        if (order.hasLocation()){
            DataBaseItem location = order.getLocation();
            addLine(fileOutputStream,"location;"
                    +location.getString("latitude")
                    +";"+location.getString("longitude")
                    +";"+location.getString("distance")
                    +";"+location.getString("time"));
        }
        str = "N";
        if (order.isReturn) {str = "Y";}
        addLine(fileOutputStream,"isreturn;"+str+";0;");
        addLine(fileOutputStream,"delivery;"+order.deliveryDate+";0;");
        addLine(fileOutputStream,"client;"+order.client_code2+";"+order.client+";"+order.payment+";0;");
        addLine(fileOutputStream,"next_payment;"+order.nextPayment+";0;");
        addLine(fileOutputStream,"price_type;"+order.price_type+";0;");
        addLine(fileOutputStream,"note;"+order.notes+";0;");

        ArrayList<DataBaseItem> content = dataBase.getOrderContent(order.id);
        for (DataBaseItem item: content) {
            str = "item;"+
                    item.getString("code2")+";"+
                    item.getString("description")+";"+
                    utils.format(item.getDouble("quantity"),3)+";"+
                    utils.format(item.getDouble("price"),2)+";"+
                    utils.format(item.getDouble("sum"),2)+";"+
                    utils.format(item.getDouble("sum_discount"),2)+";"+
                    item.getInt("is_packed")+";0;";
            addLine(fileOutputStream,str);
        }

        addLine(fileOutputStream,"doc_end;");
        fileOutputStream.close();

    }

    /**
     * SIC: Works in a Thread.
     * Write a document data to a text file.
     *
     * @param document data set of document parameters
     * @throws IOException file operations errors
     */
    private void writeDocumentIntoFile(DataBaseItem document) throws IOException{
        Context context = holder.getContext();
        DataBase dataBase = DataBase.getInstance(context);

        FileOutputStream fileOutputStream;

        Client client = new Client(context,document.getString("client_guid"));

        String parentDocument = dataBase.readAttributeValue(document.getString("guid"),"parent_document");

        fileOutputStream = context.openFileOutput(FILE_USER_SEND, MODE_PRIVATE);
        fileOutputStream.write(("").getBytes());

        addLine(fileOutputStream,"agent;"+userID);
        addLine(fileOutputStream,"doc_type;"+document.getString("type"));
        addLine(fileOutputStream,"doc_begin;"+document.getString("guid"));
        addLine(fileOutputStream,"doc_number;"+document.getString("number"));
        addLine(fileOutputStream,"doc_date;"+document.getString("date"));
        addLine(fileOutputStream,"client;"+client.code2+";"+client.description);
        addLine(fileOutputStream,"sum;"+utils.format(document.getDouble("sum"),2));
        addLine(fileOutputStream,"parent_doc;"+parentDocument);
        addLine(fileOutputStream,"note;"+document.getString("notes"));
        addLine(fileOutputStream,"doc_end;");
        fileOutputStream.close();

//        document.put("is_processed", 2);
//        document.put("is_sent", 1);
//
//        dataBase.updateDocument(document.getString("guid"), document.getValues());

    }

    /**
     * SIC: Works in a Thread.
     * Download data from server and read it's content.
     */
    private void downloadData(){

        //uncompressed data file
        String jFile = userID+".jout";
        //compressed data file
        String zFile = userID+".zip";

        String localZipFile = "user.zip";

        //download and read main data file: options, goods, price.. etc.
        try {

            //if remote side has zip file, process it
            if (downloadFile(zFile, localZipFile)) {
                unZip(localZipFile, jFile);
            } else {
                //regular, uncompressed file download
                downloadFile(jFile, FILE_USER_DOWNLOAD);
            }

            readFile();

        }catch (Exception e){
            utils.log("e","downloadData: "+e.toString());
            errorHandler.sendEmptyMessage(3);
        }

        //download images list
        try {

            if (downloadFile("images.jout", FILE_USER_DOWNLOAD)){
                readFile();
            }

        }catch (Exception e){
            utils.log("e","downloadData: "+e.toString());
            errorHandler.sendEmptyMessage(3);
        }

    }

    /**
     * Extract file from ZIP archive
     *
     * @param fileName archive file name
     * @param extractName file to extract
     * @throws IOException file operations errors
     */
    private void unZip(String fileName, String extractName) throws IOException{
        Context context = holder.getContext();
        InputStream fileInputStream = context.openFileInput(fileName);
        ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(fileInputStream));
        ZipEntry zipEntry;
        byte[] buffer = new byte[1024];
        int count;
        while ((zipEntry = zipInputStream.getNextEntry()) != null) {
            if (extractName.equals(zipEntry.getName())) {
                FileOutputStream fileOutputStream = context.openFileOutput(FILE_USER_DOWNLOAD, MODE_PRIVATE);
                while ((count = zipInputStream.read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, count);
                }
                fileOutputStream.close();
                zipInputStream.closeEntry();
                break;
            }
        }
    }

    /**
     * Performs download of the remote file into local storage
     *
     * @param remoteFileName remote file name
     * @param localFileName local file name
     * @return true if download was successful
     * @throws IOException errors on file operations
     */
    private boolean downloadFile (String remoteFileName, String localFileName) throws IOException {
        Context context = holder.getContext();

        FileOutputStream fileOutputStream = context.openFileOutput(localFileName,MODE_PRIVATE);
        fileOutputStream.write(("").getBytes());
        fileOutputStream.close();

//        FTPFile[] ftpFiles = ftpClient.listFiles();
//        if (ftpFiles != null && ftpFiles.length > 0) {
//            for (FTPFile ftpFile : ftpFiles) {
//                if (remoteFileName.equals(ftpFile.getName())){
//                    OutputStream fos = context.openFileOutput(localFileName, MODE_PRIVATE);
//                    ftpClient.retrieveFile(remoteFileName,fos);
//                    fos.close();
//                    return true;
//                }
//            }
//        }
        return false;
    }

    /**
     * Read a file content. Every line of text is to be sent via resultHolder to the listener.
     *
     * @throws IOException file operations errors
     */
    private void readFile() throws IOException {
        Context context = holder.getContext();

        FileInputStream stream = context.openFileInput(FILE_USER_DOWNLOAD);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream));
        String textLine;
        while ((textLine = bufferedReader.readLine()) != null){

            Message message = new Message();
            message.what = 2;
            message.obj = textLine;
            resultHandler.sendMessage(message);

        }
    }
}

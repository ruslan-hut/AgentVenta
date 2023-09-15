package ua.com.programmer.agentventa.documents;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import ua.com.programmer.agentventa.R;
import ua.com.programmer.agentventa.data.DataBase;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.Utils;

public class DebtDocumentModel {

    private final Utils utils = new Utils();
    private final DataBase db;
    private DataBaseItem document;
    private final ArrayList<DataBaseItem> items;
    private final String warnNoData;

    public interface DebtDocumentListener{
        void onDataLoaded();
    }

    private static DebtDocumentListener listener;

    public DebtDocumentModel(Context context){
        listener = (DebtDocumentListener) context;
        db = DataBase.getInstance(context);
        document = new DataBaseItem();
        items = new ArrayList<>();
        warnNoData = context.getString(R.string.warn_no_document_data);
    }

    public void findByGUID(String guid){
        document = db.getDebtDocument(guid);
        items.clear();
        String content = document.getString("content");
        if (content.isEmpty()) {
            requestContent();
        }else {
            setDocumentContent(content);
        }
    }

    private void readAttribute(JSONObject jsonObject, String key) throws JSONException{
        if (jsonObject.has(key)) document.put(key, jsonObject.getString(key));
    }

    private void setDocumentContent(String content){
        document.put("error","");
        try{

            JSONObject jsonObject = new JSONObject(content);
            readAttribute(jsonObject, "is_processed");
            readAttribute(jsonObject, "title");
            readAttribute(jsonObject, "company");
            readAttribute(jsonObject, "warehouse");
            readAttribute(jsonObject, "contractor");
            readAttribute(jsonObject, "total");

            if (jsonObject.has("items")){
                JSONArray jsonArray = jsonObject.getJSONArray("items");
                for (int i=0; i<jsonArray.length(); i++){
                    items.add(new DataBaseItem(jsonArray.getJSONObject(i)));
                }
            }

        } catch (JSONException e) {
            utils.debug("read debt content error: "+e.toString());
            document.put("error",content);
        }
        listener.onDataLoaded();
    }

    private void requestContent(){
        document.put("error",warnNoData);
        listener.onDataLoaded();
    }

    public String get(String key){
        return document.getString(key);
    }

    public ArrayList<DataBaseItem> getItems(){
        return items;
    }
}

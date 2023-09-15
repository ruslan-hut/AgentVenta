package ua.com.programmer.agentventa.utility;

import android.content.ContentValues;
import android.database.Cursor;
import android.location.Location;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;
import java.util.Set;

public class DataBaseItem {

    private final ContentValues itemValues;
    private final Utils utils = new Utils();

    public DataBaseItem(ContentValues values){
        itemValues = values;
    }

    public DataBaseItem(){
        itemValues = new ContentValues();
    }

    public DataBaseItem(Cursor cursor) {
        int columnIndex;
        String[] columnNames = cursor.getColumnNames();
        ContentValues values = new ContentValues();
        try{
            for (String columnName: columnNames) {
                columnIndex = cursor.getColumnIndex(columnName);
                if (columnName.equals("_id")){
                    values.put("raw_id",cursor.getLong(columnIndex));
                    continue;
                }
                switch (cursor.getType(columnIndex)) {
                    case Cursor.FIELD_TYPE_STRING:
                    case Cursor.FIELD_TYPE_NULL:
                    case Cursor.FIELD_TYPE_BLOB:
                        values.put(columnName, cursor.getString(columnIndex));
                        break;
                    case Cursor.FIELD_TYPE_INTEGER:
                        values.put(columnName, cursor.getLong(columnIndex));
                        break;
                    case Cursor.FIELD_TYPE_FLOAT:
                        values.put(columnName, cursor.getDouble(columnIndex));
                        break;
                }
            }
        }catch (Exception e){
            utils.error("Initialize DataBaseItem from cursor: "+e);
        }
        itemValues = values;
    }

    public DataBaseItem(JSONObject jsonObject) {
        JSONArray columnNames = jsonObject.names();
        ContentValues values = new ContentValues();
        for (int i = 0; i < Objects.requireNonNull(columnNames).length(); i++) {
            try {
                String columnName = columnNames.getString(i);
                Object obj = jsonObject.get(columnName);
                if (obj instanceof String) {
                    values.put(columnName, jsonObject.getString(columnName));
                }else if (obj instanceof Integer){
                    values.put(columnName, jsonObject.getInt(columnName));
                }else if (obj instanceof Long){
                    values.put(columnName, jsonObject.getLong(columnName));
                }else if (obj instanceof Double){
                    values.put(columnName, jsonObject.getDouble(columnName));
                }else if (obj instanceof Boolean) {
                    values.put(columnName, jsonObject.getBoolean(columnName));
                }else {
                    values.put(columnName, jsonObject.getString(columnName));
                }
            }catch (JSONException ex){
                utils.error("Initialize DatabaseItem from JSON: "+ex);
            }
        }
        itemValues = values;
    }

    public DataBaseItem(String type){
        itemValues = new ContentValues();
        itemValues.put("type",type);
    }

    public void clear() {itemValues.clear();}

    public boolean typeOf(String type){
        return type().equals(type);
    }

    /**
     * Define data type - read value of a special key: "value_id", "object_id" or "type"
     *
     * @return string value of "type"
     */
    public String type(){
        if (itemValues.containsKey("value_id")) return itemValues.getAsString("value_id");
        if (itemValues.containsKey("object_id")) return itemValues.getAsString("object_id");
        return getString("type");
    }

    /**
     * Replaces or sets the value of the special field to a given value.
     *
     * @param type new type value
     */
    public void setType(String type){
        if (itemValues.containsKey("value_id")) itemValues.put("value_id",type);
        else if (itemValues.containsKey("object_id")) itemValues.put("object_id",type);
        else itemValues.put("type",type);
    }

    /**
     * Read string value
     *
     * @param valueName name of the parameter
     * @return parameter value
     */
    public String getString(String valueName){
        String value = "";
        if (valueName == null) return value;
        if (itemValues.containsKey(valueName)){
            value = itemValues.getAsString(valueName);
        }
        if (value == null) value = "";
        if (value.equals("null")) value = "";
        return value;
    }

    /**
     * Read string value or return default value
     *
     * @param valueName key name
     * @param defaultValue value to return if key is not found or has ho value
     * @return returning string
     */
    public String getString(String valueName, String defaultValue){
        String value;
        if (valueName == null) return defaultValue;
        if (itemValues.containsKey(valueName)) {
            value = itemValues.getAsString(valueName);
        }else {
            value = defaultValue;
        }
        return value != null ? value : defaultValue;
    }

    public Double getDouble(String valueName){
        Double value = 0.0;
        if (valueName == null) return value;
        if (itemValues.containsKey(valueName) && itemValues.get(valueName) != null){
            if (itemValues.getAsString(valueName).equals("")) return value;
            value = itemValues.getAsDouble(valueName);
        }
        return value;
    }

    public int getInt(String valueName){
        int value = 0;
        if (valueName == null) return value;
        if (itemValues.containsKey(valueName) && itemValues.get(valueName) != null){
            if (itemValues.getAsString(valueName).equals("")) return value;
            try {
                value = itemValues.getAsInteger(valueName);
            }catch (Exception e){
                utils.debug("DataBaseItem.getInt: "+e);
            }
        }
        return value;
    }

    public long getLong(String valueName){
        long value = 0;
        if (valueName == null) return value;
        if (itemValues.containsKey(valueName) && itemValues.get(valueName) != null){
            if (itemValues.getAsString(valueName).equals("")) return value;
            try {
                value = itemValues.getAsLong(valueName);
            }catch (Exception e){
                utils.debug("DataBaseItem.getLong: "+e);
            }
        }
        return value;
    }

    public boolean getBoolean(String valueName){
        if (valueName == null) return false;
        if (itemValues.containsKey(valueName)){
            return itemValues.getAsBoolean(valueName);
        }
        return false;
    }

    public void put(String valueName, String value){
        itemValues.put(valueName, value);
    }

    public void put(String valueName, boolean value){
        itemValues.put(valueName,value);
    }

    public void put(String valueName, int value){
        itemValues.put(valueName, value);
    }

    public void put(String valueName, Double value){
        if (value == null) {
            itemValues.put(valueName, 0.0);
        }else{
            itemValues.put(valueName, value);
        }
    }

    synchronized public void put(String valueName, Long value){
        itemValues.put(valueName, value);
    }

    public ContentValues getValues(){
        ContentValues copy = new ContentValues();
        copy.putAll(itemValues);
        if (copy.containsKey("raw_id")) copy.remove("raw_id");
        return copy;
    }

    /**
     * Get values for database operations, excluding keys, not listed in a table structure
     * @return values set
     */
    public ContentValues getValuesForDataBase(){
        ContentValues copy = new ContentValues();
        copy.putAll(itemValues);
        if (copy.containsKey("raw_id")) copy.remove("raw_id");
        if (copy.containsKey("type")) copy.remove("type");
        if (copy.containsKey("value_id")) copy.remove("value_id");
        return copy;
    }

    public boolean hasValues() {
        return getValuesForDataBase().size()>0;
    }

    public JSONObject getAsJSON() {
        JSONObject document = new JSONObject();
        try {
            document.put("item_type", "databaseItem");
            Set<String> keys = itemValues.keySet();
            for (String key: keys) {
                document.put(key, itemValues.get(key));
            }
        }catch (JSONException ex){
            utils.log("e", "DatabaseItem.getAsJSON: "+ex);
        }
        return document;
    }

    /**
     * Get Location object with data from current values set
     *
     * @return location object
     */
    public Location getLocation(){
        Location location = new Location("fused");
        location.setLatitude(getDouble("latitude"));
        location.setLongitude(getDouble("longitude"));
        return location;
    }

}

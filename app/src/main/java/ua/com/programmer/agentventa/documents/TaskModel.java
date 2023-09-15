package ua.com.programmer.agentventa.documents;

import android.content.Context;

import java.util.UUID;

import ua.com.programmer.agentventa.data.DataBase;
import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.Utils;

public class TaskModel {

    private final Utils utils = new Utils();
    private final DataBase db;
    private DataBaseItem taskData;

    private static TaskListener listener;

    public interface TaskListener{
        void onDataLoaded();
    }

    public TaskModel(Context context){
        listener = (TaskListener) context;
        db = DataBase.getInstance(context);
        taskData = new DataBaseItem();
    }

    public void findByGUID(String guid){
        taskData = db.getTaskData(guid);

        if (!taskData.hasValues()){
            long time = utils.currentTime();
            taskData.put("guid",UUID.randomUUID().toString());
            taskData.put("time",time);
            taskData.put("date",utils.dateLocal(time));
        }

        listener.onDataLoaded();
    }

    public void save(){
        String guid = taskData.getString("guid");
        db.saveTask(guid,taskData);
    }

    public String get(String key){
        return taskData.getString(key);
    }

    public boolean getBoolean(String key){
        return taskData.getBoolean(key);
    }

    public void set(String key, String value){
        taskData.put(key,value);
    }

    public void setBoolean(String key, boolean value){
        taskData.put(key,value);
    }

}

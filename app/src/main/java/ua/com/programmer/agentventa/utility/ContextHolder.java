package ua.com.programmer.agentventa.utility;

import android.content.Context;

public class ContextHolder {

    private final Context context;

    public ContextHolder(Context context){
        this.context = context;
    }

    public Context getContext(){
        return context;
    }

}

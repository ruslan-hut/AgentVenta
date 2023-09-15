package ua.com.programmer.agentventa.catalogs.locations;

import android.app.IntentService;
import android.content.Intent;
import androidx.annotation.Nullable;

import com.google.android.gms.location.LocationResult;

import ua.com.programmer.agentventa.utility.Constants;

public class LocationUpdatesIntentService extends IntentService {

    //static final String ACTION_PROCESS_UPDATES = "ua.com.programmer.agent.PROCESS_UPDATES";
    private static final String TAG = LocationUpdatesIntentService.class.getSimpleName();

    public LocationUpdatesIntentService(){
        super(TAG);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent != null){
            final String action = intent.getAction();
            if (Constants.ACTION_PROCESS_UPDATES.equals(action)){
                LocationResult result = LocationResult.extractResult(intent);
                if (result != null){
                    LocationResultHelper locationResultHelper =
                            new LocationResultHelper(this,result.getLocations());
                    locationResultHelper.saveResults();
                    locationResultHelper.showNotification();
                }
            }
        }
    }
}

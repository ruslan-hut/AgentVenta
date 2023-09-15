package ua.com.programmer.agentventa.catalogs.locations;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;

import com.google.android.gms.location.LocationResult;

import java.util.List;

import ua.com.programmer.agentventa.utility.Constants;

public class LocationUpdatesBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null){
            final String action = intent.getAction();
            if (Constants.ACTION_PROCESS_UPDATES.equals(action)){
                LocationResult result = LocationResult.extractResult(intent);
                //null check due to app crashes
                //Attempt to invoke virtual method 'java.util.List com.google.android.gms.location.LocationResult.getLocations()' on a null object reference
                if (result != null){
                    List<Location> locations = result.getLocations();
                    LocationResultHelper locationResultHelper =
                            new LocationResultHelper(context,locations);
                    locationResultHelper.saveResults();
                    locationResultHelper.showNotification();
                }
            }
        }
    }
}

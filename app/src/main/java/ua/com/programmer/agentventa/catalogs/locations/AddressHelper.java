package ua.com.programmer.agentventa.catalogs.locations;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import ua.com.programmer.agentventa.utility.Utils;

class AddressHelper {

    private static final Utils utils = new Utils();

    static void getAddressFromLocation(final double lat, final double lng, final Context context, final Handler handler){

        Thread thread = new Thread(){
            @Override
            public void run() {
                Geocoder geocoder = new Geocoder(context, Locale.getDefault());
                String result = null;
                try{
                    List<Address> addressList = geocoder.getFromLocation(lat, lng, 1);
                    if (addressList != null && addressList.size() > 0){
                        Address address = addressList.get(0);
                        StringBuilder builder = new StringBuilder();
                        for (int i=0; i<=address.getMaxAddressLineIndex(); i++){
                            if (i>0) builder.append("\n");
                            builder.append(address.getAddressLine(i));
                        }
                        //builder.append(address.getLocality()).append("\n");
                        //builder.append(address.getPostalCode()).append("\n");
                        //builder.append(address.getCountryName());
                        result = builder.toString();
                    }
                }catch (IOException e){
                    utils.log("e","AddressHelper: Geocoder: "+e.toString());
                }finally {
                    Message message = new Message();
                    message.setTarget(handler);
                    message.what = 1;
                    Bundle bundle = new Bundle();
                    bundle.putString("address",result);
                    message.setData(bundle);
                    message.sendToTarget();
                }
            }
        };
        thread.start();
    }
}

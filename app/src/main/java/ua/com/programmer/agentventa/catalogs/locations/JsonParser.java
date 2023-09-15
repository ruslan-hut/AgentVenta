package ua.com.programmer.agentventa.catalogs.locations;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.Utils;

class JsonParser {

    private final Utils utils = new Utils();

    boolean validDirectionsResponse(JSONObject response){
        boolean validStatus = false;
        boolean hasRoutes = false;
        try {

            if (response.has("error_message")) {
                utils.warn(response.getString("error_message"));
            }else{
                if (response.has("status")){
                    validStatus = response.getString("status").equals("OK");
                }
                if (response.has("routes")){
                    JSONArray routesArray = response.getJSONArray("routes");
                    if (routesArray.length() > 0){
                        JSONObject route = routesArray.getJSONObject(0);
                        JSONArray legsArray = route.getJSONArray("legs");
                        hasRoutes = legsArray.length() > 0;
                    }
                }
            }

        }catch (Exception e){
            utils.error("JsonParser.validDirectionsResponse: "+e);
        }
        return validStatus & hasRoutes;
    }

    ArrayList<DataBaseItem> decodeDirectionsResponse(JSONObject response){
        ArrayList<DataBaseItem> route = new ArrayList<>();

        try {

            JSONObject routeArray = response.getJSONArray("routes").getJSONObject(0);

            JSONArray legs = routeArray.getJSONArray("legs");
            for (int k=0; k<legs.length(); k++){

                JSONObject routeData = legs.getJSONObject(k);

                if (route.size() == 0){
                    DataBaseItem startPoint = new DataBaseItem();
                    startPoint.put("latitude",routeData.getJSONObject("start_location").getDouble("lat"));
                    startPoint.put("longitude",routeData.getJSONObject("start_location").getDouble("lng"));
                    startPoint.put("name",routeData.getString("start_address"));
                    startPoint.put("selected",1);
                    route.add(startPoint);
                }

                JSONArray steps = routeData.getJSONArray("steps");
                for (int i=0; i<steps.length(); i++){

                    JSONObject step = steps.getJSONObject(i);

                    DataBaseItem point = new DataBaseItem();
                    point.put("latitude",step.getJSONObject("end_location").getDouble("lat"));
                    point.put("longitude",step.getJSONObject("end_location").getDouble("lng"));
                    point.put("distance",step.getJSONObject("distance").getDouble("value"));
                    point.put("polyline",step.getJSONObject("polyline").getString("points"));
                    point.put("selected",1);

                    if (step.has("end_address")){
                        point.put("name",step.getString("end_address"));
                    }
                    if (i == steps.length()-1){
                        //last step point has a geocoded name from the route dataset
                        point.put("name",routeData.getString("end_address"));
                    }

                    route.add(point);
                }

            }

        }catch (Exception e){
            utils.error("JsonParser.decodeDirectionsResponse: "+e);
        }

        return route;

    }

    JSONObject encodeRouteData(ArrayList<DataBaseItem> route){
        JSONObject document = null;
        try {

            document = new JSONObject();
            document.put("status", "OK");

            JSONArray routeArray = new JSONArray();
            JSONObject routeDocument = new JSONObject();

            JSONArray legsArray = new JSONArray();

            JSONObject leg = new JSONObject();
            JSONObject start = new JSONObject();
            DataBaseItem routeItem = route.get(0);
            start.put("lat",routeItem.getDouble("latitude"));
            start.put("lng",routeItem.getDouble("longitude"));
            leg.put("start_location",start);
            leg.put("start_address",routeItem.getString("name"));

            routeItem = route.get(route.size()-1);
            JSONObject end = new JSONObject();
            end.put("lat",routeItem.getDouble("latitude"));
            end.put("lng",routeItem.getDouble("longitude"));
            leg.put("end_location",end);
            leg.put("end_address",routeItem.getString("name"));

            JSONArray stepsArray = new JSONArray();

            if (route.size() > 1){
                for (int i=1; i<route.size(); i++){

                    JSONObject step = new JSONObject();
                    routeItem = route.get(i);

                    JSONObject endLocation = new JSONObject();
                    endLocation.put("lat",routeItem.getDouble("latitude"));
                    endLocation.put("lng",routeItem.getDouble("longitude"));
                    step.put("end_location",endLocation);

                    JSONObject polyline = new JSONObject();
                    polyline.put("points",routeItem.getString("polyline"));
                    step.put("polyline",polyline);

                    JSONObject distance = new JSONObject();
                    distance.put("value",routeItem.getDouble("distance"));
                    step.put("distance",distance);

                    step.put("end_address",routeItem.getString("name"));

                    stepsArray.put(step);
                }
            }

            leg.put("steps",stepsArray);
            legsArray.put(leg);

            routeDocument.put("legs",legsArray);
            routeArray.put(routeDocument);

            document.put("routes",routeArray);

        }catch (Exception e){
            utils.error("JsonParse.encodeRouteData: "+e);
        }

        return document;
    }
}

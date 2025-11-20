package ua.com.programmer.agentventa.utility;

import android.util.Log;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

import ua.com.programmer.agentventa.R;

public class Utils implements UtilsInterface {

    private final String LOG_MARK = "XBUG";

    public double round (double i, int accuracy) {
        try {
            return Double.parseDouble(format(i,accuracy));
        }catch (Exception ex){
            return 0.0;
        }
    }

    public double round (String i, int accuracy) {
        if (i==null) {
            return 0.0;
        }
        if (i.equals("")) {
            return 0.0;
        }
        try {
            return round(Double.parseDouble(i), accuracy);
        }catch (Exception ex){
            return 0.0;
        }
    }

    public String format (String i, int accuracy) {
        double value = round(i,accuracy);
        return format(value,accuracy);
    }
    public String format (double i, int accuracy) {
        return String.format(Locale.getDefault(),"%."+accuracy+"f",i).replace(",",".");
    }

    public String formatAsInteger (double i, int accuracy) {
        if (i == round(i,0)) {
            return ""+((int) i);
        }
        return format(i,accuracy);
    }

    public String formatDistance(double distance){
        if (distance < 999) return format(distance,0)+" m";
        if (distance < 9999) return format(distance/1000,1)+" km";
        return format(distance/1000,0)+" km";
    }

    public String formatWeight(double weight){
        if (weight < 999) return format(weight,0)+" kg";
        return format(weight/1000,1)+" t";
    }

    public int getInteger (String i) {
        if (i==null) {
            return 0;
        }
        if (i.equals("") || i.equals("null")) return 0;
        try {
            return Integer.parseInt(i);
        }catch (Exception e){
            error("Utils; parse integer value: "+i+"; "+e);
        }
        return 0;
    }

    public long getLong (String i) {
        if (i==null) {
            return 0;
        }
        if (i.isEmpty() || i.equals("null")) return 0;
        try {
            return Long.parseLong(i);
        }catch (Exception e){
            error("Utils; parse long value: "+i+"; "+e);
        }
        return 0;
    }

    public String getString (String s){
        if (s==null||s.equals("null")){
            return "";
        }
        return s;
    }

    /**
     * Returns current timestamp in seconds
     */
    public long currentTime(){
        return GregorianCalendar.getInstance().getTimeInMillis() / 1000;
    }

    /**
     * Calculates difference between given times and forms readable description.
     *
     * @param begin time of period beginning, seconds
     * @param end time of period ending, seconds
     * @return readable text description of time period
     */
    public String showTime(long begin, long end){
        long seconds = end - begin;
        long days = seconds/86400;
        long hours = (seconds - days*86400) / 3600;
        long minutes = (seconds - days*86400 - hours*3600) / 60;
        seconds = seconds - days*86400 - hours*3600 - minutes*60;
        String result = "";
        if (days > 0) result = ""+days+" d ";
        if (hours > 0 || !result.isEmpty()) result = result + hours + " h ";
        if (minutes > 0 || !result.isEmpty()) result = result + minutes + " m ";
        result = result + seconds + " s ";
        return result;
    }

    public long dateBeginOfToday(){
        return dateBeginOfDay(currentTime());
    }

    public long dateBeginOfDay(long time){
        Calendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis(time*1000);
        calendar.set(Calendar.HOUR_OF_DAY,0);
        calendar.set(Calendar.MINUTE,0);
        calendar.set(Calendar.SECOND,0);
        return calendar.getTimeInMillis()/1000;
    }

    public long dateEndOfToday(){
        return dateBeginOfToday() + 86399;
    }

    public long dateBeginShiftDate(int numberOfDays){
        return dateBeginOfToday() + 86400L * numberOfDays;
    }

    public long dateEndShiftDate(int numberOfDays){
        return dateEndOfToday() + 86400L * numberOfDays;
    }

    public String dateLocal(long time){
        Calendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis(time*1000);
        Date date = calendar.getTime();
        return String.format(Locale.getDefault(),"%1$td-%1$tm-%1$tY %1$tH:%1$tM", date);
    }

    public String dateLocalShort(long time){
        Calendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis(time*1000);
        Date date = calendar.getTime();
        return String.format(Locale.getDefault(),"%1$td-%1$tm-%1$tY", date);
    }

    public void log(String logType, String message){
        switch (logType){
            case "i": debug(message); break;
            case "e": error(message); break;
            default: warn(message); break;
        }
    }

    public void warn(String message){
        Log.w(LOG_MARK,message);
        FirebaseCrashlytics.getInstance().log("W: "+message);
    }

    public void error(String message){
        Log.e(LOG_MARK,message);
        FirebaseCrashlytics.getInstance().log("E: "+message);
    }

    public void debug(String message){
        //if (BuildConfig.DEBUG) {
            Log.i(LOG_MARK,message);
        //}
    }

    public StringBuilder readLogs() {

        String command = "logcat -d "+LOG_MARK+":D *:S";
        //if (debug) command = "logcat -d XBUG:D *:E";

        StringBuilder logBuilder = new StringBuilder();
        try {
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            String line;
            int linesCounter = 0;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains("--- beginning of")) continue;
                line = line + "\n";
                logBuilder.append(line);
                linesCounter++;
                if (linesCounter>=1000){
                    logBuilder.append("... Output size limit reached");
                    break;
                }
            }
        } catch (IOException e) {
            String error = "Error reading logs: "+e;
            logBuilder.append(error);
        }
        return logBuilder;
    }

    public int getPageTitleID(String tag){
        switch (tag){
            case Constants.DOCUMENT_ORDER: return R.string.header_orders_list;
            case Constants.DOCUMENT_CASH: return R.string.header_cash_list;
            case Constants.DOCUMENT_TASK: return R.string.header_tasks_list;
            default: return R.string.app_name;

        }
    }

    public double max(double arg1, double arg2){
        return Math.max(arg1, arg2);
    }
}

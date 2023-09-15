package ua.com.programmer.agentventa.license;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import ua.com.programmer.agentventa.utility.DataBaseItem;
import ua.com.programmer.agentventa.utility.Utils;

public class LicenseManager {

    private final Utils utils = new Utils();
    private String licenseKey;
    private boolean isActive;
    private String userID;
    private int keySize;
    private int maxSize;
    private int failCounter;
    private boolean userAdded;
    private boolean unlimited;

    private final ValidationListener listener;

    public interface ValidationListener{
        void onValidationResult(DataBaseItem keyOptions);
    }

    public LicenseManager(ValidationListener listener){
        failCounter = 0;
        this.listener = listener;
    }

    public void saveLicenseKey(String userID, String key, boolean currentState){

        this.userID = userID;

        licenseKey = key;
        isActive = currentState;
        userAdded = false;
        unlimited = false;
        keySize = 0;
        maxSize = 0;

        if (key.isEmpty() || key.length() < 8) {
            isActive = false;
            saveKeyState();
        }else{

            readCloudData();

        }

    }

    private void readCloudData(){
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        firestore.collection("keys")
                .document(licenseKey)
                .get()
                .addOnSuccessListener(document -> {
                    isActive = false;
                    if (document != null) {
                        if (document.contains("active"))
                            isActive = utils.getInteger(document.getString("active")) == 1;
                        if (document.contains("maxSize"))
                            maxSize = utils.getInteger(document.getString("maxSize"));
                        if (document.contains("unlimited"))
                            unlimited = utils.getInteger(document.getString("unlimited")) == 1;
                    }
                    if (isActive) {
                        checkKey();
                    }else {
                        saveKeyState();
                    }
                })
                .addOnFailureListener(e -> {
                    failCounter++;
                    if (failCounter > 1) {
                        String sub = licenseKey;
                        if (sub.length() > 6) sub = sub.substring(0,6)+"***";
                        utils.error("key "+sub+" read error: "+e.getMessage());
                        saveKeyState();
                    }else{
                        goOnline();
                    }
                });
    }

    /**
     * Try to enable network access for the Firebase client
     */
    private void goOnline(){
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        firestore.enableNetwork()
                .addOnCompleteListener(task -> {
                    utils.debug("Firebase: online mode restored");
                    readCloudData();
                })
                .addOnFailureListener(e -> utils.error("Firebase: enabling network failure; "+e));
    }

    private void saveKeyState(){

        String sub = licenseKey;
        if (sub.length() > 6) sub = sub.substring(0,6)+"***";
        utils.debug("Key "+sub+"; active "+isActive+"; size "+keySize+"; max.size "+maxSize+"; unlimited "+unlimited);

        DataBaseItem keyOptions = new DataBaseItem();
        keyOptions.put("isActive", isActive);
        keyOptions.put("keySize", keySize);
        keyOptions.put("maxSize", maxSize);
        keyOptions.put("unlimited", unlimited);

        if (listener != null) listener.onValidationResult(keyOptions);

        if (isActive){
            long time = utils.currentTime();
            Date date = new Date(time * 1000);
            Map<String, Object> document = new HashMap<>();
            document.put("userID", userID);
            document.put("date", date);
            document.put("time", time);
            if (!userAdded) document.put("dateAdded", date);

            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            firestore.collection("keys")
                    .document(licenseKey)
                    .collection("users")
                    .document(userID)
                    .set(document, SetOptions.merge());

            if (!userAdded){
                keySize++;
                document.clear();
                document.put("keySize",keySize);
                firestore.collection("keys")
                        .document(licenseKey)
                        .set(document, SetOptions.merge());
            }
        }

    }

    private void checkKey(){

        if (licenseKey.length() < 8){
            utils.warn("Wrong key: "+licenseKey);
            isActive = false;
            saveKeyState();
            return;
        }

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        firestore.collection("keys")
                .document(licenseKey)
                .collection("users")
                .get()
                .addOnCompleteListener(task -> {

                    String key = licenseKey.substring(0, 4) + "***";
                    ArrayList<String> users = new ArrayList<>();
                    if (task.isSuccessful()) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            users.add(document.getString("userID"));
                        }
                        keySize = users.size();
                        userAdded = users.contains(userID);
                        if (!userAdded && keySize >= maxSize && maxSize > 0) {
                            isActive = false;
                            utils.warn("#" + key + "; max: "+maxSize+"; size: "+keySize);
                        }
                    }else{
                        utils.debug("error reading key "+key+"; "+task.getException());
                    }

                    saveKeyState();
                })
                .addOnFailureListener(e -> {
                    utils.error("lm check key: "+e);
                    saveKeyState();
                });
    }

}

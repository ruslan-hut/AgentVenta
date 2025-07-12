package ua.com.programmer.agentventa.license

import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import ua.com.programmer.agentventa.utility.DataBaseItem
import ua.com.programmer.agentventa.utility.Utils
import java.util.Date

class LicenseManagerDeprecated(private val listener: ValidationListener?) {
    private val utils = Utils()
    private var licenseKey: String? = null
    private var isActive = false
    private var userID: String? = null
    private var keySize = 0
    private var maxSize = 0
    private var failCounter = 0
    private var userAdded = false
    private var unlimited = false

    interface ValidationListener {
        fun onValidationResult(keyOptions: DataBaseItem?)
    }

    fun saveLicenseKey(userID: String, key: String, currentState: Boolean) {
        this.userID = userID

        licenseKey = key
        isActive = currentState
        userAdded = false
        unlimited = false
        keySize = 0
        maxSize = 0

        if (key.isEmpty() || key.length < 8) {
            isActive = false
            saveKeyState()
        } else {
            readCloudData()
        }
    }

    private fun readCloudData() {
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("keys")
            .document(licenseKey!!)
            .get()
            .addOnSuccessListener(OnSuccessListener { document: DocumentSnapshot? ->
                isActive = false
                if (document != null) {
                    if (document.contains("active")) isActive =
                        utils.getInteger(document.getString("active")) == 1
                    if (document.contains("maxSize")) maxSize =
                        utils.getInteger(document.getString("maxSize"))
                    if (document.contains("unlimited")) unlimited =
                        utils.getInteger(document.getString("unlimited")) == 1
                }
                if (isActive) {
                    checkKey()
                } else {
                    saveKeyState()
                }
            })
            .addOnFailureListener(OnFailureListener { e: Exception? ->
                failCounter++
                if (failCounter > 1) {
                    var sub = licenseKey!!
                    if (sub.length > 6) sub = sub.substring(0, 6) + "***"
                    utils.error("key " + sub + " read error: " + e!!.message)
                    saveKeyState()
                } else {
                    goOnline()
                }
            })
    }

    /**
     * Try to enable network access for the Firebase client
     */
    private fun goOnline() {
        val firestore = FirebaseFirestore.getInstance()
        firestore.enableNetwork()
            .addOnCompleteListener(OnCompleteListener { task: Task<Void?>? ->
                utils.debug("Firebase: online mode restored")
                readCloudData()
            })
            .addOnFailureListener(OnFailureListener { e: Exception? -> utils.error("Firebase: enabling network failure; $e") })
    }

    private fun saveKeyState() {
        var sub = licenseKey!!
        if (sub.length > 6) sub = sub.substring(0, 6) + "***"
        utils.debug("Key $sub; active $isActive; size $keySize; max.size $maxSize; unlimited $unlimited")

        val keyOptions = DataBaseItem()
        keyOptions.put("isActive", isActive)
        keyOptions.put("keySize", keySize)
        keyOptions.put("maxSize", maxSize)
        keyOptions.put("unlimited", unlimited)

        if (listener != null) listener.onValidationResult(keyOptions)

        if (isActive) {
            val time = utils.currentTime()
            val date = Date(time * 1000)
            val document: MutableMap<String?, Any?> = HashMap<String?, Any?>()
            document.put("userID", userID)
            document.put("date", date)
            document.put("time", time)
            if (!userAdded) document.put("dateAdded", date)

            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("keys")
                .document(licenseKey!!)
                .collection("users")
                .document(userID!!)
                .set(document, SetOptions.merge())

            if (!userAdded) {
                keySize++
                document.clear()
                document.put("keySize", keySize)
                firestore.collection("keys")
                    .document(licenseKey!!)
                    .set(document, SetOptions.merge())
            }
        }
    }

    private fun checkKey() {
        if (licenseKey!!.length < 8) {
            utils.warn("Wrong key: $licenseKey")
            isActive = false
            saveKeyState()
            return
        }

        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("keys")
            .document(licenseKey!!)
            .collection("users")
            .get()
            .addOnCompleteListener(OnCompleteListener { task: Task<QuerySnapshot?>? ->
                val key = licenseKey!!.substring(0, 4) + "***"
                val users = ArrayList<String?>()
                if (task!!.isSuccessful()) {
                    for (document in task.getResult()!!) {
                        users.add(document.getString("userID"))
                    }
                    keySize = users.size
                    userAdded = users.contains(userID)
                    if (!userAdded && keySize >= maxSize && maxSize > 0) {
                        isActive = false
                        utils.warn("#$key; max: $maxSize; size: $keySize")
                    }
                } else {
                    utils.debug("error reading key " + key + "; " + task.getException())
                }
                saveKeyState()
            })
            .addOnFailureListener(OnFailureListener { e: Exception? ->
                utils.error("lm check key: $e")
                saveKeyState()
            })
    }
}

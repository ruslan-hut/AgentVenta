package ua.com.programmer.agentventa.shared

import android.content.SharedPreferences
import android.widget.ImageView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.resource.bitmap.Rotate
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.com.programmer.agentventa.BuildConfig
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.dao.cloud.CUserAccount
import ua.com.programmer.agentventa.dao.entity.ClientImage
import ua.com.programmer.agentventa.dao.entity.LClient
import ua.com.programmer.agentventa.dao.entity.LProduct
import ua.com.programmer.agentventa.dao.entity.PaymentType
import ua.com.programmer.agentventa.dao.entity.PriceType
import ua.com.programmer.agentventa.dao.entity.UserAccount
import ua.com.programmer.agentventa.dao.entity.fileName
import ua.com.programmer.agentventa.dao.entity.getBaseUrl
import ua.com.programmer.agentventa.dao.entity.hasImageData
import ua.com.programmer.agentventa.dao.entity.isDemo
import ua.com.programmer.agentventa.http.Result
import ua.com.programmer.agentventa.logger.Logger
import ua.com.programmer.agentventa.repository.FilesRepository
import ua.com.programmer.agentventa.repository.NetworkRepository
import ua.com.programmer.agentventa.repository.OrderRepository
import ua.com.programmer.agentventa.repository.UserAccountRepository
import ua.com.programmer.agentventa.settings.UserOptions
import ua.com.programmer.agentventa.settings.UserOptionsBuilder
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SharedViewModel @Inject constructor(
    private val userAccountRepository: UserAccountRepository,
    private val networkRepository: NetworkRepository,
    private val filesRepository: FilesRepository,
    private val logger: Logger,
    private val imager: RequestManager,
    private val orderRepository: OrderRepository,
    private val preference: SharedPreferences,
): ViewModel() {

    private val logTag = "Shared"

    private val _currentAccount = MutableLiveData<UserAccount>()
    val currentAccount get() = _currentAccount
    private var _options = UserOptions(isEmpty = true)
    val options get() = _options
    private var _priceTypes = listOf<PriceType>()
    val priceTypes get() = _priceTypes
    private var _paymentTypes = listOf<PaymentType>()
    val paymentTypes get() = _paymentTypes

    val barcode = MutableLiveData<String>()

    private val _sharedParams = MutableLiveData<SharedParameters>()
    val sharedParams get() = _sharedParams
    val state get() = _sharedParams.value ?: SharedParameters()

    private val _updateState = MutableLiveData<Result>()
    val updateState get() = _updateState
    private val _isRefreshing = MutableLiveData<Boolean>()
    val isRefreshing get() = _isRefreshing
    private var _progressMessage = ""
    val progressMessage get() = _progressMessage

    private var _baseUrl = ""
    private var _headers: LazyHeaders? = null

    var cacheDir: File? = null
        private set

    val documentTotals get() = _sharedParams.switchMap {
        orderRepository.watchDocumentTotals(it.orderGuid).asLiveData()
    }

    var selectClientAction: (LClient, () -> Unit) -> Unit = { _, _ -> }
    var selectProductAction: (LProduct?, () -> Unit) -> Unit = { _, _ -> }

    fun toggleSortByName() {
        _sharedParams.value = state.copy(sortByName = !state.sortByName)
    }

    // method is used in the product list screen menu
    fun toggleRestsOnly() {
        _sharedParams.value = state.copy(restsOnly = !state.restsOnly)
        preference.edit().putBoolean("show_rests_only", state.restsOnly).apply()
    }

    // method is used in a preference screen and should not change the value of the preference
    fun setRestsOnly(value: Boolean) {
        _sharedParams.value = state.copy(restsOnly = value)
    }

    fun toggleClientProducts() {
        _sharedParams.value = state.copy(clientProducts = !state.clientProducts)
    }

    fun setPrice(description: String) {
        _sharedParams.value = state.copy(priceType = getPriceTypeCode(description))
    }

    fun setDocumentGuid(guid: String) {
        _sharedParams.value = state.copy(orderGuid = guid)
    }

    fun getPriceTypeCode(description: String): String {
        return priceTypes.find { it.description == description }?.priceType ?: ""
    }

    fun getPriceDescription(code: String): String {
        return priceTypes.find { it.priceType == code }?.description ?: ""
    }

    fun getPaymentType(description: String): PaymentType {
        return paymentTypes.find { it.description == description } ?: PaymentType()
    }

    fun clearActions() {
        setDocumentGuid("")
        selectClientAction = { _, _ -> }
        selectProductAction = { _, _ -> }
    }

    fun setCacheDir(dir: File) {
        cacheDir = dir
    }

    fun fileInCache(fileName: String): File {
        return File(cacheDir, fileName)
    }

    fun deleteFileInCache(fileName: String) {
        try {
            val file = fileInCache(fileName)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            logger.e(logTag, "delete file: ${e.message}")
        }
    }

    init {
        logger.cleanUp()
        viewModelScope.launch {
            if (!userAccountRepository.hasAccounts()) {
                setupDemoAccount()
            }
        }
        viewModelScope.launch {
            userAccountRepository.currentAccount.collect {
                val account = it ?: UserAccount(guid = "")
                _baseUrl = account.getBaseUrl()
                _options = UserOptionsBuilder.build(account)
                _sharedParams.value = state.copy(
                        currentAccount = account.guid,
                        priceType = "",
                    )
                _currentAccount.value = account

                _priceTypes = orderRepository.getPriceTypes()
                _paymentTypes = orderRepository.getPaymentTypes()

                if (account.guid.isNotEmpty() && !account.isDemo()) {
                    FirebaseCrashlytics.getInstance().setUserId(account.guid)
                    sendUserInfo()
                }
            }
        }
        preference.getBoolean("show_rests_only", false).let {
            _sharedParams.value = state.copy(restsOnly = it)
        }

    }

    private fun sendUserInfo() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInWithEmailAndPassword(BuildConfig.FIREBASE_EMAIL, BuildConfig.FIREBASE_PASSWORD)
                .addOnSuccessListener { sendUserInfoContinue() }
                .addOnFailureListener { e -> logger.w("FA", e.message ?: "sign in failed") }
        } else {
            sendUserInfoContinue()
        }
    }

    private fun sendUserInfoContinue() {
        val account = CUserAccount.build(_currentAccount.value)
        if (account.guid.isBlank()) return
        viewModelScope.launch {
            val firebase = FirebaseFirestore.getInstance()
            firebase.collection("users_venta")
                .document(account.guid)
                .set(account)
        }
    }

    private fun getHeaders(): LazyHeaders {
        if (_headers == null) {
            val encodedAuth = android.util.Base64.encodeToString(
                "${_currentAccount.value?.dbUser ?: ""}:${_currentAccount.value?.dbPassword ?: ""}".toByteArray(),
                android.util.Base64.NO_WRAP
            )
            _headers = LazyHeaders.Builder()
                .addHeader("Authorization", "Basic $encodedAuth")
                .build()
        }
        return _headers!!
    }

    /**
     * Returns the full image URL based on the provided parameters.
     *
     * @param base The base URL to use if the `url` parameter is blank.
     * @param guid The unique identifier for the image, used in constructing the URL if `url` is blank.
     * @param url The image URL. If this is not blank, it will be returned as-is.
     *
     * @return The full image URL. If `url` is blank, constructs the URL using `base` and `guid`.
     */
    private fun getImageUrl(base: String, guid: String, url: String): String {
        return url.ifBlank { "$base/image/$guid" }
    }

    fun loadImage(product: LProduct, view: ImageView, rotation: Int = 0) {

        if (!options.loadImages) return
        if (!product.hasImageData()) return

        val url = getImageUrl(_baseUrl, product.imageGuid ?: "", product.imageUrl ?: "")
        val glideUrl = GlideUrl(
            url,
            getHeaders()
        )
        imager.load(glideUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.baseline_downloading_24)
            .error(R.drawable.baseline_error_outline_24)
            .transform(Rotate(rotation))
            .into(view)

    }

    fun loadClientImage(image: ClientImage, view: ImageView, rotation: Int = 0) {

        if (image.isLocal == 0) {

            // if file was saved in a local cache, delete it after sending to the server
            val imageFile = fileInCache(image.fileName())
            try {
                if (imageFile.exists()) {
                    imageFile.delete()
                }
            } catch (e: Exception) {
                logger.e(logTag, "delete file: ${e.message}")
            }

            val url = getImageUrl(_baseUrl, image.guid, image.url)
            val glideUrl = GlideUrl(
                url,
                getHeaders()
            )
            imager.load(glideUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.baseline_downloading_24)
                .error(R.drawable.baseline_error_outline_24)
                .transform(Rotate(rotation))
                .into(view)
        } else {
            imager.load(fileInCache(image.fileName()))
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .placeholder(R.drawable.baseline_downloading_24)
                .error(R.drawable.baseline_error_outline_24)
                .transform(Rotate(rotation))
                .into(view)
        }

    }

    private suspend fun setupDemoAccount() {
        val demo = UserAccount.buildDemo()
        userAccountRepository.saveAccount(demo)
        userAccountRepository.setIsCurrent(demo.guid)
    }

    fun callDiffSync(afterSync: () -> Unit) {
        if (_isRefreshing.value == true) return
        _isRefreshing.value = true
        _progressMessage = ""
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                networkRepository.updateDifferential().collect { result ->
                    withContext(Dispatchers.Main) {
                        _updateState.value = result
                        if (result is Result.Success || result is Result.Error) {
                            _isRefreshing.value = false
                        }
                    }
                }
            }
            afterSync()
        }
    }

    fun callFullSync(afterSync: () -> Unit) {
        if (_isRefreshing.value == true) return
        _isRefreshing.value = true
        _progressMessage = ""
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                networkRepository.updateAll().collect { result ->
                    withContext(Dispatchers.Main) {
                        _updateState.value = result
                        if (result is Result.Success || result is Result.Error) {
                            _isRefreshing.value = false
                        }
                    }
                }
            }
            afterSync()
        }
    }

    fun callPrintDocument(guid: String, afterSync: (Boolean) -> Unit) {
        if (_isRefreshing.value == true) return afterSync(false)
        if (cacheDir == null) return afterSync(false)
        _isRefreshing.value = true
        _progressMessage = ""
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                networkRepository.getPrintData(guid, cacheDir!!).collect { result ->
                    withContext(Dispatchers.Main) {
                        _updateState.value = result
                        if (result is Result.Success || result is Result.Error) {
                            _isRefreshing.value = false
                            afterSync(result is Result.Success)
                        }
                    }
                }
            }
        }
    }

    fun addProgressText(text: String) {
        _progressMessage = if (_progressMessage.isBlank()) text else "$progressMessage\n$text"
    }

    fun saveClientImage(clientGuid: String, imageGuid: String) {
        val image = ClientImage(
            databaseId = state.currentAccount,
            clientGuid = clientGuid,
            guid = imageGuid,
            url = "",
            description = "",
            timestamp = System.currentTimeMillis(),
            isLocal = 1,
            isSent = 0,
            isDefault = 0,
        )
        val imageFile = fileInCache(image.fileName())
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val imageWithData = if (imageFile.exists()) {
                    image.copy(url = encodeBase64(imageFile))
                } else {
                    logger.e(logTag, "client image file not found: $imageGuid")
                    image
                }
                filesRepository.saveClientImage(imageWithData)
            }
        }
    }

    private fun encodeBase64(file: File): String {
        return try {
            val bytes = file.readBytes()
            android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            logger.e(logTag, "encode Base64: ${e.message}")
            ""
        }
    }

    fun onBarcodeRead(value: String) {
        if (value.isBlank() || value.length < 10) return
        barcode.value = value
    }

    fun clearBarcode() {
        barcode.value = ""
    }

}
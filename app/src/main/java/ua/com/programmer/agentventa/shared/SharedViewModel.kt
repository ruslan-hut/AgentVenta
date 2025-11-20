package ua.com.programmer.agentventa.shared

import android.content.SharedPreferences
import android.widget.ImageView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
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
import ua.com.programmer.agentventa.dao.entity.isDemo
import ua.com.programmer.agentventa.http.Result
import ua.com.programmer.agentventa.logger.Logger
import ua.com.programmer.agentventa.repository.CommonRepository
import ua.com.programmer.agentventa.repository.FilesRepository
import ua.com.programmer.agentventa.repository.NetworkRepository
import ua.com.programmer.agentventa.repository.OrderRepository
import ua.com.programmer.agentventa.repository.UserAccountRepository
import ua.com.programmer.agentventa.settings.UserOptions
import ua.com.programmer.agentventa.settings.UserOptionsBuilder
import java.io.File
import java.util.GregorianCalendar
import javax.inject.Inject
import androidx.core.content.edit
import ua.com.programmer.agentventa.dao.entity.Company
import ua.com.programmer.agentventa.dao.entity.Store
import ua.com.programmer.agentventa.license.LicenseManager

@HiltViewModel
class SharedViewModel @Inject constructor(
    private val userAccountRepository: UserAccountRepository,
    private val networkRepository: NetworkRepository,
    private val filesRepository: FilesRepository,
    private val logger: Logger,
    private val imageLoadingManager: ImageLoadingManager,
    private val orderRepository: OrderRepository,
    private val commonRepository: CommonRepository,
    private val preference: SharedPreferences,
    private val lm: LicenseManager,
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
    private val state get() = _sharedParams.value ?: SharedParameters()

    private val _updateState = MutableLiveData<Result>()
    val updateState get() = _updateState
    private val _isRefreshing = MutableLiveData<Boolean>()
    val isRefreshing get() = _isRefreshing
    private var _progressMessage = ""
    val progressMessage get() = _progressMessage

    private var _companies: List<Company> = emptyList()
    private var _stores: List<Store> = emptyList()

    var cacheDir: File? = null
        private set

    val documentTotals get() = _sharedParams.switchMap {
        orderRepository.watchDocumentTotals(it.docGuid).asLiveData()
    }

    var selectClientAction: (LClient, () -> Unit) -> Unit = { _, _ -> }
    var selectProductAction: (LProduct?, () -> Unit) -> Unit = { _, _ -> }

    fun toggleSortByName() {
        _sharedParams.value = state.copy(sortByName = !state.sortByName)
    }

    // method is used in the product list screen menu
    fun toggleRestsOnly() {
        _sharedParams.value = state.copy(restsOnly = !state.restsOnly)
        preference.edit { putBoolean("show_rests_only", state.restsOnly) }
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

    fun setDocumentGuid(type: String = "", guid: String = "", companyGuid: String = "", storeGuid: String = "") {
        if (guid.isBlank()) {
            _sharedParams.value = state.copy(
                docType = type,
                docGuid = guid,
            )
        }else{
            _sharedParams.value = state.copy(
                docType = type,
                docGuid = guid,
                companyGuid = companyGuid,
                company = _companies.find { it.guid == companyGuid }?.description ?: "",
                storeGuid = storeGuid,
                store = _stores.find { it.guid == storeGuid }?.description ?: "",
            )
        }
    }

    fun setCompany(guid: String) {
        _sharedParams.value = state.copy(
            companyGuid = guid,
            company = _companies.find { it.guid == guid }?.description ?: "",
        )
    }

    fun setStore(guid: String) {
        _sharedParams.value = state.copy(
            storeGuid = guid,
            store = _stores.find { it.guid == guid }?.description ?: "",
        )
    }

    private fun setDefaults() {
        val company = _companies.find { it.isDefault == 1 } ?: Company()
        val store = _stores.find { it.isDefault == 1 } ?: Store()

        _sharedParams.value = state.copy(
            company = company.description,
            companyGuid = company.guid,
            store = store.description,
            storeGuid = store.guid,
        )
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
        setDocumentGuid()
        selectClientAction = { _, _ -> }
        selectProductAction = { _, _ -> }
    }

    fun setCacheDir(dir: File) {
        cacheDir = dir
        imageLoadingManager.setCacheDir(dir)
    }

    fun fileInCache(fileName: String): File {
        return imageLoadingManager.fileInCache(fileName)
    }

    fun deleteFileInCache(fileName: String) {
        imageLoadingManager.deleteFileInCache(fileName)
    }

    init {
        deleteOldData()
        logger.cleanUp()
        viewModelScope.launch {
            if (!userAccountRepository.hasAccounts()) {
                setupDemoAccount()
            }
        }
        viewModelScope.launch {
            userAccountRepository.currentAccount.collect {
                val account = it ?: UserAccount(guid = "")
                imageLoadingManager.configure(account)
                _options = UserOptionsBuilder.build(account)
                _sharedParams.value = state.copy(
                        currentAccount = account.guid,
                        priceType = "",
                    )
                _currentAccount.value = account

                _priceTypes = orderRepository.getPriceTypes()
                _paymentTypes = orderRepository.getPaymentTypes()

                _companies = orderRepository.getCompanies()
                _stores = orderRepository.getStores()

                setDefaults()

                if (account.guid.isNotEmpty() && !account.isDemo()) {
                    FirebaseCrashlytics.getInstance().setUserId(account.guid)
                    sendUserInfo()
                }
            }
        }
        preference.getBoolean("show_rests_only", false).let {
            _sharedParams.value = state.copy(restsOnly = it)
        }
        preference.getBoolean("ignore_sequential_barcodes", false).let {
            _sharedParams.value = state.copy(ignoreBarcodeReads = it)
        }

    }

//    private fun sendUserInfo() {
//        val auth = FirebaseAuth.getInstance()
//        if (auth.currentUser == null) {
//            auth.signInWithEmailAndPassword(BuildConfig.FIREBASE_EMAIL, BuildConfig.FIREBASE_PASSWORD)
//                .addOnSuccessListener { sendUserInfoContinue() }
//                .addOnFailureListener { e -> logger.w("FA", e.message ?: "sign in failed") }
//        } else {
//            sendUserInfoContinue()
//        }
//    }

    private fun sendUserInfo() {
        val account = CUserAccount.build(_currentAccount.value)
        if (account.guid.isBlank()) return
        viewModelScope.launch {
//            val firebase = FirebaseFirestore.getInstance()
//            firebase.collection("users_venta")
//                .document(account.guid)
//                .set(account)
            lm.getLicense(account)
        }
    }

    fun loadImage(product: LProduct, view: ImageView, rotation: Int = 0) {
        imageLoadingManager.loadProductImage(product, view, rotation, options.loadImages)
    }

    fun loadClientImage(image: ClientImage, view: ImageView, rotation: Int = 0) {
        imageLoadingManager.loadClientImage(image, view, rotation)
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
                    image.copy(url = imageLoadingManager.encodeBase64(imageFile))
                } else {
                    logger.e(logTag, "client image file not found: $imageGuid")
                    image
                }
                filesRepository.saveClientImage(imageWithData)
            }
        }
    }

    fun onBarcodeRead(value: String) {
        if (value.isBlank() || value.length < 10) return
        barcode.value = value
    }

    fun clearBarcode() {
        barcode.value = ""
    }

    /**
     * Deletes old data from the repository.
     *
     * This method calculates a timestamp for 60 days prior to the current date
     * and triggers the cleanup process in the `CommonRepository` to delete
     * data older than this timestamp.
     */
    private fun deleteOldData() {
        val currentTime = GregorianCalendar.getInstance().timeInMillis / 1000
        // take 60 days from the current date
        val from = currentTime - 60 * 24 * 60 * 60
        viewModelScope.launch {
            commonRepository.cleanup(from)
        }
    }

    fun getCompanies(loadList: (List<Company>) -> Unit) {
        viewModelScope.launch {
          loadList(orderRepository.getCompanies())
        }
    }

    fun getStores(loadList: (List<Store>) -> Unit) {
        viewModelScope.launch {
            loadList(orderRepository.getStores())
        }
    }
}
package ua.com.programmer.agentventa.shared

import android.content.SharedPreferences
import android.widget.ImageView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.com.programmer.agentventa.dao.entity.ClientImage
import ua.com.programmer.agentventa.dao.entity.Company
import ua.com.programmer.agentventa.dao.entity.DocumentTotals
import ua.com.programmer.agentventa.dao.entity.LClient
import ua.com.programmer.agentventa.dao.entity.LProduct
import ua.com.programmer.agentventa.dao.entity.PaymentType
import ua.com.programmer.agentventa.dao.entity.PriceType
import ua.com.programmer.agentventa.dao.entity.Store
import ua.com.programmer.agentventa.dao.entity.UserAccount
import ua.com.programmer.agentventa.dao.entity.fileName
import ua.com.programmer.agentventa.http.Result
import ua.com.programmer.agentventa.logger.Logger
import ua.com.programmer.agentventa.repository.CommonRepository
import ua.com.programmer.agentventa.repository.FilesRepository
import ua.com.programmer.agentventa.repository.NetworkRepository
import ua.com.programmer.agentventa.repository.OrderRepository
import ua.com.programmer.agentventa.settings.UserOptions
import java.io.File
import java.util.GregorianCalendar
import javax.inject.Inject
import androidx.core.content.edit

/**
 * Sync operation events (one-time).
 */
sealed class SyncEvent {
    data class Progress(val message: String) : SyncEvent()
    data class Success(val message: String) : SyncEvent()
    data class Error(val message: String) : SyncEvent()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SharedViewModel @Inject constructor(
    private val networkRepository: NetworkRepository,
    private val filesRepository: FilesRepository,
    private val logger: Logger,
    private val imageLoadingManager: ImageLoadingManager,
    private val orderRepository: OrderRepository,
    private val commonRepository: CommonRepository,
    private val preference: SharedPreferences,
    private val accountStateManager: AccountStateManager,
) : ViewModel() {

    private val logTag = "Shared"

    // Account state from AccountStateManager
    private val _currentAccount = MutableStateFlow(UserAccount(guid = ""))
    val currentAccountFlow: StateFlow<UserAccount> = _currentAccount.asStateFlow()

    // LiveData for XML binding and legacy observe() calls
    val currentAccount: androidx.lifecycle.LiveData<UserAccount> = _currentAccount.asLiveData()

    val options: UserOptions get() = accountStateManager.options.value
    val priceTypes: List<PriceType> get() = accountStateManager.priceTypes.value
    val paymentTypes: List<PaymentType> get() = accountStateManager.paymentTypes.value

    // Barcode state
    private val _barcode = MutableStateFlow("")
    val barcodeFlow: StateFlow<String> = _barcode.asStateFlow()
    val barcode: androidx.lifecycle.LiveData<String> = _barcode.asLiveData()

    // Shared parameters state
    private val _sharedParams = MutableStateFlow(SharedParameters())
    val sharedParamsFlow: StateFlow<SharedParameters> = _sharedParams.asStateFlow()
    val sharedParams: androidx.lifecycle.LiveData<SharedParameters> = _sharedParams.asLiveData()

    // Sync state
    private val _updateState = MutableStateFlow<Result?>(null)
    val updateStateFlow: StateFlow<Result?> = _updateState.asStateFlow()
    val updateState: androidx.lifecycle.LiveData<Result?> = _updateState.asLiveData()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshingFlow: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    val isRefreshing: androidx.lifecycle.LiveData<Boolean> = _isRefreshing.asLiveData()

    private val _progressMessage = MutableStateFlow("")
    val progressMessageFlow: StateFlow<String> = _progressMessage.asStateFlow()
    val progressMessage: String get() = _progressMessage.value

    // Sync events channel
    private val _syncEvents = EventChannel<SyncEvent>()
    val syncEvents = _syncEvents.flow

    var cacheDir: File? = null
        private set

    // Document totals as StateFlow
    private val _documentTotalsFlow: StateFlow<DocumentTotals> = _sharedParams
        .flatMapLatest { params ->
            if (params.docGuid.isEmpty()) flowOf(DocumentTotals())
            else orderRepository.watchDocumentTotals(params.docGuid)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DocumentTotals()
        )
    val documentTotals: androidx.lifecycle.LiveData<DocumentTotals> = _documentTotalsFlow.asLiveData()

    var selectClientAction: (LClient, () -> Unit) -> Unit = { _, _ -> }
    var selectProductAction: (LProduct?, () -> Unit) -> Unit = { _, _ -> }

    fun toggleSortByName() {
        _sharedParams.value = _sharedParams.value.copy(sortByName = !_sharedParams.value.sortByName)
    }

    fun toggleRestsOnly() {
        val newValue = !_sharedParams.value.restsOnly
        _sharedParams.value = _sharedParams.value.copy(restsOnly = newValue)
        preference.edit { putBoolean("show_rests_only", newValue) }
    }

    fun setRestsOnly(value: Boolean) {
        _sharedParams.value = _sharedParams.value.copy(restsOnly = value)
    }

    fun toggleClientProducts() {
        _sharedParams.value = _sharedParams.value.copy(clientProducts = !_sharedParams.value.clientProducts)
    }

    fun setPrice(description: String) {
        _sharedParams.value = _sharedParams.value.copy(priceType = getPriceTypeCode(description))
    }

    fun setDocumentGuid(type: String = "", guid: String = "", companyGuid: String = "", storeGuid: String = "") {
        if (guid.isBlank()) {
            _sharedParams.value = _sharedParams.value.copy(docType = type, docGuid = guid)
        } else {
            _sharedParams.value = _sharedParams.value.copy(
                docType = type,
                docGuid = guid,
                companyGuid = companyGuid,
                company = accountStateManager.findCompany(companyGuid)?.description ?: "",
                storeGuid = storeGuid,
                store = accountStateManager.findStore(storeGuid)?.description ?: "",
            )
        }
    }

    fun setCompany(guid: String) {
        _sharedParams.value = _sharedParams.value.copy(
            companyGuid = guid,
            company = accountStateManager.findCompany(guid)?.description ?: "",
        )
    }

    fun setStore(guid: String) {
        _sharedParams.value = _sharedParams.value.copy(
            storeGuid = guid,
            store = accountStateManager.findStore(guid)?.description ?: "",
        )
    }

    private fun setDefaults() {
        val company = accountStateManager.defaultCompany.value
        val store = accountStateManager.defaultStore.value

        _sharedParams.value = _sharedParams.value.copy(
            company = company.description,
            companyGuid = company.guid,
            store = store.description,
            storeGuid = store.guid,
        )
    }

    // Delegate to AccountStateManager
    fun getPriceTypeCode(description: String): String = accountStateManager.getPriceTypeCode(description)
    fun getPriceDescription(code: String): String = accountStateManager.getPriceDescription(code)
    fun getPaymentType(description: String): PaymentType = accountStateManager.getPaymentType(description)

    fun clearActions() {
        setDocumentGuid()
        selectClientAction = { _, _ -> }
        selectProductAction = { _, _ -> }
    }

    fun setCacheDir(dir: File) {
        cacheDir = dir
        imageLoadingManager.setCacheDir(dir)
    }

    fun fileInCache(fileName: String): File = imageLoadingManager.fileInCache(fileName)

    fun deleteFileInCache(fileName: String) = imageLoadingManager.deleteFileInCache(fileName)

    init {
        deleteOldData()
        logger.cleanUp()

        // Listen for account changes
        accountStateManager.addAccountChangeListener { account ->
            imageLoadingManager.configure(account)
            _sharedParams.value = _sharedParams.value.copy(
                currentAccount = account.guid,
                priceType = "",
            )
            _currentAccount.value = account
            setDefaults()
        }

        // Load preferences
        _sharedParams.value = _sharedParams.value.copy(
            restsOnly = preference.getBoolean("show_rests_only", false),
            ignoreBarcodeReads = preference.getBoolean("ignore_sequential_barcodes", false)
        )
    }

    fun loadImage(product: LProduct, view: ImageView, rotation: Int = 0) {
        imageLoadingManager.loadProductImage(product, view, rotation, options.loadImages)
    }

    fun loadClientImage(image: ClientImage, view: ImageView, rotation: Int = 0) {
        imageLoadingManager.loadClientImage(image, view, rotation)
    }

    fun callDiffSync(afterSync: () -> Unit) {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        _progressMessage.value = ""
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                networkRepository.updateDifferential().collect { result ->
                    withContext(Dispatchers.Main) {
                        _updateState.value = result
                        handleSyncResult(result)
                    }
                }
            }
            afterSync()
        }
    }

    fun callFullSync(afterSync: () -> Unit) {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        _progressMessage.value = ""
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                networkRepository.updateAll().collect { result ->
                    withContext(Dispatchers.Main) {
                        _updateState.value = result
                        handleSyncResult(result)
                    }
                }
            }
            afterSync()
        }
    }

    private fun handleSyncResult(result: Result) {
        when (result) {
            is Result.Success -> {
                _isRefreshing.value = false
                _syncEvents.send(SyncEvent.Success(result.message))
            }
            is Result.Error -> {
                _isRefreshing.value = false
                _syncEvents.send(SyncEvent.Error(result.message))
            }
            is Result.Progress -> {
                _syncEvents.send(SyncEvent.Progress(result.message))
            }
        }
    }

    fun callPrintDocument(guid: String, afterSync: (Boolean) -> Unit) {
        if (_isRefreshing.value) return afterSync(false)
        if (cacheDir == null) return afterSync(false)
        _isRefreshing.value = true
        _progressMessage.value = ""
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
        val current = _progressMessage.value
        _progressMessage.value = if (current.isBlank()) text else "$current\n$text"
    }

    fun saveClientImage(clientGuid: String, imageGuid: String) {
        val image = ClientImage(
            databaseId = _sharedParams.value.currentAccount,
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
        _barcode.value = value
    }

    fun clearBarcode() {
        _barcode.value = ""
    }

    private fun deleteOldData() {
        val currentTime = GregorianCalendar.getInstance().timeInMillis / 1000
        val from = currentTime - 60 * 24 * 60 * 60
        viewModelScope.launch {
            commonRepository.cleanup(from)
        }
    }

    fun getCompanies(loadList: (List<Company>) -> Unit) {
        loadList(accountStateManager.companies.value)
    }

    fun getStores(loadList: (List<Store>) -> Unit) {
        loadList(accountStateManager.stores.value)
    }
}

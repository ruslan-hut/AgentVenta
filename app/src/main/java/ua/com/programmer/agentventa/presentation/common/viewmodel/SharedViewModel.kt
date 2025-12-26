package ua.com.programmer.agentventa.presentation.common.viewmodel

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
import ua.com.programmer.agentventa.data.local.entity.ClientImage
import ua.com.programmer.agentventa.data.local.entity.Company
import ua.com.programmer.agentventa.data.local.entity.DocumentTotals
import ua.com.programmer.agentventa.data.local.entity.LClient
import ua.com.programmer.agentventa.data.local.entity.LProduct
import ua.com.programmer.agentventa.data.local.entity.PaymentType
import ua.com.programmer.agentventa.data.local.entity.PriceType
import ua.com.programmer.agentventa.data.local.entity.Store
import ua.com.programmer.agentventa.data.local.entity.UserAccount
import ua.com.programmer.agentventa.data.local.entity.fileName
import ua.com.programmer.agentventa.data.remote.Result
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import ua.com.programmer.agentventa.domain.repository.CommonRepository
import ua.com.programmer.agentventa.domain.repository.FilesRepository
import ua.com.programmer.agentventa.domain.repository.OrderRepository
import ua.com.programmer.agentventa.presentation.features.settings.UserOptions
import java.io.File
import java.util.GregorianCalendar
import javax.inject.Inject
import androidx.core.content.edit

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SharedViewModel @Inject constructor(
    private val filesRepository: FilesRepository,
    private val logger: Logger,
    private val imageLoadingManager: ImageLoadingManager,
    private val orderRepository: OrderRepository,
    private val commonRepository: CommonRepository,
    private val preference: SharedPreferences,
    private val accountStateManager: AccountStateManager,
    private val syncManager: SyncManager,
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

    // Sync state (delegated to SyncManager)
    val updateStateFlow: StateFlow<Result?> = syncManager.updateState
    val updateState: androidx.lifecycle.LiveData<Result?> = syncManager.updateState.asLiveData()

    val isRefreshingFlow: StateFlow<Boolean> = syncManager.isRefreshing
    val isRefreshing: androidx.lifecycle.LiveData<Boolean> = syncManager.isRefreshing.asLiveData()

    val progressMessageFlow: StateFlow<String> = syncManager.progressMessage
    val progressMessage: String get() = syncManager.progressMessage.value

    val syncEvents = syncManager.syncEvents

    // Snackbar events for WebSocket notifications
    val snackbarEvents = syncManager.snackbarEvents

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
        syncManager.callDiffSync(viewModelScope, afterSync)
    }

    fun callFullSync(afterSync: () -> Unit) {
        syncManager.callFullSync(viewModelScope, afterSync)
    }

    fun callPrintDocument(guid: String, afterSync: (Boolean) -> Unit) {
        syncManager.callPrintDocument(viewModelScope, guid, cacheDir, afterSync)
    }

    fun addProgressText(text: String) {
        syncManager.addProgressText(text)
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

package ua.com.programmer.agentventa.fiscal

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.com.programmer.agentventa.dao.entity.UserAccount
import ua.com.programmer.agentventa.fiscal.checkbox.Checkbox
import ua.com.programmer.agentventa.logger.Logger
import ua.com.programmer.agentventa.repository.OrderRepository
import ua.com.programmer.agentventa.repository.UserAccountRepository
import ua.com.programmer.agentventa.settings.UserOptions
import ua.com.programmer.agentventa.settings.UserOptionsBuilder
import ua.com.programmer.agentventa.utility.Constants
import java.io.File
import javax.inject.Inject
import kotlin.reflect.KSuspendFunction2

@HiltViewModel
class FiscalViewModel @Inject constructor(
    private val userAccountRepository: UserAccountRepository,
    private val orderRepository: OrderRepository,
    private val logger: Logger
): ViewModel() {

    val isLoading = MutableLiveData<Boolean>()
    val message = MutableLiveData<String>()
    val state = MutableLiveData<FiscalState>()
    val operationResult = MutableLiveData<OperationResult?>()

    var fiscalOptions = FiscalOptions()
    private var fiscalService: FiscalService? = null

    init {
        viewModelScope.launch {
            userAccountRepository.currentAccount.collect {
                val account = it ?: UserAccount(guid = "")
                val userOptions = UserOptionsBuilder.build(account)
                if (userOptions.fiscalProvider.isEmpty()) {
                    fiscalService = null
                    fiscalOptions = FiscalOptions()
                }
            }
        }
    }

    fun clearOperationResult() {
        operationResult.value = null
    }

    /**
     * Initialises fiscal options and service based on the given user options.
     *
     * @param userOptions The options for the current user containing fiscal settings.
     * @return Boolean indicating success or failure of initialisation.
     * - Returns `false` if the `fiscalProvider` field in the `userOptions` is empty.
     * - Otherwise, it initialises `fiscalOptions` based on `userOptions`.
     * - Checks if the current `fiscalService` matches the `fiscalProvider` in `userOptions`.
     * - If not, it re-initialises `fiscalService` accordingly.
     * - Updates the `state` to reflect the current state of `fiscalService`.
     * - Returns `true` if `fiscalService` is successfully initialised, otherwise returns `false`.
     */
    fun initialise(userOptions: UserOptions, cache: File?): Boolean {
        if (userOptions.fiscalProvider.isEmpty()) {
            fiscalService = null
            fiscalOptions = FiscalOptions()
            return false
        }
        fiscalOptions = FiscalOptions(
            fiscalNumber = userOptions.fiscalNumber,
            cashier = userOptions.fiscalCashier,
            provider = userOptions.fiscalProvider,
            deviceId = userOptions.fiscalDeviceId,
            fileDir = cache,
        )
        val id = fiscalService?.serviceId ?: ""
        if (id != userOptions.fiscalProvider) {
            fiscalService = when (userOptions.fiscalProvider) {
                Constants.FISCAL_PROVIDER_CHECKBOX -> Checkbox(orderRepository)
                else -> null
            }
        }
        state.value = fiscalService?.currentState()
        return fiscalService != null
    }

    fun isNotReady() = fiscalService == null

    fun onCashierLogin() {
        callService(FiscalService::cashierLogin) {
            operationResult.value = it
        }
    }

    fun onCashierLogout() {
        callService(FiscalService::cashierLogout){
            operationResult.value = it
        }
    }

    fun onCheckStatus() {
        callService(FiscalService::checkStatus){
            operationResult.value = it
        }
    }

    fun onOpenShift() {
        callService(FiscalService::openShift){
            operationResult.value = it
        }
    }

    fun onCloseShift() {
        callService(FiscalService::closeShift){
            operationResult.value = it
        }
    }

    fun onCreateXReport() {
        callService(FiscalService::createXReport){
            operationResult.value = it
        }
    }

    fun createReceipt(orderGuid: String) {
        fiscalOptions = fiscalOptions.copy(orderGuid = orderGuid)
        callService(FiscalService::createReceipt){
            operationResult.value = it
        }
    }

    fun createServiceReceipt(value: Int) {
        fiscalOptions = fiscalOptions.copy(value = value)
        callService(FiscalService::createServiceReceipt){
            operationResult.value = it
        }
    }

    fun getReceipt(orderGuid: String) {
        fiscalOptions = fiscalOptions.copy(orderGuid = orderGuid)
        callService(FiscalService::getReceipt){
            operationResult.value = it
        }
    }

    fun getReceiptText(orderGuid: String) {
        fiscalOptions = fiscalOptions.copy(orderGuid = orderGuid)
        callService(FiscalService::getReceiptText){
            operationResult.value = it
        }
    }

    private fun callService(action: KSuspendFunction2<FiscalService, FiscalOptions, OperationResult>, onResult: (OperationResult) -> Unit = {}) {
        if (isLoading.value == true) return
        if (fiscalService == null) {
            onResult(OperationResult(false, "Не підключено фіскальний сервіс"))
            return
        }
        isLoading.value = true
        viewModelScope.launch{
            var actionResult = OperationResult(false, "Не вдалося виконати запит")
            withContext(Dispatchers.IO) {
                fiscalService?.let {service ->
                    val initResult = service.init(fiscalOptions)
                    actionResult = if (initResult.success) {
                        action(service, fiscalOptions)
                    } else {
                        initResult
                    }
                }
            }
            if (!actionResult.success) {
                logger.w("FS", actionResult.message)
            }
            state.value = fiscalService?.currentState() ?: FiscalState()
            isLoading.value = false
            withContext(Dispatchers.Main) {
                onResult(actionResult)
            }
        }
    }
}
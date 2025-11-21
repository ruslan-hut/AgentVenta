package ua.com.programmer.agentventa.shared

import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.dao.cloud.CUserAccount
import ua.com.programmer.agentventa.dao.entity.Company
import ua.com.programmer.agentventa.dao.entity.PaymentType
import ua.com.programmer.agentventa.dao.entity.PriceType
import ua.com.programmer.agentventa.dao.entity.Store
import ua.com.programmer.agentventa.dao.entity.UserAccount
import ua.com.programmer.agentventa.dao.entity.isDemo
import ua.com.programmer.agentventa.license.LicenseManager
import ua.com.programmer.agentventa.logger.Logger
import ua.com.programmer.agentventa.repository.OrderRepository
import ua.com.programmer.agentventa.repository.UserAccountRepository
import ua.com.programmer.agentventa.settings.UserOptions
import ua.com.programmer.agentventa.settings.UserOptionsBuilder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton manager for account state that can be shared across ViewModels.
 * Uses StateFlow for reactive state management.
 *
 * This centralizes all account-related state:
 * - Current account and user options
 * - Price types, payment types
 * - Companies and stores
 * - Account switching logic
 */
@Singleton
class AccountStateManager @Inject constructor(
    private val userAccountRepository: UserAccountRepository,
    private val orderRepository: OrderRepository,
    private val licenseManager: LicenseManager,
    private val logger: Logger
) {
    private val logTag = "AccountStateManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Account state
    private val _currentAccount = MutableStateFlow(UserAccount(guid = ""))
    val currentAccount: StateFlow<UserAccount> = _currentAccount.asStateFlow()

    private val _options = MutableStateFlow(UserOptions(isEmpty = true))
    val options: StateFlow<UserOptions> = _options.asStateFlow()

    // Catalog data for current account
    private val _priceTypes = MutableStateFlow<List<PriceType>>(emptyList())
    val priceTypes: StateFlow<List<PriceType>> = _priceTypes.asStateFlow()

    private val _paymentTypes = MutableStateFlow<List<PaymentType>>(emptyList())
    val paymentTypes: StateFlow<List<PaymentType>> = _paymentTypes.asStateFlow()

    private val _companies = MutableStateFlow<List<Company>>(emptyList())
    val companies: StateFlow<List<Company>> = _companies.asStateFlow()

    private val _stores = MutableStateFlow<List<Store>>(emptyList())
    val stores: StateFlow<List<Store>> = _stores.asStateFlow()

    // Default selections
    private val _defaultCompany = MutableStateFlow(Company())
    val defaultCompany: StateFlow<Company> = _defaultCompany.asStateFlow()

    private val _defaultStore = MutableStateFlow(Store())
    val defaultStore: StateFlow<Store> = _defaultStore.asStateFlow()

    // Callbacks for account changes
    private val accountChangeListeners = mutableListOf<(UserAccount) -> Unit>()

    init {
        startAccountObserver()
        ensureAccountExists()
    }

    /**
     * Register a listener for account changes.
     * Returns a function to unregister.
     */
    fun addAccountChangeListener(listener: (UserAccount) -> Unit): () -> Unit {
        accountChangeListeners.add(listener)
        // Immediately notify with current account
        _currentAccount.value.takeIf { it.guid.isNotEmpty() }?.let { listener(it) }
        return { accountChangeListeners.remove(listener) }
    }

    private fun startAccountObserver() {
        scope.launch {
            userAccountRepository.currentAccount.collect { account ->
                val userAccount = account ?: UserAccount(guid = "")

                _currentAccount.value = userAccount
                _options.value = UserOptionsBuilder.build(userAccount)

                if (userAccount.guid.isNotEmpty()) {
                    loadAccountData()

                    if (!userAccount.isDemo()) {
                        FirebaseCrashlytics.getInstance().setUserId(userAccount.guid)
                        sendUserInfo(userAccount)
                    }
                }

                // Notify listeners on main thread
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    accountChangeListeners.forEach { it(userAccount) }
                }
            }
        }
    }

    private fun ensureAccountExists() {
        scope.launch {
            if (!userAccountRepository.hasAccounts()) {
                setupDemoAccount()
            }
        }
    }

    private suspend fun loadAccountData() {
        _priceTypes.value = orderRepository.getPriceTypes()
        _paymentTypes.value = orderRepository.getPaymentTypes()
        _companies.value = orderRepository.getCompanies()
        _stores.value = orderRepository.getStores()

        _defaultCompany.value = _companies.value.find { it.isDefault == 1 } ?: Company()
        _defaultStore.value = _stores.value.find { it.isDefault == 1 } ?: Store()
    }

    private suspend fun setupDemoAccount() {
        val demo = UserAccount.buildDemo()
        userAccountRepository.saveAccount(demo)
        userAccountRepository.setIsCurrent(demo.guid)
    }

    private fun sendUserInfo(account: UserAccount) {
        val cloudAccount = CUserAccount.build(account)
        if (cloudAccount.guid.isBlank()) return
        scope.launch {
            licenseManager.getLicense(cloudAccount)
        }
    }

    // Lookup helpers

    fun getPriceTypeCode(description: String): String {
        return _priceTypes.value.find { it.description == description }?.priceType ?: ""
    }

    fun getPriceDescription(code: String): String {
        return _priceTypes.value.find { it.priceType == code }?.description ?: ""
    }

    fun getPaymentType(description: String): PaymentType {
        return _paymentTypes.value.find { it.description == description } ?: PaymentType()
    }

    fun findCompany(guid: String): Company? {
        return _companies.value.find { it.guid == guid }
    }

    fun findStore(guid: String): Store? {
        return _stores.value.find { it.guid == guid }
    }
}

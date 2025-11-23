package ua.com.programmer.agentventa.presentation.common.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import ua.com.programmer.agentventa.data.local.entity.Company
import ua.com.programmer.agentventa.data.local.entity.PaymentType
import ua.com.programmer.agentventa.data.local.entity.PriceType
import ua.com.programmer.agentventa.data.local.entity.Store
import ua.com.programmer.agentventa.data.local.entity.UserAccount
import ua.com.programmer.agentventa.presentation.features.settings.UserOptions
import javax.inject.Inject

/**
 * ViewModel that exposes account state to UI layer using StateFlow.
 * Delegates to AccountStateManager singleton for actual state management.
 *
 * Use this ViewModel in fragments that need reactive account state
 * without depending on SharedViewModel.
 */
@HiltViewModel
class AccountStateViewModel @Inject constructor(
    private val accountStateManager: AccountStateManager
) : ViewModel() {

    // Expose StateFlows from AccountStateManager
    val currentAccount: StateFlow<UserAccount> = accountStateManager.currentAccount
    val options: StateFlow<UserOptions> = accountStateManager.options
    val priceTypes: StateFlow<List<PriceType>> = accountStateManager.priceTypes
    val paymentTypes: StateFlow<List<PaymentType>> = accountStateManager.paymentTypes
    val companies: StateFlow<List<Company>> = accountStateManager.companies
    val stores: StateFlow<List<Store>> = accountStateManager.stores
    val defaultCompany: StateFlow<Company> = accountStateManager.defaultCompany
    val defaultStore: StateFlow<Store> = accountStateManager.defaultStore

    // Delegate lookup methods
    fun getPriceTypeCode(description: String): String = accountStateManager.getPriceTypeCode(description)
    fun getPriceDescription(code: String): String = accountStateManager.getPriceDescription(code)
    fun getPaymentType(description: String): PaymentType = accountStateManager.getPaymentType(description)
    fun findCompany(guid: String): Company? = accountStateManager.findCompany(guid)
    fun findStore(guid: String): Store? = accountStateManager.findStore(guid)
}

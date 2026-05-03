package ua.com.programmer.agentventa.domain.repository

import ua.com.programmer.agentventa.data.local.entity.UserAccount
import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.data.local.entity.PriceType

interface UserAccountRepository {
    val currentAccount: Flow<UserAccount?>
    val currentAccountGuid: Flow<String>
    val priceTypes: Flow<List<PriceType>?>
    suspend fun saveAccount(account: UserAccount): Long

    /**
     * Atomic read-modify-write on the current account. The transform sees the
     * latest persisted state and its result is saved before any other
     * updateCurrent call observes the row. Use this whenever multiple writers
     * may mutate disjoint fields (e.g. token refresh + WS options push) — a
     * raw getCurrent()+saveAccount() pair races and silently drops one of the
     * updates.
     *
     * Returns the saved account, or null if there is no current account.
     */
    suspend fun updateCurrent(transform: (UserAccount) -> UserAccount): UserAccount?

    fun getAll(): Flow<List<UserAccount>>
    fun getByGuid(guid: String): Flow<UserAccount>
    suspend fun setIsCurrent(guid: String)
    suspend fun deleteByGuid(guid: String): Int
    suspend fun getCurrent(): UserAccount?
    suspend fun hasAccounts(): Boolean
}
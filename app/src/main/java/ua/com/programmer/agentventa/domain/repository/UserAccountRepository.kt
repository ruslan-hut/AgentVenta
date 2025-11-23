package ua.com.programmer.agentventa.domain.repository

import ua.com.programmer.agentventa.data.local.entity.UserAccount
import kotlinx.coroutines.flow.Flow
import ua.com.programmer.agentventa.data.local.entity.PriceType

interface UserAccountRepository {
    val currentAccount: Flow<UserAccount?>
    val currentAccountGuid: Flow<String>
    val priceTypes: Flow<List<PriceType>?>
    suspend fun saveAccount(account: UserAccount): Long
    fun getAll(): Flow<List<UserAccount>>
    fun getByGuid(guid: String): Flow<UserAccount>
    suspend fun setIsCurrent(guid: String)
    suspend fun deleteByGuid(guid: String): Int
    suspend fun getCurrent(): UserAccount?
    suspend fun hasAccounts(): Boolean
}
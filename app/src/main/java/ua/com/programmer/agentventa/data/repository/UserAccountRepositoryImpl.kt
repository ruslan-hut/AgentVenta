package ua.com.programmer.agentventa.data.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.com.programmer.agentventa.data.local.dao.UserAccountDao
import ua.com.programmer.agentventa.data.local.entity.UserAccount
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import ua.com.programmer.agentventa.utility.Constants
import javax.inject.Inject

class UserAccountRepositoryImpl @Inject constructor(
    private val userAccountDao: UserAccountDao
): UserAccountRepository {

    override val currentAccount = userAccountDao.watchCurrent()

    override val currentAccountGuid: Flow<String> = currentAccount
        .map { it?.guid ?: "" }

    override val priceTypes = userAccountDao.getPriceTypes()

    override suspend fun saveAccount(account: UserAccount): Long {
        val upd = userAccountDao.update(account)
        if (upd > 0) return upd.toLong()
        return userAccountDao.insert(account)
    }

    override fun getAll(): Flow<List<UserAccount>> {
        return userAccountDao.getAll()
    }

    override fun getByGuid(guid: String): Flow<UserAccount> {
        return userAccountDao.getByGuid(guid).map { account ->
            account ?: UserAccount(guid = guid, dataFormat = Constants.SYNC_FORMAT_HTTP) }
    }

    override suspend fun setIsCurrent(guid: String) {
        userAccountDao.resetIsCurrent()
        userAccountDao.setIsCurrent(guid)
    }

    override suspend fun deleteByGuid(guid: String): Int {
        var del = userAccountDao.deleteByGuidProduct(guid)
        del += userAccountDao.deleteByGuidClients(guid)
        del += userAccountDao.deleteByGuidClientLocations(guid)
        del += userAccountDao.deleteByGuidOrderContent(guid)
        del += userAccountDao.deleteByGuidOrders(guid)
        del += userAccountDao.deleteByGuidCash(guid)
        del +=  userAccountDao.deleteByGuid(guid)
        Log.w("UserAccountRepository", "deleted account: $guid; data elements: $del")
        return del
    }

    override suspend fun getCurrent(): UserAccount? {
        return userAccountDao.getCurrent()
    }

    override suspend fun hasAccounts(): Boolean {
        return userAccountDao.numberOfAccounts() > 0
    }

}
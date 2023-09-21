package ua.com.programmer.agentventa.dao.cloud

import ua.com.programmer.agentventa.BuildConfig
import ua.com.programmer.agentventa.dao.entity.UserAccount
import ua.com.programmer.agentventa.extensions.localFormatted
import java.util.Date

data class CUserAccount (
    val guid: String = "",
    val description: String = "",
    val license: String = "",
    val dataFormat: String = "",
    val dbServer: String = "",
    val dbName: String = "",
    val dbUser: String = "",
    val dbPassword: String = "",
    val token: String = "",
    val options: String = "",
    val loginTime: Long = 0,
    val loginDate: String = "",
    val appVersion: String = "",
    val appFlavor: String = ""
){
    companion object Builder {
        fun build(account: UserAccount?): CUserAccount {
            return if (account == null) {
                CUserAccount()
            } else {
                val time = System.currentTimeMillis()
                CUserAccount(
                    guid = account.guid,
                    description = account.description,
                    license = account.license,
                    dataFormat = account.dataFormat,
                    dbServer = account.dbServer,
                    dbName = account.dbName,
                    dbUser = account.dbUser,
                    dbPassword = account.dbPassword,
                    token = account.token,
                    options = account.options,
                    loginTime = time,
                    loginDate = Date(time).localFormatted(),
                    appVersion = BuildConfig.VERSION_CODE.toString(),
                    appFlavor = BuildConfig.FLAVOR
                )
            }
        }
    }
}
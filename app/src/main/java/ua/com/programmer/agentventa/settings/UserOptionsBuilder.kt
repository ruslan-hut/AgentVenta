package ua.com.programmer.agentventa.settings

import ua.com.programmer.agentventa.dao.entity.UserAccount
import ua.com.programmer.agentventa.utility.XMap

class UserOptionsBuilder {

    private fun fromString(options: String, id: String = ""): UserOptions {
        if (options.isEmpty()) return UserOptions(isEmpty = true)
        val optionsMap = XMap(options)
        if (optionsMap.isEmpty()) return UserOptions(isEmpty = true)
        val userId = optionsMap.getString("userId").ifEmpty { id }
        return UserOptions(
            isEmpty = false,
            userId = userId,
            allowPriceTypeChoose = optionsMap.getBoolean("allowPriceTypeChoose"),
            requireDeliveryDate = optionsMap.getBoolean("requireDeliveryDate"),
            locations = optionsMap.getBoolean("locations"),
            lastLocationTime = optionsMap.getLong("lastLocationTime"),
            sendPushToken = optionsMap.getBoolean("sendPushToken"),
            loadImages = optionsMap.getBoolean("loadImages"),
            watchList = optionsMap.getString("watchList"),
            checkOrderLocation = optionsMap.getBoolean("checkOrderLocation"),
            read = optionsMap.getBoolean("read"),
            token = optionsMap.getString("token"),
            write = optionsMap.getBoolean("write"),
            license = optionsMap.getString("license"),
            editLocations = optionsMap.getBoolean("editLocations"),
            currency = optionsMap.getString("currency"),
            printingEnabled = optionsMap.getBoolean("printingEnabled"),
            showClientPriceOnly = optionsMap.getBoolean("showClientPriceOnly"),
            setClientPrice = optionsMap.getBoolean("setClientPrice"),
            differentialUpdates = optionsMap.getBoolean("differentialUpdates"),
            clientsLocations = optionsMap.getBoolean("clientsLocations"),
            clientsDirections = optionsMap.getBoolean("clientsDirections"),
            clientsProducts = optionsMap.getBoolean("clientsProducts"),
            defaultClient = optionsMap.getString("defaultClient"),
            useDemands = optionsMap.getBoolean("useDemands"),
            usePackageMark = optionsMap.getBoolean("usePackageMark"),
            fiscalNumber = optionsMap.getString("fiscalNumber"),
            fiscalCashier = optionsMap.getString("fiscalCashier"),
            fiscalDeviceId = optionsMap.getString("fiscalDeviceId"),
            fiscalProvider = optionsMap.getString("fiscalProvider"),
        )
    }

    fun fromAccount(account: UserAccount?): UserOptions {
        return fromString(account?.options ?: "", account?.guid ?: "")
    }

    companion object {
        fun build(account: UserAccount?): UserOptions {
            return UserOptionsBuilder().fromAccount(account)
        }
    }
}
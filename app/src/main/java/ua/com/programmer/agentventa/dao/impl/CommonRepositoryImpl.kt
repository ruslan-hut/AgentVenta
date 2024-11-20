package ua.com.programmer.agentventa.dao.impl

import ua.com.programmer.agentventa.dao.CommonDao
import ua.com.programmer.agentventa.logger.Logger
import ua.com.programmer.agentventa.repository.CommonRepository
import javax.inject.Inject

class CommonRepositoryImpl @Inject constructor(
    private val commonDao: CommonDao,
    private val logger: Logger
) : CommonRepository {

    val tag = "CommonRepo"

    override suspend fun cleanup(from: Long): Int {
        //logger.d(tag, "deleteOldData: from = $from")

        // content deletion must be called first
        val orderContent = commonDao.deleteOrderContent(from)

        val order = commonDao.deleteOrders(from)
        if (order > 0) {
            logger.d(tag, "cleanup: orders deleted: $order")
        }

        val cash = commonDao.deleteCash(from)
        if (cash > 0) {
            logger.d(tag, "cleanup: cash deleted: $cash")
        }

        val tasks = commonDao.deleteTasks(from)
        if (tasks > 0) {
            logger.d(tag, "cleanup: tasks deleted: $tasks")
        }

        val locations = commonDao.deleteLocations(from)
        if (locations > 0) {
            logger.d(tag, "cleanup: locations deleted: $locations")
        }

        return order + orderContent + cash + tasks + locations
    }
}

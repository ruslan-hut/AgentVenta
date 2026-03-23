package ua.com.programmer.agentventa.domain.usecase.order

import kotlinx.coroutines.CoroutineDispatcher
import ua.com.programmer.agentventa.data.local.dao.DiscountDao
import ua.com.programmer.agentventa.di.CoroutineModule.IoDispatcher
import ua.com.programmer.agentventa.domain.result.Result
import ua.com.programmer.agentventa.domain.usecase.SuspendUseCase
import javax.inject.Inject

class GetProductDiscountUseCase @Inject constructor(
    private val discountDao: DiscountDao,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : SuspendUseCase<GetProductDiscountUseCase.Params, Double>(dispatcher) {

    data class Params(
        val dbGuid: String,
        val clientGuid: String,
        val productGuid: String,
        val groupGuid: String = "",
    )

    override suspend fun execute(params: Params): Result<Double> {
        val groupGuid = params.groupGuid.ifEmpty {
            discountDao.getProductGroupGuid(params.dbGuid, params.productGuid) ?: ""
        }
        val discount = discountDao.getDiscount(
            dbGuid = params.dbGuid,
            clientGuid = params.clientGuid,
            productGuid = params.productGuid,
            groupGuid = groupGuid
        ) ?: 0.0
        return Result.Success(discount)
    }
}

package ua.com.programmer.agentventa.domain.usecase.order

import kotlinx.coroutines.CoroutineDispatcher
import ua.com.programmer.agentventa.data.local.dao.DiscountDao
import ua.com.programmer.agentventa.di.CoroutineModule.IoDispatcher
import ua.com.programmer.agentventa.domain.result.Result
import ua.com.programmer.agentventa.domain.usecase.SuspendUseCase
import ua.com.programmer.agentventa.extensions.DiscountResolver
import javax.inject.Inject

class GetProductDiscountUseCase @Inject constructor(
    private val discountDao: DiscountDao,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : SuspendUseCase<GetProductDiscountUseCase.Params, Double>(dispatcher) {

    data class Params(
        val dbGuid: String,
        val clientGuid: String,
        val productGuid: String,
    )

    override suspend fun execute(params: Params): Result<Double> {
        val discount = DiscountResolver.resolve(
            discountDao = discountDao,
            dbGuid = params.dbGuid,
            clientGuid = params.clientGuid,
            productGuid = params.productGuid,
        )
        return Result.Success(discount)
    }
}

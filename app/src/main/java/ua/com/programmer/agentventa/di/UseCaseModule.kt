package ua.com.programmer.agentventa.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import ua.com.programmer.agentventa.domain.usecase.order.CopyOrderUseCase
import ua.com.programmer.agentventa.domain.usecase.order.CreateOrderUseCase
import ua.com.programmer.agentventa.domain.usecase.order.DeleteOrderUseCase
import ua.com.programmer.agentventa.domain.usecase.order.EnableOrderEditUseCase
import ua.com.programmer.agentventa.domain.usecase.order.GetOrderUseCase
import ua.com.programmer.agentventa.domain.usecase.order.GetOrderWithContentUseCase
import ua.com.programmer.agentventa.domain.usecase.order.GetOrdersUseCase
import ua.com.programmer.agentventa.domain.usecase.order.SaveOrderUseCase
import ua.com.programmer.agentventa.domain.usecase.order.ValidateOrderUseCase
import ua.com.programmer.agentventa.repository.OrderRepository

/**
 * Hilt module providing use cases.
 * Scoped to ViewModelComponent for ViewModel lifecycle.
 */
@Module
@InstallIn(ViewModelComponent::class)
object UseCaseModule {

    @Provides
    @ViewModelScoped
    fun provideGetOrdersUseCase(
        orderRepository: OrderRepository
    ): GetOrdersUseCase = GetOrdersUseCase(orderRepository)

    @Provides
    @ViewModelScoped
    fun provideGetOrderUseCase(
        orderRepository: OrderRepository
    ): GetOrderUseCase = GetOrderUseCase(orderRepository)

    @Provides
    @ViewModelScoped
    fun provideGetOrderWithContentUseCase(
        orderRepository: OrderRepository
    ): GetOrderWithContentUseCase = GetOrderWithContentUseCase(orderRepository)

    @Provides
    @ViewModelScoped
    fun provideValidateOrderUseCase(): ValidateOrderUseCase = ValidateOrderUseCase()

    @Provides
    @ViewModelScoped
    fun provideSaveOrderUseCase(
        orderRepository: OrderRepository,
        validateOrderUseCase: ValidateOrderUseCase
    ): SaveOrderUseCase = SaveOrderUseCase(orderRepository, validateOrderUseCase)

    @Provides
    @ViewModelScoped
    fun provideCreateOrderUseCase(
        orderRepository: OrderRepository
    ): CreateOrderUseCase = CreateOrderUseCase(orderRepository)

    @Provides
    @ViewModelScoped
    fun provideDeleteOrderUseCase(
        orderRepository: OrderRepository
    ): DeleteOrderUseCase = DeleteOrderUseCase(orderRepository)

    @Provides
    @ViewModelScoped
    fun provideEnableOrderEditUseCase(
        orderRepository: OrderRepository
    ): EnableOrderEditUseCase = EnableOrderEditUseCase(orderRepository)

    @Provides
    @ViewModelScoped
    fun provideCopyOrderUseCase(
        orderRepository: OrderRepository
    ): CopyOrderUseCase = CopyOrderUseCase(orderRepository)
}

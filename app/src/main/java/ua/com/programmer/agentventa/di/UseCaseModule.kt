package ua.com.programmer.agentventa.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import ua.com.programmer.agentventa.domain.usecase.cash.CreateCashUseCase
import ua.com.programmer.agentventa.domain.usecase.cash.DeleteCashUseCase
import ua.com.programmer.agentventa.domain.usecase.cash.EnableCashEditUseCase
import ua.com.programmer.agentventa.domain.usecase.cash.SaveCashUseCase
import ua.com.programmer.agentventa.domain.usecase.cash.ValidateCashUseCase
import ua.com.programmer.agentventa.domain.usecase.order.CopyOrderUseCase
import ua.com.programmer.agentventa.domain.usecase.order.CreateOrderUseCase
import ua.com.programmer.agentventa.domain.usecase.order.DeleteOrderUseCase
import ua.com.programmer.agentventa.domain.usecase.order.EnableOrderEditUseCase
import ua.com.programmer.agentventa.domain.usecase.order.GetOrderUseCase
import ua.com.programmer.agentventa.domain.usecase.order.GetOrderWithContentUseCase
import ua.com.programmer.agentventa.domain.usecase.order.GetOrdersUseCase
import ua.com.programmer.agentventa.domain.usecase.order.SaveOrderUseCase
import ua.com.programmer.agentventa.domain.usecase.order.ValidateOrderUseCase
import ua.com.programmer.agentventa.domain.usecase.task.CreateTaskUseCase
import ua.com.programmer.agentventa.domain.usecase.task.DeleteTaskUseCase
import ua.com.programmer.agentventa.domain.usecase.task.MarkTaskDoneUseCase
import ua.com.programmer.agentventa.domain.usecase.task.SaveTaskUseCase
import ua.com.programmer.agentventa.domain.usecase.task.ValidateTaskUseCase
import ua.com.programmer.agentventa.domain.repository.CashRepository
import ua.com.programmer.agentventa.domain.repository.OrderRepository
import ua.com.programmer.agentventa.domain.repository.TaskRepository

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

    // Cash use cases

    @Provides
    @ViewModelScoped
    fun provideValidateCashUseCase(): ValidateCashUseCase = ValidateCashUseCase()

    @Provides
    @ViewModelScoped
    fun provideSaveCashUseCase(
        cashRepository: CashRepository,
        validateCashUseCase: ValidateCashUseCase
    ): SaveCashUseCase = SaveCashUseCase(cashRepository, validateCashUseCase)

    @Provides
    @ViewModelScoped
    fun provideCreateCashUseCase(
        cashRepository: CashRepository
    ): CreateCashUseCase = CreateCashUseCase(cashRepository)

    @Provides
    @ViewModelScoped
    fun provideDeleteCashUseCase(
        cashRepository: CashRepository
    ): DeleteCashUseCase = DeleteCashUseCase(cashRepository)

    @Provides
    @ViewModelScoped
    fun provideEnableCashEditUseCase(
        cashRepository: CashRepository
    ): EnableCashEditUseCase = EnableCashEditUseCase(cashRepository)

    // Task use cases

    @Provides
    @ViewModelScoped
    fun provideValidateTaskUseCase(): ValidateTaskUseCase = ValidateTaskUseCase()

    @Provides
    @ViewModelScoped
    fun provideSaveTaskUseCase(
        taskRepository: TaskRepository,
        validateTaskUseCase: ValidateTaskUseCase
    ): SaveTaskUseCase = SaveTaskUseCase(taskRepository, validateTaskUseCase)

    @Provides
    @ViewModelScoped
    fun provideCreateTaskUseCase(
        taskRepository: TaskRepository
    ): CreateTaskUseCase = CreateTaskUseCase(taskRepository)

    @Provides
    @ViewModelScoped
    fun provideDeleteTaskUseCase(
        taskRepository: TaskRepository
    ): DeleteTaskUseCase = DeleteTaskUseCase(taskRepository)

    @Provides
    @ViewModelScoped
    fun provideMarkTaskDoneUseCase(
        taskRepository: TaskRepository
    ): MarkTaskDoneUseCase = MarkTaskDoneUseCase(taskRepository)
}

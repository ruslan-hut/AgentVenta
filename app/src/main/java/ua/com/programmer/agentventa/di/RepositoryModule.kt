package ua.com.programmer.agentventa.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import ua.com.programmer.agentventa.data.local.dao.CashDao
import ua.com.programmer.agentventa.data.local.dao.LocationDao
import ua.com.programmer.agentventa.data.local.dao.OrderDao
import ua.com.programmer.agentventa.data.local.dao.TaskDao
import ua.com.programmer.agentventa.data.local.dao.UserAccountDao
import ua.com.programmer.agentventa.data.local.entity.Cash
import ua.com.programmer.agentventa.data.local.entity.Order
import ua.com.programmer.agentventa.data.local.entity.Task
import ua.com.programmer.agentventa.data.repository.CashRepositoryImpl
import ua.com.programmer.agentventa.data.repository.OrderRepositoryImpl
import ua.com.programmer.agentventa.data.repository.TaskRepositoryImpl
import ua.com.programmer.agentventa.domain.repository.DocumentRepository
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import ua.com.programmer.agentventa.utility.UtilsInterface

@Module
@InstallIn(ViewModelComponent::class)
object RepositoryModule {
    @Provides
    fun provideOrderRepository(
        dao: OrderDao,
        accountDao: UserAccountDao,
        userAccountRepository: UserAccountRepository,
        locationDao: LocationDao,
        utils: UtilsInterface
    ): DocumentRepository<Order> {
        return OrderRepositoryImpl(dao, accountDao, userAccountRepository, locationDao, utils)
    }
    @Provides
    fun provideCashRepository(
        dao: CashDao,
        accountDao: UserAccountDao,
        userAccountRepository: UserAccountRepository,
        utils: UtilsInterface
    ): DocumentRepository<Cash> {
        return CashRepositoryImpl(dao, accountDao, userAccountRepository, utils)
    }
    @Provides
    fun provideTaskRepository(
        dao: TaskDao,
        accountDao: UserAccountDao,
        userAccountRepository: UserAccountRepository,
        utils: UtilsInterface
    ): DocumentRepository<Task> {
        return TaskRepositoryImpl(dao, accountDao, userAccountRepository, utils)
    }
}
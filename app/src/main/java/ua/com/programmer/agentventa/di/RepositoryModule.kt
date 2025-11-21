package ua.com.programmer.agentventa.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import ua.com.programmer.agentventa.dao.CashDao
import ua.com.programmer.agentventa.dao.LocationDao
import ua.com.programmer.agentventa.dao.OrderDao
import ua.com.programmer.agentventa.dao.TaskDao
import ua.com.programmer.agentventa.dao.UserAccountDao
import ua.com.programmer.agentventa.dao.entity.Cash
import ua.com.programmer.agentventa.dao.entity.Order
import ua.com.programmer.agentventa.dao.entity.Task
import ua.com.programmer.agentventa.dao.impl.CashRepositoryImpl
import ua.com.programmer.agentventa.dao.impl.OrderRepositoryImpl
import ua.com.programmer.agentventa.dao.impl.TaskRepositoryImpl
import ua.com.programmer.agentventa.repository.DocumentRepository
import ua.com.programmer.agentventa.repository.UserAccountRepository
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
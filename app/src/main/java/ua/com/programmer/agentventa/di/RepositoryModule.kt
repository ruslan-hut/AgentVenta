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

@Module
@InstallIn(ViewModelComponent::class)
object RepositoryModule {
    @Provides
    fun provideOrderRepository(dao: OrderDao, accountDao: UserAccountDao, locationDao: LocationDao): DocumentRepository<Order> {
        return OrderRepositoryImpl(dao, accountDao, locationDao)
    }
    @Provides
    fun provideCashRepository(dao: CashDao, accountDao: UserAccountDao): DocumentRepository<Cash> {
        return CashRepositoryImpl(dao, accountDao)
    }
    @Provides
    fun provideTaskRepository(dao: TaskDao, accountDao: UserAccountDao): DocumentRepository<Task> {
        return TaskRepositoryImpl(dao, accountDao)
    }
}
package ua.com.programmer.agentventa.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.components.ViewModelComponent
import ua.com.programmer.agentventa.dao.AppDatabase
import ua.com.programmer.agentventa.dao.CashDao
import ua.com.programmer.agentventa.dao.ClientDao
import ua.com.programmer.agentventa.dao.CommonDao
import ua.com.programmer.agentventa.dao.CompanyDao
import ua.com.programmer.agentventa.dao.DataExchangeDao
import ua.com.programmer.agentventa.dao.LocationDao
import ua.com.programmer.agentventa.dao.LogDao
import ua.com.programmer.agentventa.dao.OrderDao
import ua.com.programmer.agentventa.dao.ProductDao
import ua.com.programmer.agentventa.dao.RestDao
import ua.com.programmer.agentventa.dao.StoreDao
import ua.com.programmer.agentventa.dao.TaskDao
import ua.com.programmer.agentventa.dao.UserAccountDao
import ua.com.programmer.agentventa.dao.impl.CashRepositoryImpl
import ua.com.programmer.agentventa.dao.impl.ClientRepositoryImpl
import ua.com.programmer.agentventa.dao.impl.CommonRepositoryImpl
import ua.com.programmer.agentventa.dao.impl.DataExchangeRepositoryImpl
import ua.com.programmer.agentventa.dao.impl.FilesRepositoryImpl
import ua.com.programmer.agentventa.dao.impl.LogRepositoryImpl
import ua.com.programmer.agentventa.dao.impl.OrderRepositoryImpl
import ua.com.programmer.agentventa.dao.impl.ProductRepositoryImpl
import ua.com.programmer.agentventa.dao.impl.TaskRepositoryImpl
import ua.com.programmer.agentventa.dao.impl.UserAccountRepositoryImpl
import ua.com.programmer.agentventa.geo.LocationRepositoryImpl
import ua.com.programmer.agentventa.http.NetworkRepositoryImpl
import ua.com.programmer.agentventa.repository.CashRepository
import ua.com.programmer.agentventa.repository.ClientRepository
import ua.com.programmer.agentventa.repository.CommonRepository
import ua.com.programmer.agentventa.repository.DataExchangeRepository
import ua.com.programmer.agentventa.repository.FilesRepository
import ua.com.programmer.agentventa.repository.LocationRepository
import ua.com.programmer.agentventa.repository.LogRepository
import ua.com.programmer.agentventa.repository.NetworkRepository
import ua.com.programmer.agentventa.repository.OrderRepository
import ua.com.programmer.agentventa.repository.ProductRepository
import ua.com.programmer.agentventa.repository.TaskRepository
import ua.com.programmer.agentventa.repository.UserAccountRepository

@Module
@InstallIn(ViewModelComponent::class, ServiceComponent::class)
class DomainModule {
    @Provides
    fun provideOrderDao(database: AppDatabase): OrderDao {
        return database.orderDao()
    }
    @Provides
    fun provideLocationsDao(database: AppDatabase): LocationDao {
        return database.locationDao()
    }
    @Provides
    fun provideAccountDao(database: AppDatabase): UserAccountDao {
        return database.userAccountDao()
    }
    @Provides
    fun provideProductDao(database: AppDatabase): ProductDao {
        return database.productDao()
    }
    @Provides
    fun provideClientDao(database: AppDatabase): ClientDao {
        return database.clientDao()
    }
    @Provides
    fun provideLogDao(database: AppDatabase): LogDao {
        return database.logDao()
    }
    @Provides
    fun provideDataExchangeDao(database: AppDatabase): DataExchangeDao {
        return database.dataExchangeDao()
    }
    @Provides
    fun provideCashDao(database: AppDatabase): CashDao {
        return database.cashDao()
    }
    @Provides
    fun provideTaskDao(database: AppDatabase): TaskDao {
        return database.taskDao()
    }
    @Provides
    fun provideCommonDao(database: AppDatabase): CommonDao {
        return database.commonDao()
    }
    @Provides
    fun provideCompanyDao(database: AppDatabase): CompanyDao {
        return database.companyDao()
    }
    @Provides
    fun provideStoreDao(database: AppDatabase): StoreDao {
        return database.storeDao()
    }
    @Provides
    fun provideRestDao(database: AppDatabase): RestDao {
        return database.restDao()
    }
}

@Module
@InstallIn(ViewModelComponent::class, ServiceComponent::class)
abstract class RepositoryBindModule {

    @Binds
    abstract fun bindOrderRepository(repositoryImpl: OrderRepositoryImpl): OrderRepository

    @Binds
    abstract fun bindUserRepository(repositoryImpl: UserAccountRepositoryImpl): UserAccountRepository

    @Binds
    abstract fun bindProductRepository(repositoryImpl: ProductRepositoryImpl): ProductRepository

    @Binds
    abstract fun bindClientRepository(repositoryImpl: ClientRepositoryImpl): ClientRepository

    @Binds
    abstract fun bindNetworkRepository(repositoryImpl: NetworkRepositoryImpl): NetworkRepository

    @Binds
    abstract fun bindLogRepository(repositoryImpl: LogRepositoryImpl): LogRepository

    @Binds
    abstract fun bindDataExchangeRepository(repositoryImpl: DataExchangeRepositoryImpl): DataExchangeRepository

    @Binds
    abstract fun bindCashRepository(repositoryImpl: CashRepositoryImpl): CashRepository

    @Binds
    abstract fun bindCommonRepository(repositoryImpl: CommonRepositoryImpl): CommonRepository

    @Binds
    abstract fun bindTaskRepository(repositoryImpl: TaskRepositoryImpl): TaskRepository

    @Binds
    abstract fun bindLocationRepository(repositoryImpl: LocationRepositoryImpl): LocationRepository

    @Binds
    abstract fun bindFilesRepository(repositoryImpl: FilesRepositoryImpl): FilesRepository
}
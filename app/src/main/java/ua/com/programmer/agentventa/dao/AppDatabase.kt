package ua.com.programmer.agentventa.dao

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ua.com.programmer.agentventa.dao.entity.Cash
import ua.com.programmer.agentventa.dao.entity.Client
import ua.com.programmer.agentventa.dao.entity.ClientImage
import ua.com.programmer.agentventa.dao.entity.ClientLocation
import ua.com.programmer.agentventa.dao.entity.Debt
import ua.com.programmer.agentventa.dao.entity.LocationHistory
import ua.com.programmer.agentventa.dao.entity.LogEvent
import ua.com.programmer.agentventa.dao.entity.Order
import ua.com.programmer.agentventa.dao.entity.OrderContent
import ua.com.programmer.agentventa.dao.entity.PaymentType
import ua.com.programmer.agentventa.dao.entity.PriceType
import ua.com.programmer.agentventa.dao.entity.Product
import ua.com.programmer.agentventa.dao.entity.ProductImage
import ua.com.programmer.agentventa.dao.entity.ProductPrice
import ua.com.programmer.agentventa.dao.entity.Task
import ua.com.programmer.agentventa.dao.entity.UserAccount

@Database(entities = [
    UserAccount::class,
    Order::class,
    OrderContent::class,
    Product::class,
    ProductPrice::class,
    ProductImage::class,
    PriceType::class,
    PaymentType::class,
    Client::class,
    Debt::class,
    ClientLocation::class,
    ClientImage::class,
    LocationHistory::class,
    Task::class,
    Cash::class,
    LogEvent::class
                     ], version = 17)
abstract class AppDatabase: RoomDatabase() {

    abstract fun orderDao(): OrderDao
    abstract fun productDao(): ProductDao
    abstract fun clientDao(): ClientDao
    abstract fun locationDao(): LocationDao
    abstract fun userAccountDao(): UserAccountDao
    abstract fun logDao(): LogDao
    abstract fun dataExchangeDao(): DataExchangeDao
    abstract fun taskDao(): TaskDao
    abstract fun cashDao(): CashDao
    abstract fun commonDao(): CommonDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE debts ADD COLUMN content TEXT NOT NULL DEFAULT ''")
            }
        }
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE client_locations ADD COLUMN address TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE client_locations ADD COLUMN is_modified INTEGER NOT NULL DEFAULT 0")
            }
        }
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE client_images (" +
                        "db_guid TEXT NOT NULL DEFAULT ''," +
                        "client_guid TEXT NOT NULL DEFAULT ''," +
                        "guid TEXT NOT NULL DEFAULT ''," +
                        "url TEXT NOT NULL DEFAULT ''," +
                        "description TEXT NOT NULL DEFAULT ''," +
                        "timestamp INTEGER NOT NULL DEFAULT 0," +
                        "is_local INTEGER NOT NULL DEFAULT 0," +
                        "is_sent INTEGER NOT NULL DEFAULT 0," +
                        "is_default INTEGER NOT NULL DEFAULT 0," +
                        "PRIMARY KEY(db_guid,client_guid,guid))")
            }
        }
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE payment_types (" +
                        "db_guid TEXT NOT NULL DEFAULT ''," +
                        "payment_type TEXT NOT NULL DEFAULT ''," +
                        "description TEXT NOT NULL DEFAULT ''," +
                        "timestamp INTEGER NOT NULL DEFAULT 0," +
                        "is_fiscal INTEGER NOT NULL DEFAULT 0," +
                        "is_default INTEGER NOT NULL DEFAULT 0," +
                        "PRIMARY KEY(db_guid,payment_type))")
                db.execSQL("ALTER TABLE orders ADD COLUMN is_fiscal INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "agent_database")
                    .addMigrations(
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16,
                        MIGRATION_16_17
                    )
                    .build()
                INSTANCE = instance
                return instance
            }
        }

    }
}
package ua.com.programmer.agentventa.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ua.com.programmer.agentventa.data.local.entity.Cash
import ua.com.programmer.agentventa.data.local.entity.Client
import ua.com.programmer.agentventa.data.local.entity.ClientImage
import ua.com.programmer.agentventa.data.local.entity.ClientLocation
import ua.com.programmer.agentventa.data.local.entity.Company
import ua.com.programmer.agentventa.data.local.entity.Debt
import ua.com.programmer.agentventa.data.local.entity.LocationHistory
import ua.com.programmer.agentventa.data.local.entity.LogEvent
import ua.com.programmer.agentventa.data.local.entity.Order
import ua.com.programmer.agentventa.data.local.entity.OrderContent
import ua.com.programmer.agentventa.data.local.entity.PaymentType
import ua.com.programmer.agentventa.data.local.entity.PriceType
import ua.com.programmer.agentventa.data.local.entity.Product
import ua.com.programmer.agentventa.data.local.entity.ProductImage
import ua.com.programmer.agentventa.data.local.entity.ProductPrice
import ua.com.programmer.agentventa.data.local.entity.Rest
import ua.com.programmer.agentventa.data.local.entity.Store
import ua.com.programmer.agentventa.data.local.entity.Task
import ua.com.programmer.agentventa.data.local.entity.UserAccount
import ua.com.programmer.agentventa.data.local.dao.CashDao
import ua.com.programmer.agentventa.data.local.dao.ClientDao
import ua.com.programmer.agentventa.data.local.dao.CommonDao
import ua.com.programmer.agentventa.data.local.dao.CompanyDao
import ua.com.programmer.agentventa.data.local.dao.DataExchangeDao
import ua.com.programmer.agentventa.data.local.dao.LocationDao
import ua.com.programmer.agentventa.data.local.dao.LogDao
import ua.com.programmer.agentventa.data.local.dao.OrderDao
import ua.com.programmer.agentventa.data.local.dao.ProductDao
import ua.com.programmer.agentventa.data.local.dao.RestDao
import ua.com.programmer.agentventa.data.local.dao.StoreDao
import ua.com.programmer.agentventa.data.local.dao.TaskDao
import ua.com.programmer.agentventa.data.local.dao.UserAccountDao

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
    LogEvent::class,
    Rest::class,
    Company::class,
    Store::class,
                     ], version = 21)
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
    abstract fun companyDao(): CompanyDao
    abstract fun storeDao(): StoreDao
    abstract fun restDao(): RestDao

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
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE companies (" +
                        "db_guid TEXT NOT NULL DEFAULT ''," +
                        "guid TEXT NOT NULL DEFAULT ''," +
                        "description TEXT NOT NULL DEFAULT ''," +
                        "is_default INTEGER NOT NULL DEFAULT 0," +
                        "timestamp INTEGER NOT NULL DEFAULT 0," +
                        "PRIMARY KEY(db_guid,guid))")

                db.execSQL("CREATE TABLE stores (" +
                        "db_guid TEXT NOT NULL DEFAULT ''," +
                        "guid TEXT NOT NULL DEFAULT ''," +
                        "description TEXT NOT NULL DEFAULT ''," +
                        "is_default INTEGER NOT NULL DEFAULT 0," +
                        "timestamp INTEGER NOT NULL DEFAULT 0," +
                        "PRIMARY KEY(db_guid,guid))")

                db.execSQL("CREATE TABLE rests (" +
                        "db_guid TEXT NOT NULL DEFAULT ''," +
                        "company_guid TEXT NOT NULL DEFAULT ''," +
                        "store_guid TEXT NOT NULL DEFAULT ''," +
                        "product_guid TEXT NOT NULL DEFAULT ''," +
                        "quantity REAL NOT NULL DEFAULT 0.0," +
                        "timestamp INTEGER NOT NULL DEFAULT 0," +
                        "PRIMARY KEY(db_guid,company_guid,store_guid,product_guid))")

                db.execSQL("ALTER TABLE orders ADD COLUMN company_guid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE orders ADD COLUMN company TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE orders ADD COLUMN store_guid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE orders ADD COLUMN store TEXT NOT NULL DEFAULT ''")

                db.execSQL("ALTER TABLE cash ADD COLUMN company_guid TEXT NOT NULL DEFAULT ''")

                db.execSQL("DROP TABLE debts")
                db.execSQL("CREATE TABLE debts ("+
                    "db_guid TEXT NOT NULL DEFAULT '',"+
                    "company_guid TEXT NOT NULL DEFAULT '',"+
                    "client_guid TEXT NOT NULL DEFAULT '',"+
                    "doc_guid TEXT NOT NULL DEFAULT '',"+
                    "doc_id TEXT NOT NULL DEFAULT '',"+
                    "doc_type TEXT NOT NULL DEFAULT '',"+
                    "has_content INTEGER NOT NULL DEFAULT 0,"+
                    "content TEXT NOT NULL DEFAULT '',"+
                    "sum REAL NOT NULL DEFAULT 0.0,"+
                    "sum_in REAL NOT NULL DEFAULT 0.0,"+
                    "sum_out REAL NOT NULL DEFAULT 0.0,"+
                    "is_total INTEGER NOT NULL DEFAULT 0,"+
                    "sorting INTEGER NOT NULL DEFAULT 0,"+
                    "timestamp INTEGER NOT NULL DEFAULT 0,"+
                    "PRIMARY KEY(db_guid, company_guid, client_guid, doc_id))")
            }
        }
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cash ADD COLUMN company TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE cash ADD COLUMN client TEXT NOT NULL DEFAULT ''")
            }
        }
        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cash ADD COLUMN status TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add indexes for Order table
                db.execSQL("CREATE INDEX IF NOT EXISTS index_orders_db_guid ON orders(db_guid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_orders_db_guid_time ON orders(db_guid, time)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_orders_db_guid_client_guid ON orders(db_guid, client_guid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_orders_db_guid_is_sent ON orders(db_guid, is_sent)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_orders_client_guid_time ON orders(client_guid, time)")

                // Add indexes for Client table
                db.execSQL("CREATE INDEX IF NOT EXISTS index_clients_db_guid ON clients(db_guid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_clients_db_guid_group_guid ON clients(db_guid, group_guid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_clients_db_guid_is_group ON clients(db_guid, is_group)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_clients_db_guid_description_lc ON clients(db_guid, description_lc)")

                // Add indexes for Product table
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_db_guid ON products(db_guid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_db_guid_group_guid ON products(db_guid, group_guid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_db_guid_is_group ON products(db_guid, is_group)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_db_guid_barcode ON products(db_guid, barcode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_db_guid_description_lc ON products(db_guid, description_lc)")

                // Add indexes for OrderContent table
                db.execSQL("CREATE INDEX IF NOT EXISTS index_order_content_order_guid ON order_content(order_guid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_order_content_order_guid_product_guid ON order_content(order_guid, product_guid)")
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
                        MIGRATION_16_17,
                        MIGRATION_17_18,
                        MIGRATION_18_19,
                        MIGRATION_19_20,
                        MIGRATION_20_21,
                    )
                    .build()
                INSTANCE = instance
                return instance
            }
        }

    }
}
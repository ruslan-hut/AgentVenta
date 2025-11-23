package ua.com.programmer.agentventa.fixtures

import ua.com.programmer.agentventa.data.local.entity.*
import java.util.*

/**
 * Centralized test fixtures for ViewModel testing.
 * Provides sample data with known values and consistent relationships.
 */
object TestFixtures {

    // ========== Constants ==========

    const val TEST_DB_GUID = "test-db-guid"
    const val TEST_ACCOUNT_GUID = "test-account-123"

    // Entity GUIDs
    const val CLIENT_1_GUID = "client-guid-1"
    const val CLIENT_2_GUID = "client-guid-2"
    const val CLIENT_3_GUID = "client-guid-3"

    const val PRODUCT_1_GUID = "product-guid-1"
    const val PRODUCT_2_GUID = "product-guid-2"
    const val PRODUCT_3_GUID = "product-guid-3"

    const val ORDER_1_GUID = "order-guid-1"
    const val ORDER_2_GUID = "order-guid-2"
    const val ORDER_3_GUID = "order-guid-3"

    const val CASH_1_GUID = "cash-guid-1"
    const val CASH_2_GUID = "cash-guid-2"

    const val TASK_1_GUID = "task-guid-1"
    const val TASK_2_GUID = "task-guid-2"

    const val COMPANY_1_GUID = "company-guid-1"
    const val COMPANY_2_GUID = "company-guid-2"

    const val STORE_1_GUID = "store-guid-1"
    const val STORE_2_GUID = "store-guid-2"

    const val PRICE_TYPE_RETAIL = "price-retail"
    const val PRICE_TYPE_WHOLESALE = "price-wholesale"

    const val PAYMENT_TYPE_CASH = "payment-cash"
    const val PAYMENT_TYPE_CARD = "payment-card"

    // ========== User Accounts ==========

    fun createTestAccount(
        guid: String = TEST_ACCOUNT_GUID,
        isCurrent: Int = 1,
        description: String = "Test Account",
        license: String = "test-license-key",
        token: String = "test-token-123"
    ) = UserAccount(
        guid = guid,
        isCurrent = isCurrent,
        description = description,
        license = license,
        dataFormat = "json",
        dbServer = "test-server.com",
        dbName = "test_database",
        dbUser = "test_user",
        dbPassword = "test_password",
        token = token,
        options = "{}"
    )

    fun createDemoAccount() = UserAccount(
        guid = "demo-account-guid",
        isCurrent = 0,
        description = "Demo Account",
        license = "demo-license",
        dataFormat = "json",
        dbServer = "demo-server.com",
        dbName = "demo_db",
        dbUser = "demo_user",
        dbPassword = "demo_pass",
        token = "demo-token",
        options = """{"enableLocations":true,"enableImages":true}"""
    )

    // ========== Companies ==========

    fun createCompany1() = Company(
        databaseId = TEST_DB_GUID,
        guid = COMPANY_1_GUID,
        description = "Main Company LLC",
        isDefault = 1,
        timestamp = System.currentTimeMillis()
    )

    fun createCompany2() = Company(
        databaseId = TEST_DB_GUID,
        guid = COMPANY_2_GUID,
        description = "Branch Company Ltd",
        isDefault = 0,
        timestamp = System.currentTimeMillis()
    )

    // ========== Stores ==========

    fun createStore1() = Store(
        databaseId = TEST_DB_GUID,
        guid = STORE_1_GUID,
        description = "Warehouse #1",
        isDefault = 1,
        timestamp = System.currentTimeMillis()
    )

    fun createStore2() = Store(
        databaseId = TEST_DB_GUID,
        guid = STORE_2_GUID,
        description = "Warehouse #2",
        isDefault = 0,
        timestamp = System.currentTimeMillis()
    )

    // ========== Price Types ==========

    fun createPriceTypeRetail() = PriceType(
        databaseId = TEST_DB_GUID,
        priceType = PRICE_TYPE_RETAIL,
        description = "Retail Price",
        timestamp = System.currentTimeMillis()
    )

    fun createPriceTypeWholesale() = PriceType(
        databaseId = TEST_DB_GUID,
        priceType = PRICE_TYPE_WHOLESALE,
        description = "Wholesale Price",
        timestamp = System.currentTimeMillis()
    )

    fun allPriceTypes() = listOf(
        createPriceTypeRetail(),
        createPriceTypeWholesale()
    )

    // ========== Payment Types ==========

    fun createPaymentTypeCash() = PaymentType(
        databaseId = TEST_DB_GUID,
        paymentType = PAYMENT_TYPE_CASH,
        isFiscal = 1,
        isDefault = 1,
        description = "Cash",
        timestamp = System.currentTimeMillis()
    )

    fun createPaymentTypeCard() = PaymentType(
        databaseId = TEST_DB_GUID,
        paymentType = PAYMENT_TYPE_CARD,
        isFiscal = 1,
        isDefault = 0,
        description = "Credit Card",
        timestamp = System.currentTimeMillis()
    )

    fun allPaymentTypes() = listOf(
        createPaymentTypeCash(),
        createPaymentTypeCard()
    )

    // ========== Clients ==========

    fun createClient1() = Client(
        databaseId = TEST_DB_GUID,
        guid = CLIENT_1_GUID,
        timestamp = System.currentTimeMillis(),
        code1 = "C001",
        code2 = "CLIENT001",
        description = "ABC Retail Store",
        descriptionLc = "abc retail store",
        notes = "Premium client",
        phone = "+380501234567",
        address = "123 Main Street, Kyiv",
        discount = 5.0,
        bonus = 100.0,
        priceType = PRICE_TYPE_RETAIL,
        isBanned = 0,
        groupGuid = "",
        isActive = 1,
        isGroup = 0
    )

    fun createClient2() = Client(
        databaseId = TEST_DB_GUID,
        guid = CLIENT_2_GUID,
        timestamp = System.currentTimeMillis(),
        code1 = "C002",
        code2 = "CLIENT002",
        description = "XYZ Wholesale Co",
        descriptionLc = "xyz wholesale co",
        notes = "Wholesale client",
        phone = "+380502345678",
        address = "456 Commerce Ave, Lviv",
        discount = 10.0,
        bonus = 500.0,
        priceType = PRICE_TYPE_WHOLESALE,
        isBanned = 0,
        groupGuid = "",
        isActive = 1,
        isGroup = 0
    )

    fun createClient3Banned() = Client(
        databaseId = TEST_DB_GUID,
        guid = CLIENT_3_GUID,
        timestamp = System.currentTimeMillis(),
        code1 = "C003",
        code2 = "CLIENT003",
        description = "Banned Client Ltd",
        descriptionLc = "banned client ltd",
        notes = "",
        phone = "+380503456789",
        address = "789 Old Street, Odesa",
        discount = 0.0,
        bonus = 0.0,
        priceType = PRICE_TYPE_RETAIL,
        isBanned = 1,
        banMessage = "Payment overdue",
        groupGuid = "",
        isActive = 0,
        isGroup = 0
    )

    fun createLClient1() = LClient(
        guid = CLIENT_1_GUID,
        timestamp = System.currentTimeMillis(),
        code = "C001",
        description = "ABC Retail Store",
        notes = "Premium client",
        phone = "+380501234567",
        address = "123 Main Street, Kyiv",
        discount = 5.0,
        bonus = 100.0,
        debt = 1500.0,
        priceType = PRICE_TYPE_RETAIL,
        isBanned = false,
        groupGuid = "",
        isActive = true,
        isGroup = false,
        latitude = 50.4501,
        longitude = 30.5234
    )

    fun createLClient2() = LClient(
        guid = CLIENT_2_GUID,
        timestamp = System.currentTimeMillis(),
        code = "C002",
        description = "XYZ Wholesale Co",
        notes = "Wholesale client",
        phone = "+380502345678",
        address = "456 Commerce Ave, Lviv",
        discount = 10.0,
        bonus = 500.0,
        debt = 5000.0,
        priceType = PRICE_TYPE_WHOLESALE,
        isBanned = false,
        groupGuid = "",
        isActive = true,
        isGroup = false,
        latitude = 49.8397,
        longitude = 24.0297
    )

    fun allClients() = listOf(createClient1(), createClient2(), createClient3Banned())
    fun allLClients() = listOf(createLClient1(), createLClient2())

    // ========== Debts ==========

    fun createDebt1ForClient1() = Debt(
        databaseId = TEST_DB_GUID,
        companyGuid = COMPANY_1_GUID,
        clientGuid = CLIENT_1_GUID,
        docGuid = ORDER_1_GUID,
        docId = "DOC-001",
        docType = "order",
        hasContent = 1,
        content = "",
        sum = 1500.0,
        sumIn = 0.0,
        sumOut = 1500.0,
        isTotal = 0,
        sorting = 1,
        timestamp = System.currentTimeMillis()
    )

    fun createDebt2ForClient2() = Debt(
        databaseId = TEST_DB_GUID,
        companyGuid = COMPANY_1_GUID,
        clientGuid = CLIENT_2_GUID,
        docGuid = ORDER_2_GUID,
        docId = "DOC-002",
        docType = "order",
        hasContent = 1,
        content = "",
        sum = 5000.0,
        sumIn = 0.0,
        sumOut = 5000.0,
        isTotal = 0,
        sorting = 1,
        timestamp = System.currentTimeMillis()
    )

    // ========== Products ==========

    fun createProduct1() = Product(
        databaseId = TEST_DB_GUID,
        guid = PRODUCT_1_GUID,
        timestamp = System.currentTimeMillis(),
        code1 = "P001",
        code2 = "PROD001",
        vendorCode = "VND-001",
        barcode = "1234567890123",
        description = "Test Product Alpha",
        descriptionLc = "test product alpha",
        price = 100.0,
        minPrice = 80.0,
        basePrice = 90.0,
        quantity = 50.0,
        weight = 0.5,
        unit = "шт",
        groupGuid = "",
        isActive = 1,
        isGroup = 0
    )

    fun createProduct2() = Product(
        databaseId = TEST_DB_GUID,
        guid = PRODUCT_2_GUID,
        timestamp = System.currentTimeMillis(),
        code1 = "P002",
        code2 = "PROD002",
        vendorCode = "VND-002",
        barcode = "2234567890123",
        description = "Test Product Beta",
        descriptionLc = "test product beta",
        price = 200.0,
        minPrice = 160.0,
        basePrice = 180.0,
        quantity = 100.0,
        weight = 1.0,
        unit = "шт",
        groupGuid = "",
        isActive = 1,
        isGroup = 0
    )

    fun createProduct3OutOfStock() = Product(
        databaseId = TEST_DB_GUID,
        guid = PRODUCT_3_GUID,
        timestamp = System.currentTimeMillis(),
        code1 = "P003",
        code2 = "PROD003",
        vendorCode = "VND-003",
        barcode = "3234567890123",
        description = "Test Product Gamma (Out of Stock)",
        descriptionLc = "test product gamma (out of stock)",
        price = 50.0,
        minPrice = 40.0,
        basePrice = 45.0,
        quantity = 0.0,
        weight = 0.25,
        unit = "шт",
        groupGuid = "",
        isActive = 1,
        isGroup = 0
    )

    fun createLProduct1() = LProduct(
        guid = PRODUCT_1_GUID,
        code = "P001",
        vendorCode = "VND-001",
        description = "Test Product Alpha",
        unit = "шт",
        rest = 50.0,
        quantity = 0.0,
        weight = 0.5,
        price = 100.0,
        priceType = PRICE_TYPE_RETAIL,
        basePrice = 90.0,
        minPrice = 80.0,
        isGroup = false,
        isActive = true
    )

    fun createLProduct2() = LProduct(
        guid = PRODUCT_2_GUID,
        code = "P002",
        vendorCode = "VND-002",
        description = "Test Product Beta",
        unit = "шт",
        rest = 100.0,
        quantity = 0.0,
        weight = 1.0,
        price = 200.0,
        priceType = PRICE_TYPE_RETAIL,
        basePrice = 180.0,
        minPrice = 160.0,
        isGroup = false,
        isActive = true
    )

    fun allProducts() = listOf(createProduct1(), createProduct2(), createProduct3OutOfStock())
    fun allLProducts() = listOf(createLProduct1(), createLProduct2())

    // ========== Product Prices ==========

    fun createPricesForProduct1() = listOf(
        LPrice(
            priceType = PRICE_TYPE_RETAIL,
            price = 100.0,
            description = "Retail Price",
            basePrice = 90.0,
            isCurrent = true
        ),
        LPrice(
            priceType = PRICE_TYPE_WHOLESALE,
            price = 85.0,
            description = "Wholesale Price",
            basePrice = 90.0,
            isCurrent = false
        )
    )

    fun createPricesForProduct2() = listOf(
        LPrice(
            priceType = PRICE_TYPE_RETAIL,
            price = 200.0,
            description = "Retail Price",
            basePrice = 180.0,
            isCurrent = true
        ),
        LPrice(
            priceType = PRICE_TYPE_WHOLESALE,
            price = 170.0,
            description = "Wholesale Price",
            basePrice = 180.0,
            isCurrent = false
        )
    )

    // ========== Orders ==========

    fun createOrder1() = Order(
        guid = ORDER_1_GUID,
        databaseId = TEST_DB_GUID,
        date = "2025-01-15",
        time = System.currentTimeMillis(),
        number = 1,
        companyGuid = COMPANY_1_GUID,
        company = "Main Company LLC",
        storeGuid = STORE_1_GUID,
        store = "Warehouse #1",
        clientGuid = CLIENT_1_GUID,
        clientDescription = "ABC Retail Store",
        discount = 5.0,
        priceType = PRICE_TYPE_RETAIL,
        paymentType = PAYMENT_TYPE_CASH,
        price = 1500.0,
        quantity = 10.0,
        weight = 5.0,
        discountValue = 75.0,
        latitude = 50.4501,
        longitude = 30.5234,
        distance = 120.0,
        isReturn = 0,
        isProcessed = 0,
        isSent = 0
    )

    fun createOrder2Return() = Order(
        guid = ORDER_2_GUID,
        databaseId = TEST_DB_GUID,
        date = "2025-01-16",
        time = System.currentTimeMillis(),
        number = 2,
        companyGuid = COMPANY_1_GUID,
        company = "Main Company LLC",
        storeGuid = STORE_1_GUID,
        store = "Warehouse #1",
        clientGuid = CLIENT_2_GUID,
        clientDescription = "XYZ Wholesale Co",
        discount = 10.0,
        priceType = PRICE_TYPE_WHOLESALE,
        paymentType = PAYMENT_TYPE_CARD,
        price = 500.0,
        quantity = 3.0,
        weight = 1.5,
        discountValue = 50.0,
        isReturn = 1,  // This is a return order
        isProcessed = 0,
        isSent = 0
    )

    fun createOrder3Sent() = Order(
        guid = ORDER_3_GUID,
        databaseId = TEST_DB_GUID,
        date = "2025-01-14",
        time = System.currentTimeMillis() - 86400000, // 1 day ago
        number = 3,
        companyGuid = COMPANY_1_GUID,
        company = "Main Company LLC",
        storeGuid = STORE_2_GUID,
        store = "Warehouse #2",
        clientGuid = CLIENT_1_GUID,
        clientDescription = "ABC Retail Store",
        discount = 0.0,
        priceType = PRICE_TYPE_RETAIL,
        paymentType = PAYMENT_TYPE_CASH,
        price = 300.0,
        quantity = 2.0,
        weight = 1.0,
        isReturn = 0,
        isProcessed = 1,
        isSent = 1  // Already sent to server
    )

    fun allOrders() = listOf(createOrder1(), createOrder2Return(), createOrder3Sent())

    // ========== Order Content ==========

    fun createOrderContent1Line1() = OrderContent(
        orderGuid = ORDER_1_GUID,
        productGuid = PRODUCT_1_GUID,
        unitCode = "шт",
        quantity = 5.0,
        weight = 2.5,
        price = 100.0,
        sum = 500.0,
        discount = 25.0
    )

    fun createOrderContent1Line2() = OrderContent(
        orderGuid = ORDER_1_GUID,
        productGuid = PRODUCT_2_GUID,
        unitCode = "шт",
        quantity = 5.0,
        weight = 5.0,
        price = 200.0,
        sum = 1000.0,
        discount = 50.0
    )

    fun createLOrderContent1Line1() = LOrderContent(
        orderGuid = ORDER_1_GUID,
        productGuid = PRODUCT_1_GUID,
        code = "P001",
        description = "Test Product Alpha",
        unit = "шт",
        quantity = 5.0,
        weight = 2.5,
        price = 100.0,
        sum = 500.0,
        discount = 25.0
    )

    fun createLOrderContent1Line2() = LOrderContent(
        orderGuid = ORDER_1_GUID,
        productGuid = PRODUCT_2_GUID,
        code = "P002",
        description = "Test Product Beta",
        unit = "шт",
        quantity = 5.0,
        weight = 5.0,
        price = 200.0,
        sum = 1000.0,
        discount = 50.0
    )

    fun orderContentForOrder1() = listOf(
        createOrderContent1Line1(),
        createOrderContent1Line2()
    )

    fun lOrderContentForOrder1() = listOf(
        createLOrderContent1Line1(),
        createLOrderContent1Line2()
    )

    // ========== Cash Documents ==========

    fun createCash1() = Cash(
        guid = CASH_1_GUID,
        databaseId = TEST_DB_GUID,
        date = "2025-01-15",
        time = System.currentTimeMillis(),
        number = 1,
        companyGuid = COMPANY_1_GUID,
        company = "Main Company LLC",
        clientGuid = CLIENT_1_GUID,
        client = "ABC Retail Store",
        referenceGuid = ORDER_1_GUID,
        sum = 1500.0,
        notes = "Payment for order #1",
        fiscalNumber = 12345,
        isFiscal = 1,
        isProcessed = 0,
        isSent = 0
    )

    fun createCash2() = Cash(
        guid = CASH_2_GUID,
        databaseId = TEST_DB_GUID,
        date = "2025-01-16",
        time = System.currentTimeMillis(),
        number = 2,
        companyGuid = COMPANY_1_GUID,
        company = "Main Company LLC",
        clientGuid = CLIENT_2_GUID,
        client = "XYZ Wholesale Co",
        sum = 2500.0,
        notes = "Advance payment",
        fiscalNumber = 0,
        isFiscal = 0,
        isProcessed = 0,
        isSent = 0
    )

    fun allCashDocuments() = listOf(createCash1(), createCash2())

    // ========== Tasks ==========

    fun createTask1() = Task(
        guid = TASK_1_GUID,
        databaseId = TEST_DB_GUID,
        time = System.currentTimeMillis(),
        date = "2025-01-15",
        isDone = 0,
        color = "blue",
        clientGuid = CLIENT_1_GUID,
        description = "Visit ABC Retail Store",
        notes = "Check inventory and take new order"
    )

    fun createTask2Completed() = Task(
        guid = TASK_2_GUID,
        databaseId = TEST_DB_GUID,
        time = System.currentTimeMillis() - 86400000, // 1 day ago
        date = "2025-01-14",
        isDone = 1,
        color = "green",
        clientGuid = CLIENT_2_GUID,
        description = "Delivery to XYZ Wholesale",
        notes = "Completed successfully"
    )

    fun allTasks() = listOf(createTask1(), createTask2Completed())

    // ========== Document Totals ==========

    fun createDocumentTotalsForOrder1() = DocumentTotals(
        documents = 1,
        returns = 0,
        weight = 7.5,
        sum = 1500.0,
        discount = 75.0,
        sumReturn = 0.0,
        quantity = 10.0
    )

    fun createDocumentTotalsForAllOrders() = DocumentTotals(
        documents = 2,
        returns = 1,
        weight = 7.5,
        sum = 1800.0,
        discount = 125.0,
        sumReturn = 500.0,
        quantity = 13.0
    )

    fun createEmptyDocumentTotals() = DocumentTotals(
        documents = 0,
        returns = 0,
        weight = 0.0,
        sum = 0.0,
        discount = 0.0,
        sumReturn = 0.0,
        quantity = 0.0
    )

    // ========== Error Scenarios ==========

    /**
     * Test exception for simulating repository errors
     */
    class TestRepositoryException(message: String) : Exception(message)

    /**
     * Test exception for network errors
     */
    class TestNetworkException(message: String) : Exception(message)

    /**
     * Test exception for validation errors
     */
    class TestValidationException(message: String) : Exception(message)

    fun createRepositoryError() = TestRepositoryException("Database connection failed")
    fun createNetworkError() = TestNetworkException("Network timeout")
    fun createValidationError() = TestValidationException("Invalid order data")

    // ========== Helper Methods ==========

    /**
     * Creates a complete test dataset with all related entities
     */
    fun createCompleteDataset() = object {
        val account = createTestAccount()
        val company = createCompany1()
        val store = createStore1()
        val priceTypes = allPriceTypes()
        val paymentTypes = allPaymentTypes()
        val clients = allClients()
        val products = allProducts()
        val orders = allOrders()
        val orderContent = orderContentForOrder1()
        val cashDocuments = allCashDocuments()
        val tasks = allTasks()
        val debts = listOf(createDebt1ForClient1(), createDebt2ForClient2())
    }

    /**
     * Creates a minimal dataset for simple tests
     */
    fun createMinimalDataset() = object {
        val account = createTestAccount()
        val company = createCompany1()
        val client = createClient1()
        val product = createProduct1()
        val order = createOrder1()
    }
}

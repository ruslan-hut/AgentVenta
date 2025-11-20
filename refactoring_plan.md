# AgentVenta Refactoring Plan

> Comprehensive analysis and improvement recommendations for the AgentVenta Android application

## Executive Summary

The AgentVenta codebase is a mature Android sales/order management application using modern Android technologies (Hilt, Room, Retrofit, Navigation Component). However, it exhibits several architectural issues, outdated patterns, and opportunities for significant improvement in maintainability, testability, and code quality.

**Key Findings:**
- ❌ **Zero test coverage** - No unit, integration, or UI tests
- ⚠️ **Security vulnerabilities** - Unencrypted data storage, hardcoded credentials
- ⚠️ **Performance risks** - Blocking calls (`runBlocking`), inefficient database queries
- 📉 **Technical debt** - Heavy code duplication, God classes, tight coupling
- 🔄 **Outdated patterns** - Extensive LiveData usage instead of StateFlow

---

## 1. Architecture Issues

### 1.1 SOLID Principle Violations

#### Single Responsibility Principle (SRP) - God Classes

**SharedViewModel** (`/shared/SharedViewModel.kt` - 492 lines)
- **Problem:** Handles too many responsibilities
  - Current account state management
  - Image loading (Glide configuration)
  - Network sync operations
  - Barcode scanning
  - File cache management
  - Firebase operations
  - Shared parameters across app

**Recommended Refactoring:**
```kotlin
// Split into focused ViewModels/Managers
class AccountStateViewModel @Inject constructor(...)
class ImageLoadingManager @Inject constructor(...)
class SyncCoordinator @Inject constructor(...)
class BarcodeHandler @Inject constructor(...)
class CacheManager @Inject constructor(...)
```

**NetworkRepositoryImpl** (`/http/NetworkRepositoryImpl.kt` - 526 lines)
- **Problem:** Combines multiple concerns
  - Token refresh logic (lines 103-134)
  - Network requests
  - Data persistence (lines 444-463)
  - PDF processing (lines 494-525)
  - Error handling
  - Time formatting

**Recommended Refactoring:**
```kotlin
class TokenManager @Inject constructor(...)
class DataSyncService @Inject constructor(...)
class PdfExportService @Inject constructor(...)
class SyncErrorHandler @Inject constructor(...)
```

**MainActivity** (`/MainActivity.kt`)
- **Problem:** Activity doing too much
  - Permission handling (lines 203-255)
  - Navigation setup
  - Progress UI management (lines 181-201)
  - Barcode scanning (lines 257-283)
  - Location service management
  - Message conversion (lines 159-179)

**Recommended Refactoring:**
```kotlin
class PermissionManager @Inject constructor(...)
class ProgressCoordinator @Inject constructor(...)
class BarcodeScanner @Inject constructor(...)
```

#### Open/Closed Principle (OCP) Violations

**DocumentListViewModel** (`/documents/common/DocumentListViewModel.kt`)
- Line 19: Open class but uses hardcoded string formatting
- Lines 70-81: `updateCounters` with hardcoded locale formatting
- **Solution:** Use strategy pattern for formatting

```kotlin
interface CounterFormatter {
    fun format(counter: DocumentCounter): String
}

class DocumentListViewModel(
    private val formatter: CounterFormatter = DefaultCounterFormatter()
)
```

#### Dependency Inversion Principle (DIP) Violations

**OrderRepositoryImpl** (`/dao/impl/OrderRepositoryImpl.kt`)
- Line 43: `private val utils = Utils()` - direct instantiation
- **Problem:** Cannot be mocked for testing

**CashRepositoryImpl** (`/dao/impl/CashRepositoryImpl.kt`)
- Line 25: Same issue with Utils instantiation

**Recommended Fix:**
```kotlin
class OrderRepositoryImpl @Inject constructor(
    private val orderDao: OrderDao,
    private val utils: UtilsInterface  // Inject interface instead
)
```

### 1.2 Tight Coupling Issues

**NetworkRepositoryImpl with Authentication**
- Lines 59-101: Tightly coupled to `HttpAuthInterceptor` and `TokenRefresh`
- Line 115: `runBlocking` in token refresh - **CRITICAL ISSUE**
- Lines 127, 143: Synchronous account saving with `runBlocking`

**Problem:** Blocking the thread can cause ANR (Application Not Responding)

**Recommended Fix:**
```kotlin
// Instead of:
val response = runBlocking { apiService?.check(accountGuid) }

// Use proper coroutines:
suspend fun refreshToken(accountGuid: String): Result<TokenResponse> {
    return withContext(Dispatchers.IO) {
        try {
            val response = apiService.check(accountGuid)
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
```

**SharedViewModel Coupling**
- Lines 52-63: Tightly coupled to multiple repositories
- Lines 234-238: Direct Firebase usage in ViewModel
- Lines 298-315: Direct Glide usage with hard-coded configuration

**Solution:** Extract to separate services/use cases

### 1.3 Missing Abstractions

**No Domain Layer**
- All ViewModels directly inject repository implementations
- Business logic scattered across ViewModels and Repositories
- **Recommendation:** Introduce use cases/interactors layer

**Recommended Domain Layer:**
```kotlin
// /domain/usecase/order/
class GetOrdersUseCase @Inject constructor(
    private val orderRepository: OrderRepository
) {
    suspend operator fun invoke(filter: String): Flow<Result<List<Order>>> {
        return orderRepository.getDocuments(filter)
            .map { orders ->
                // Business logic here
                Result.Success(orders)
            }
            .catch { e ->
                emit(Result.Error(e))
            }
    }
}

class SaveOrderUseCase @Inject constructor(...)
class SyncDataUseCase @Inject constructor(...)
```

**No Error Handling Abstraction**
- Each ViewModel handles errors differently
- No consistent error types

**Recommended Solution:**
```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: DomainException) : Result<Nothing>()
    data class Loading(val progress: Int = 0) : Result<Nothing>()
}

sealed class DomainException : Exception() {
    data class NetworkError(val code: Int, override val message: String) : DomainException()
    data class DatabaseError(override val message: String) : DomainException()
    data class ValidationError(val field: String, override val message: String) : DomainException()
    data class AuthenticationError(override val message: String) : DomainException()
}
```

**No Data Mapper Abstraction**
- Room entities used directly in UI layer
- No separation between database models and UI models

**Recommended Solution:**
```kotlin
// /domain/model/
data class OrderUiModel(
    val guid: String,
    val clientName: String,
    val totalAmount: String,
    val dateFormatted: String,
    val status: OrderStatus
)

// /data/mapper/
class OrderMapper @Inject constructor() {
    fun toUiModel(entity: Order): OrderUiModel { ... }
    fun toEntity(uiModel: OrderUiModel): Order { ... }
}
```

---

## 2. Modern Android Practices

### 2.1 LiveData → StateFlow/SharedFlow Migration

**Problem:** Heavy use of LiveData instead of Kotlin Flow

**Why StateFlow is better:**
- Better coroutine support
- Type-safe
- No need for null safety (no initial null value)
- Can be combined with other flows
- Better lifecycle handling

**Files requiring migration:**
- `OrderViewModel.kt` (lines 37-56)
- `DocumentListViewModel.kt` (lines 24-33)
- `SharedViewModel.kt` (lines 67-86)
- **All ViewModels** (30+ files)

**Migration Example:**

```kotlin
// Before (LiveData)
class OrderViewModel : ViewModel() {
    private val _documentGuid = MutableLiveData("")
    val documentGuid: LiveData<String> = _documentGuid

    private val _navigateToPage = MutableLiveData<Int>()
    val navigateToPage: LiveData<Int> = _navigateToPage

    val document = _documentGuid.switchMap { guid ->
        repository.getDocument(guid).asLiveData()
    }
}

// After (StateFlow/SharedFlow)
class OrderViewModel : ViewModel() {
    private val _documentGuid = MutableStateFlow("")
    val documentGuid: StateFlow<String> = _documentGuid.asStateFlow()

    // Use SharedFlow for one-time events (no replay on config changes)
    private val _navigateToPage = MutableSharedFlow<Int>(replay = 0)
    val navigateToPage: SharedFlow<Int> = _navigateToPage.asSharedFlow()

    val document: StateFlow<Order?> = _documentGuid
        .flatMapLatest { guid ->
            repository.getDocument(guid)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
}
```

**UI State Pattern:**
```kotlin
data class OrderUiState(
    val document: Order = Order(),
    val content: List<LOrderContent> = emptyList(),
    val isLoading: Boolean = false,
    val error: DomainException? = null,
    val saveResult: SaveResult? = null
)

class OrderViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(OrderUiState())
    val uiState: StateFlow<OrderUiState> = _uiState.asStateFlow()

    fun updateDocument(update: (Order) -> Order) {
        _uiState.update { it.copy(document = update(it.document)) }
    }
}
```

**Fragment Collection Pattern:**
```kotlin
// In Fragment
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // Collect StateFlow
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiState.collect { state ->
                updateUI(state)
            }
        }
    }

    // Collect SharedFlow (one-time events)
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.navigateToPage.collect { page ->
                navigateToPage(page)
            }
        }
    }
}
```

### 2.2 Coroutine Best Practices

**Critical Issue: runBlocking Usage**

**Location:** `NetworkRepositoryImpl.kt`
- Line 115: `val response = runBlocking { apiService?.check(accountGuid) }`
- Lines 127, 143: `runBlocking { userAccountRepository.saveAccount(...) }`

**Risk:** Application Not Responding (ANR) errors

**Fix:**
```kotlin
// Convert entire class methods to suspend functions
suspend fun updateAll(type: String): Flow<Result> = flow {
    emit(Result.Progress("Refreshing token..."))

    val tokenResult = refreshTokenSafely()
    if (tokenResult is Result.Error) {
        emit(tokenResult)
        return@flow
    }

    emit(Result.Progress("Downloading data..."))
    // ... rest of logic
}

private suspend fun refreshTokenSafely(): Result {
    return withContext(Dispatchers.IO) {
        try {
            val response = apiService.check(currentAccount.guid)
            Result.Success(response)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
```

**Improper Context Switching:**

**Problem:** Multiple ViewModels use `withContext(Dispatchers.Main)` inside `viewModelScope.launch`
- `viewModelScope` already runs on Main dispatcher
- Unnecessary context switches

**Example locations:**
- OrderViewModel lines 87, 118, 165, 205, 230

**Fix:**
```kotlin
// Before (unnecessary context switch)
viewModelScope.launch {
    withContext(Dispatchers.Main) {  // Already on Main!
        _documentGuid.value = guid
    }
}

// After (remove redundant context)
viewModelScope.launch {
    _documentGuid.value = guid
}

// Only use withContext for IO operations
viewModelScope.launch {
    val result = withContext(Dispatchers.IO) {
        repository.saveDocument(document)
    }
    // Back on Main dispatcher automatically
    handleResult(result)
}
```

### 2.3 Deprecated APIs

**onRequestPermissionsResult** (MainActivity.kt line 242)
- Deprecated in Android 11+

**Migration to ActivityResultContracts:**
```kotlin
class MainActivity : AppCompatActivity() {

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startLocationService()
        } else {
            showPermissionDeniedDialog()
        }
    }

    private fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                startLocationService()
            }
            shouldShowRequestPermissionRationale(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                showPermissionRationale()
            }
            else -> {
                locationPermissionLauncher.launch(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
        }
    }
}
```

---

## 3. Code Duplication

### 3.1 Document ViewModel Pattern

**Duplicated across:**
- `OrderViewModel.kt`
- `CashViewModel.kt`
- `TaskViewModel.kt`

**Common duplicated code:**
```kotlin
// All three have this pattern:
private val _documentGuid = MutableLiveData("")
val documentGuid: LiveData<String> = _documentGuid

val document = _documentGuid.switchMap { guid ->
    repository.getDocument(guid).asLiveData()
}

fun setCurrentDocument(guid: String) {
    _documentGuid.value = guid
}

fun initNewDocument() {
    viewModelScope.launch {
        val newDoc = repository.newDocument()
        _documentGuid.value = newDoc?.guid ?: ""
    }
}

fun deleteDocument(guid: String) {
    viewModelScope.launch(Dispatchers.IO) {
        repository.deleteDocument(guid)
    }
}
```

**Refactored Base Class:**
```kotlin
abstract class DocumentViewModel<T>(
    protected val repository: DocumentRepository<T>
) : ViewModel() {

    private val _documentGuid = MutableStateFlow("")
    val documentGuid: StateFlow<String> = _documentGuid.asStateFlow()

    val document: StateFlow<T?> = _documentGuid
        .flatMapLatest { guid ->
            if (guid.isEmpty()) flowOf(null)
            else repository.getDocument(guid)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun setCurrentDocument(guid: String) {
        _documentGuid.value = guid
    }

    fun initNewDocument() {
        viewModelScope.launch {
            repository.newDocument()?.let { doc ->
                _documentGuid.value = getDocumentGuid(doc)
                onNewDocumentCreated(doc)
            }
        }
    }

    fun deleteDocument(guid: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteDocument(guid)
            }
            onDocumentDeleted(guid)
        }
    }

    // Abstract methods for subclass customization
    protected abstract fun getDocumentGuid(document: T): String
    protected open fun onNewDocumentCreated(document: T) {}
    protected open fun onDocumentDeleted(guid: String) {}
}

// Usage
class OrderViewModel @Inject constructor(
    orderRepository: OrderRepository,
    private val sharedViewModel: SharedViewModel
) : DocumentViewModel<Order>(orderRepository) {

    override fun getDocumentGuid(document: Order) = document.guid

    override fun onNewDocumentCreated(document: Order) {
        // Order-specific logic
    }

    // Only order-specific methods here
    fun addProduct(product: LProduct) { ... }
    fun calculateTotals() { ... }
}
```

**Impact:** Eliminates ~100 lines of duplicated code per ViewModel

### 3.2 Repository Implementation Duplication

**Duplicated in:**
- `OrderRepositoryImpl.kt`
- `CashRepositoryImpl.kt`

**Common patterns:**
- Utils instantiation (lines 25-43)
- Date handling
- Filter logic in getDocuments (lines 46-51)
- Totals query pattern (lines 75-79)

**Refactored Base Repository:**
```kotlin
abstract class BaseDocumentRepository<T>(
    protected val utils: UtilsInterface,
    protected val ioDispatcher: CoroutineDispatcher
) : DocumentRepository<T> {

    protected fun applyFilter(filter: String): String {
        return if (filter.isEmpty()) "%" else "%$filter%"
    }

    protected fun getCurrentDate(): String {
        return utils.dateToString(Date())
    }

    override fun getDocuments(filter: String): Flow<List<T>> {
        return getDocumentsInternal(applyFilter(filter))
            .flowOn(ioDispatcher)
    }

    protected abstract fun getDocumentsInternal(filter: String): Flow<List<T>>

    // Common methods for all document repositories
}

class OrderRepositoryImpl @Inject constructor(
    private val orderDao: OrderDao,
    utils: UtilsInterface,
    @IoDispatcher ioDispatcher: CoroutineDispatcher
) : BaseDocumentRepository<Order>(utils, ioDispatcher) {

    override fun getDocumentsInternal(filter: String): Flow<List<Order>> {
        return orderDao.getDocuments(filter)
    }

    // Order-specific methods only
}
```

### 3.3 List Adapter Duplication

**Problem:** Similar adapter patterns across multiple list screens

**Generic Adapter Solution:**
```kotlin
abstract class BaseListAdapter<T, VH : RecyclerView.ViewHolder>(
    diffCallback: DiffUtil.ItemCallback<T>
) : ListAdapter<T, VH>(diffCallback) {

    var onItemClick: ((T) -> Unit)? = null
    var onItemLongClick: ((T) -> Boolean)? = null

    protected fun setupClickListeners(holder: VH, item: T) {
        holder.itemView.setOnClickListener {
            onItemClick?.invoke(item)
        }
        holder.itemView.setOnLongClickListener {
            onItemLongClick?.invoke(item) ?: false
        }
    }
}

// Usage
class OrderListAdapter : BaseListAdapter<Order, OrderViewHolder>(OrderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemOrderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = getItem(position)
        holder.bind(order)
        setupClickListeners(holder, order)
    }

    class OrderViewHolder(
        private val binding: ItemOrderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(order: Order) {
            binding.order = order
            binding.executePendingBindings()
        }
    }
}

object OrderDiffCallback : DiffUtil.ItemCallback<Order>() {
    override fun areItemsTheSame(oldItem: Order, newItem: Order) =
        oldItem.guid == newItem.guid

    override fun areContentsTheSame(oldItem: Order, newItem: Order) =
        oldItem == newItem
}
```

---

## 4. Error Handling

### 4.1 Current State - Inconsistent Error Handling

**Missing try-catch blocks in ViewModels:**

**OrderViewModel.kt:**
- Lines 83-94: `initNewDocument` - no error handling
- Lines 132-169: `onProductClick` - no error handling for calculations
- No error state exposed to UI

**CashViewModel.kt:**
- No error handling in any method
- Line 69: `toDoubleOrNull()` returns null but not validated

**SharedViewModel.kt:**
- Lines 202, 327, 451: Generic `catch (e: Exception)` with only logging
- No user feedback on errors

**NetworkRepositoryImpl.kt:**
- Lines 221-229: Catches exceptions but emits generic error message
- Line 302: Loses error context

### 4.2 Recommended Error Handling Architecture

**Step 1: Define Error Types**
```kotlin
// /domain/error/DomainException.kt
sealed class DomainException : Exception() {
    data class NetworkError(
        val code: Int,
        override val message: String,
        val isRetryable: Boolean = true
    ) : DomainException()

    data class DatabaseError(
        override val message: String
    ) : DomainException()

    data class ValidationError(
        val field: String,
        override val message: String
    ) : DomainException()

    data class AuthenticationError(
        override val message: String,
        val shouldLogout: Boolean = false
    ) : DomainException()

    data class BusinessLogicError(
        override val message: String
    ) : DomainException()
}
```

**Step 2: Result Wrapper**
```kotlin
// /domain/result/Result.kt
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: DomainException) : Result<Nothing>()
    data class Loading(val progress: Int = 0) : Result<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading

    fun getOrNull(): T? = (this as? Success)?.data

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw exception
        is Loading -> throw IllegalStateException("Data is still loading")
    }
}

// Extension functions for easier use
inline fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    if (this is Result.Success) action(data)
    return this
}

inline fun <T> Result<T>.onError(action: (DomainException) -> Unit): Result<T> {
    if (this is Result.Error) action(exception)
    return this
}
```

**Step 3: Repository Error Handling**
```kotlin
class OrderRepositoryImpl @Inject constructor(
    private val orderDao: OrderDao,
    private val utils: UtilsInterface,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : OrderRepository {

    override suspend fun saveDocument(document: Order): Result<Order> {
        return withContext(ioDispatcher) {
            try {
                // Validate first
                validateOrder(document)?.let { error ->
                    return@withContext Result.Error(error)
                }

                orderDao.insertOrder(document)
                Result.Success(document)
            } catch (e: SQLiteException) {
                Result.Error(
                    DomainException.DatabaseError("Failed to save order: ${e.message}")
                )
            } catch (e: Exception) {
                Result.Error(
                    DomainException.BusinessLogicError("Unexpected error: ${e.message}")
                )
            }
        }
    }

    private fun validateOrder(order: Order): DomainException.ValidationError? {
        return when {
            order.clientGuid.isNullOrEmpty() ->
                DomainException.ValidationError("client", "Client is required")
            order.content.isEmpty() ->
                DomainException.ValidationError("content", "Order must have at least one item")
            else -> null
        }
    }
}
```

**Step 4: ViewModel Error Handling**
```kotlin
class OrderViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    data class OrderUiState(
        val document: Order = Order(),
        val isLoading: Boolean = false,
        val error: UiError? = null,
        val saveSuccess: Boolean = false
    )

    private val _uiState = MutableStateFlow(OrderUiState())
    val uiState: StateFlow<OrderUiState> = _uiState.asStateFlow()

    fun saveOrder() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = orderRepository.saveDocument(_uiState.value.document)

            result
                .onSuccess { order ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            saveSuccess = true,
                            document = order
                        )
                    }
                }
                .onError { exception ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = errorHandler.toUiError(exception)
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
```

**Step 5: UI Error Display**
```kotlin
// Fragment
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiState.collect { state ->
                handleUiState(state)
            }
        }
    }
}

private fun handleUiState(state: OrderUiState) {
    binding.progressBar.isVisible = state.isLoading

    state.error?.let { error ->
        showErrorDialog(error)
        viewModel.clearError()
    }

    if (state.saveSuccess) {
        showSuccessMessage()
        navigateBack()
    }
}

private fun showErrorDialog(error: UiError) {
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.error_title)
        .setMessage(error.message)
        .setPositiveButton(R.string.ok) { dialog, _ ->
            dialog.dismiss()
        }
        .apply {
            if (error.isRetryable) {
                setNegativeButton(R.string.retry) { _, _ ->
                    viewModel.saveOrder()
                }
            }
        }
        .show()
}
```

### 4.3 Network Error Handling with Retry

**Enhanced NetworkRepository:**
```kotlin
class NetworkRepositoryImpl @Inject constructor(
    private val apiService: HttpClientApi,
    private val retryPolicy: RetryPolicy,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : NetworkRepository {

    override suspend fun updateAll(type: String): Flow<Result<SyncData>> = flow {
        emit(Result.Loading(0))

        retryPolicy.retry {
            fetchDataWithProgress(type)
        }
            .onSuccess { data ->
                emit(Result.Success(data))
            }
            .onError { exception ->
                emit(Result.Error(exception))
            }
    }.flowOn(ioDispatcher)

    private suspend fun fetchDataWithProgress(type: String): Result<SyncData> {
        return try {
            val response = apiService.getData(type)
            if (response.isSuccessful) {
                Result.Success(response.body()!!)
            } else {
                Result.Error(
                    DomainException.NetworkError(
                        code = response.code(),
                        message = "Server error: ${response.message()}",
                        isRetryable = response.code() in 500..599
                    )
                )
            }
        } catch (e: IOException) {
            Result.Error(
                DomainException.NetworkError(
                    code = -1,
                    message = "Network connection failed",
                    isRetryable = true
                )
            )
        } catch (e: HttpException) {
            Result.Error(
                DomainException.NetworkError(
                    code = e.code(),
                    message = e.message(),
                    isRetryable = e.code() in 500..599
                )
            )
        }
    }
}

// Retry policy with exponential backoff
class RetryPolicy @Inject constructor() {
    suspend fun <T> retry(
        maxAttempts: Int = 3,
        initialDelay: Long = 1000,
        maxDelay: Long = 10000,
        factor: Double = 2.0,
        block: suspend () -> Result<T>
    ): Result<T> {
        var currentDelay = initialDelay
        repeat(maxAttempts) { attempt ->
            val result = block()

            if (result is Result.Success) {
                return result
            }

            if (result is Result.Error && !result.exception.isRetryable()) {
                return result
            }

            if (attempt < maxAttempts - 1) {
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            }
        }

        return Result.Error(
            DomainException.NetworkError(
                code = -1,
                message = "Max retry attempts reached",
                isRetryable = false
            )
        )
    }
}
```

---

## 5. Testing Strategy

### 5.1 Current State

**❌ ZERO TEST COVERAGE**
- No `*Test.kt` files found in project
- No `test/` or `androidTest/` source sets configured
- High risk for regressions

### 5.2 Recommended Testing Architecture

**Add Test Dependencies** (`app/build.gradle`):
```gradle
dependencies {
    // Unit Testing
    testImplementation "junit:junit:4.13.2"
    testImplementation "org.mockito.kotlin:mockito-kotlin:5.1.0"
    testImplementation "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3"
    testImplementation "app.cash.turbine:turbine:1.0.0"  // Flow testing
    testImplementation "com.google.truth:truth:1.1.5"
    testImplementation "androidx.arch.core:core-testing:2.2.0"  // InstantTaskExecutorRule

    // Android Testing
    androidTestImplementation "androidx.test.ext:junit:1.1.5"
    androidTestImplementation "androidx.test.espresso:espresso-core:3.5.1"
    androidTestImplementation "com.google.dagger:hilt-android-testing:2.48"
    kspAndroidTest "com.google.dagger:hilt-compiler:2.48"
    androidTestImplementation "androidx.room:room-testing:2.8.4"
    androidTestImplementation "com.squareup.okhttp3:mockwebserver:4.12.0"
    androidTestImplementation "androidx.navigation:navigation-testing:2.9.3"
}
```

### 5.3 Unit Test Examples

**ViewModel Testing:**
```kotlin
// test/java/ua/com/programmer/agentventa/documents/order/OrderViewModelTest.kt
@ExperimentalCoroutinesApi
class OrderViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: OrderViewModel
    private lateinit var orderRepository: OrderRepository
    private lateinit var sharedViewModel: SharedViewModel

    @Before
    fun setup() {
        orderRepository = mockk(relaxed = true)
        sharedViewModel = mockk(relaxed = true)
        viewModel = OrderViewModel(orderRepository, sharedViewModel)
    }

    @Test
    fun `initNewDocument creates new order with current date`() = runTest {
        // Given
        val expectedOrder = Order(
            guid = "test-guid",
            date = "2025-01-15"
        )
        coEvery { orderRepository.newDocument() } returns expectedOrder

        // When
        viewModel.initNewDocument()
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.document.guid).isEqualTo("test-guid")
            assertThat(state.isLoading).isFalse()
        }
    }

    @Test
    fun `saveOrder shows error when validation fails`() = runTest {
        // Given
        val invalidOrder = Order(clientGuid = null)
        val expectedError = DomainException.ValidationError(
            field = "client",
            message = "Client is required"
        )
        coEvery { orderRepository.saveDocument(any()) } returns Result.Error(expectedError)

        // When
        viewModel.saveOrder()
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.error).isNotNull()
            assertThat(state.error?.message).contains("Client is required")
            assertThat(state.saveSuccess).isFalse()
        }
    }

    @Test
    fun `addProduct updates order content and recalculates totals`() = runTest {
        // Given
        val product = LProduct(
            guid = "prod-1",
            description = "Test Product",
            price = 100.0
        )

        // When
        viewModel.onProductClick(product, 5.0)
        advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.document.content).hasSize(1)
            assertThat(state.document.content[0].quantity).isEqualTo(5.0)
            assertThat(state.document.total).isEqualTo(500.0)
        }
    }
}

// Test rule for main dispatcher
@ExperimentalCoroutinesApi
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
```

**Repository Testing:**
```kotlin
// test/java/ua/com/programmer/agentventa/dao/impl/OrderRepositoryImplTest.kt
@RunWith(RobolectricTestRunner::class)
class OrderRepositoryImplTest {

    private lateinit var database: AppDatabase
    private lateinit var orderDao: OrderDao
    private lateinit var repository: OrderRepositoryImpl
    private lateinit var utils: UtilsInterface

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        orderDao = database.orderDao()
        utils = mockk(relaxed = true)

        repository = OrderRepositoryImpl(
            orderDao = orderDao,
            utils = utils,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `saveDocument inserts order to database`() = runTest {
        // Given
        val order = Order(
            guid = "test-guid",
            clientGuid = "client-1",
            date = "2025-01-15"
        )

        // When
        val result = repository.saveDocument(order)

        // Then
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val savedOrder = orderDao.getOrder("test-guid")
        assertThat(savedOrder).isNotNull()
        assertThat(savedOrder?.clientGuid).isEqualTo("client-1")
    }

    @Test
    fun `saveDocument returns validation error when client is missing`() = runTest {
        // Given
        val invalidOrder = Order(
            guid = "test-guid",
            clientGuid = null
        )

        // When
        val result = repository.saveDocument(invalidOrder)

        // Then
        assertThat(result).isInstanceOf(Result.Error::class.java)
        val error = (result as Result.Error).exception
        assertThat(error).isInstanceOf(DomainException.ValidationError::class.java)
    }
}
```

**Use Case Testing:**
```kotlin
// test/java/ua/com/programmer/agentventa/domain/usecase/SaveOrderUseCaseTest.kt
class SaveOrderUseCaseTest {

    private lateinit var useCase: SaveOrderUseCase
    private lateinit var orderRepository: OrderRepository
    private lateinit var clientRepository: ClientRepository

    @Before
    fun setup() {
        orderRepository = mockk()
        clientRepository = mockk()
        useCase = SaveOrderUseCase(orderRepository, clientRepository)
    }

    @Test
    fun `invoke validates client exists before saving order`() = runTest {
        // Given
        val order = Order(clientGuid = "client-1")
        coEvery { clientRepository.getClient("client-1") } returns null

        // When
        val result = useCase(order)

        // Then
        assertThat(result).isInstanceOf(Result.Error::class.java)
        val error = (result as Result.Error).exception
        assertThat(error).isInstanceOf(DomainException.ValidationError::class.java)

        coVerify(exactly = 0) { orderRepository.saveDocument(any()) }
    }

    @Test
    fun `invoke saves order when all validations pass`() = runTest {
        // Given
        val order = Order(clientGuid = "client-1")
        val client = Client(guid = "client-1", description = "Test Client")

        coEvery { clientRepository.getClient("client-1") } returns client
        coEvery { orderRepository.saveDocument(order) } returns Result.Success(order)

        // When
        val result = useCase(order)

        // Then
        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify(exactly = 1) { orderRepository.saveDocument(order) }
    }
}
```

### 5.4 Integration Test Examples

**Database Integration Test:**
```kotlin
// androidTest/java/ua/com/programmer/agentventa/dao/OrderDaoTest.kt
@RunWith(AndroidJUnit4::class)
class OrderDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var orderDao: OrderDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .build()
        orderDao = database.orderDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertOrderAndRead() = runTest {
        // Given
        val order = Order(
            guid = "order-1",
            dbGuid = "db-1",
            clientGuid = "client-1",
            date = "2025-01-15"
        )

        // When
        orderDao.insertOrder(order)

        // Then
        val retrieved = orderDao.getOrder("order-1")
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.clientGuid).isEqualTo("client-1")
    }

    @Test
    fun getDocuments_filtersCurrentAccountOnly() = runTest {
        // Given
        val account1 = UserAccount(guid = "db-1", isCurrent = true)
        val account2 = UserAccount(guid = "db-2", isCurrent = false)
        database.userAccountDao().insertAccount(account1)
        database.userAccountDao().insertAccount(account2)

        val order1 = Order(guid = "order-1", dbGuid = "db-1", date = "2025-01-15")
        val order2 = Order(guid = "order-2", dbGuid = "db-2", date = "2025-01-15")
        orderDao.insertOrder(order1)
        orderDao.insertOrder(order2)

        // When
        val orders = orderDao.getDocuments("").first()

        // Then
        assertThat(orders).hasSize(1)
        assertThat(orders[0].guid).isEqualTo("order-1")
    }
}
```

**Network Integration Test:**
```kotlin
// androidTest/java/ua/com/programmer/agentventa/http/NetworkRepositoryImplTest.kt
@RunWith(AndroidJUnit4::class)
class NetworkRepositoryImplTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: HttpClientApi
    private lateinit var repository: NetworkRepositoryImpl

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(HttpClientApi::class.java)
        repository = NetworkRepositoryImpl(apiService, /* ... */)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun updateAll_successfulResponse_emitsSuccess() = runTest {
        // Given
        val responseBody = """{"clients": [{"guid": "1", "name": "Test"}]}"""
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(responseBody)
        )

        // When
        val results = repository.updateAll("clients").toList()

        // Then
        assertThat(results.last()).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun updateAll_networkError_retriesWithBackoff() = runTest {
        // Given
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        // When
        val results = repository.updateAll("clients").toList()

        // Then
        assertThat(mockWebServer.requestCount).isEqualTo(3)
        assertThat(results.last()).isInstanceOf(Result.Success::class.java)
    }
}
```

### 5.5 UI Test Examples

**Order Creation Flow Test:**
```kotlin
// androidTest/java/ua/com/programmer/agentventa/documents/order/OrderFragmentTest.kt
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class OrderFragmentTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun createOrder_selectClient_savesSuccessfully() {
        // Navigate to order creation
        onView(withId(R.id.fab_new_order)).perform(click())

        // Select client
        onView(withId(R.id.button_select_client)).perform(click())
        onView(withText("Test Client")).perform(click())

        // Add product
        onView(withId(R.id.button_add_product)).perform(click())
        onView(withText("Test Product")).perform(click())

        // Enter quantity
        onView(withId(R.id.edit_quantity))
            .perform(replaceText("5"))

        // Save order
        onView(withId(R.id.button_save)).perform(click())

        // Verify success message
        onView(withText(R.string.order_saved))
            .inRoot(isToast())
            .check(matches(isDisplayed()))
    }

    @Test
    fun createOrder_withoutClient_showsError() {
        // Navigate to order creation
        onView(withId(R.id.fab_new_order)).perform(click())

        // Try to save without selecting client
        onView(withId(R.id.button_save)).perform(click())

        // Verify error shown
        onView(withText(R.string.error_client_required))
            .check(matches(isDisplayed()))
    }
}
```

### 5.6 Test Coverage Goals

**Target Coverage:**
- **Unit Tests:** 70% code coverage
  - ViewModels: 80%
  - Repositories: 75%
  - Use Cases: 90%
  - Utilities: 85%

- **Integration Tests:** 40% code coverage
  - Database operations: 60%
  - Network calls: 50%
  - Repository + DAO: 55%

- **UI Tests:** 30% code coverage
  - Critical user flows: 80%
  - Form validation: 60%
  - Navigation: 40%

**Measurement:**
```gradle
// Add to app/build.gradle
android {
    buildTypes {
        debug {
            testCoverageEnabled = true
        }
    }
}

// Generate coverage report
./gradlew createDebugCoverageReport
// Report available at: app/build/reports/coverage/debug/index.html
```

---

## 6. Performance Improvements

### 6.1 Database Query Optimization

**Problem: Inefficient Subqueries**

**Current (OrderDao.kt lines 67-79):**
```kotlin
@Query("""
    SELECT * FROM orders
    WHERE db_guid IN (SELECT guid FROM user_accounts WHERE is_current=1)
    ORDER BY date DESC
    LIMIT 200
""")
```

**Issue:** Subquery executed for every query

**Solution 1: Cached Current Account GUID**
```kotlin
// In UserAccountRepository
class UserAccountRepositoryImpl @Inject constructor(
    private val userAccountDao: UserAccountDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : UserAccountRepository {

    private val _currentAccountGuid = MutableStateFlow<String?>(null)
    val currentAccountGuid: StateFlow<String?> = _currentAccountGuid.asStateFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            userAccountDao.getCurrentAccount()
                .map { it?.guid }
                .collect { guid ->
                    _currentAccountGuid.value = guid
                }
        }
    }
}

// In OrderDao
@Query("""
    SELECT * FROM orders
    WHERE db_guid = :currentDbGuid
    ORDER BY date DESC
    LIMIT 200
""")
fun getDocuments(currentDbGuid: String, filter: String): Flow<List<Order>>
```

**Solution 2: Database Indexes**
```kotlin
@Entity(
    tableName = "orders",
    indices = [
        Index(value = ["db_guid"]),
        Index(value = ["client_guid"]),
        Index(value = ["date"]),
        Index(value = ["db_guid", "date"]),  // Composite for common query
        Index(value = ["db_guid", "is_sent"])  // For sync queries
    ]
)
data class Order(...)

@Entity(
    tableName = "user_accounts",
    indices = [
        Index(value = ["is_current"])
    ]
)
data class UserAccount(...)
```

**Problem: N+1 Query (OrderDao.kt lines 144-173)**

**Current:**
```kotlin
// Fragment loads list of orders
// Then for each order, loads content separately
orders.forEach { order ->
    val content = orderDao.getOrderContent(order.guid)
}
```

**Solution: Use @Transaction with @Relation**
```kotlin
// Create compound object
data class OrderWithContent(
    @Embedded val order: Order,
    @Relation(
        parentColumn = "guid",
        entityColumn = "order_guid"
    )
    val content: List<OrderContent>
)

@Transaction
@Query("""
    SELECT * FROM orders
    WHERE db_guid = :currentDbGuid
    ORDER BY date DESC
    LIMIT :limit
""")
fun getOrdersWithContent(
    currentDbGuid: String,
    limit: Int = 200
): Flow<List<OrderWithContent>>
```

**Problem: Inefficient LIKE Queries (ClientDao.kt line 25)**

**Current:**
```kotlin
@Query("""
    SELECT * FROM clients
    WHERE description LIKE :filter
""")
```

**Issue:** LIKE with leading wildcard (`%filter%`) prevents index usage

**Solution: Full-Text Search (FTS)**
```kotlin
// Create FTS table
@Fts4(contentEntity = Client::class)
@Entity(tableName = "clients_fts")
data class ClientFts(
    @ColumnInfo(name = "rowid")
    val rowid: Int,
    val description: String,
    val code: String
)

// FTS DAO
@Dao
interface ClientFtsDao {
    @Query("""
        SELECT c.* FROM clients c
        JOIN clients_fts fts ON c.rowid = fts.rowid
        WHERE clients_fts MATCH :query
        ORDER BY c.description
    """)
    fun searchClients(query: String): Flow<List<Client>>
}

// Usage in Repository
override fun searchClients(query: String): Flow<List<Client>> {
    return if (query.length < 3) {
        // For short queries, use regular LIKE
        clientDao.getClients("%$query%")
    } else {
        // For longer queries, use FTS
        clientFtsDao.searchClients("$query*")
    }
}
```

**Problem: Missing Pagination**

**Solution: Paging 3 Library**
```kotlin
// Add dependency
implementation "androidx.paging:paging-runtime-ktx:3.2.1"

// DAO
@Query("""
    SELECT * FROM orders
    WHERE db_guid = :currentDbGuid
    ORDER BY date DESC
""")
fun getDocumentsPaged(currentDbGuid: String): PagingSource<Int, Order>

// Repository
override fun getDocumentsPaged(filter: String): Flow<PagingData<Order>> {
    return Pager(
        config = PagingConfig(
            pageSize = 50,
            prefetchDistance = 10,
            enablePlaceholders = false
        ),
        pagingSourceFactory = {
            orderDao.getDocumentsPaged(currentAccountGuid)
        }
    ).flow
}

// ViewModel
val orders: Flow<PagingData<Order>> = repository.getDocumentsPaged("")
    .cachedIn(viewModelScope)

// Fragment
val adapter = OrderPagingAdapter()
binding.recyclerView.adapter = adapter

viewLifecycleOwner.lifecycleScope.launch {
    viewModel.orders.collectLatest { pagingData ->
        adapter.submitData(pagingData)
    }
}
```

### 6.2 Memory Leak Prevention

**Problem: Lambda Storage in SharedViewModel (lines 102-103)**

**Current:**
```kotlin
var selectClientAction: (LClient, () -> Unit) -> Unit = { _, _ -> }
var selectProductAction: (LProduct?, () -> Unit) -> Unit = { _, _ -> }
```

**Issue:** Lambdas may hold Fragment/Activity references

**Solution: Use SharedFlow for Events**
```kotlin
// Remove lambda storage
sealed class SelectionEvent {
    data class ClientSelected(val client: LClient) : SelectionEvent()
    data class ProductSelected(val product: LProduct) : SelectionEvent()
}

private val _selectionEvents = MutableSharedFlow<SelectionEvent>(replay = 0)
val selectionEvents: SharedFlow<SelectionEvent> = _selectionEvents.asSharedFlow()

// Emit event instead of calling lambda
fun selectClient(client: LClient) {
    viewModelScope.launch {
        _selectionEvents.emit(SelectionEvent.ClientSelected(client))
    }
}

// Collect in Fragment
viewLifecycleOwner.lifecycleScope.launch {
    viewModel.selectionEvents.collect { event ->
        when (event) {
            is SelectionEvent.ClientSelected -> handleClientSelected(event.client)
            is SelectionEvent.ProductSelected -> handleProductSelected(event.product)
        }
    }
}
```

**Problem: Missing onCleared() in ViewModels**

**Solution: Add Cleanup**
```kotlin
class OrderViewModel @Inject constructor(...) : ViewModel() {

    private var imageLoadingJob: Job? = null

    fun loadImage(url: String) {
        imageLoadingJob?.cancel()
        imageLoadingJob = viewModelScope.launch {
            // Load image
        }
    }

    override fun onCleared() {
        super.onCleared()
        imageLoadingJob?.cancel()
        // Clear any other resources
    }
}
```

**Problem: Activity Context References (MainActivity.kt)**

**Current:**
```kotlin
private var currentFragment: Fragment? = null
```

**Solution: Use WeakReference or Don't Store**
```kotlin
// Option 1: WeakReference
private var currentFragment: WeakReference<Fragment>? = null

fun setCurrentFragment(fragment: Fragment) {
    currentFragment = WeakReference(fragment)
}

// Option 2: Get from NavController when needed
private fun getCurrentFragment(): Fragment? {
    return supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
        ?.childFragmentManager
        ?.fragments
        ?.firstOrNull()
}
```

### 6.3 Image Loading Optimization

**Problem: Unlimited Cache (SharedViewModel.kt lines 298-315)**

**Current:**
```kotlin
imager.load(glideUrl)
    .diskCacheStrategy(DiskCacheStrategy.ALL)
```

**Issues:**
- `DiskCacheStrategy.ALL` caches both original and transformed - doubles storage
- No memory cache size limit
- No image compression

**Solution: Optimized Glide Configuration**
```kotlin
// AppGlideModule
@GlideModule
class AgentVentaGlideModule : AppGlideModule() {

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // Memory cache: 10% of available memory
        val memoryCacheSizeBytes = 1024 * 1024 * 20 // 20MB
        builder.setMemoryCache(LruResourceCache(memoryCacheSizeBytes.toLong()))

        // Disk cache: 100MB max
        val diskCacheSizeBytes = 1024 * 1024 * 100
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, diskCacheSizeBytes.toLong()))

        // Log level
        builder.setLogLevel(Log.ERROR)
    }

    override fun isManifestParsingEnabled(): Boolean {
        return false
    }
}

// Usage in ViewModel/Repository
class ImageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val glideRequestManager: RequestManager
) {

    fun loadImage(url: String, imageView: ImageView) {
        glideRequestManager
            .load(url)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)  // Only transformed
            .override(800, 800)  // Max dimensions
            .placeholder(R.drawable.placeholder)
            .error(R.drawable.error_image)
            .into(imageView)
    }

    suspend fun compressAndEncodeImage(file: File): String {
        return withContext(Dispatchers.IO) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)

            // Calculate scaled dimensions
            val maxDimension = 1024
            val scale = min(
                maxDimension.toFloat() / bitmap.width,
                maxDimension.toFloat() / bitmap.height
            )

            if (scale < 1.0f) {
                val scaledWidth = (bitmap.width * scale).toInt()
                val scaledHeight = (bitmap.height * scale).toInt()
                val scaledBitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    scaledWidth,
                    scaledHeight,
                    true
                )

                // Compress to JPEG
                val outputStream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                val bytes = outputStream.toByteArray()

                scaledBitmap.recycle()
                bitmap.recycle()

                Base64.encodeToString(bytes, Base64.DEFAULT)
            } else {
                // Original is small enough
                val bytes = file.readBytes()
                Base64.encodeToString(bytes, Base64.DEFAULT)
            }
        }
    }
}
```

**Problem: Synchronous File Operations in ViewModel (SharedViewModel.kt lines 322-350)**

**Solution: Move to Repository Layer**
```kotlin
class FilesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : FilesRepository {

    override suspend fun loadClientImage(imageGuid: String): Result<File> {
        return withContext(ioDispatcher) {
            try {
                val imageFile = File(context.cacheDir, "$imageGuid.jpg")
                if (imageFile.exists()) {
                    Result.Success(imageFile)
                } else {
                    Result.Error(
                        DomainException.BusinessLogicError("Image file not found")
                    )
                }
            } catch (e: IOException) {
                Result.Error(DomainException.BusinessLogicError("Failed to load image: ${e.message}"))
            }
        }
    }
}
```

---

## 7. Security Improvements

### 7.1 Encrypted Data Storage

**Problem: Unencrypted SharedPreferences and Database**

**Solution: Use AndroidX Security Library**

**Add Dependency:**
```gradle
implementation "androidx.security:security-crypto:1.1.0-alpha06"
```

**Encrypted SharedPreferences:**
```kotlin
// In GlobalModule
@Provides
@Singleton
fun provideEncryptedSharedPreferences(
    @ApplicationContext context: Context
): SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    return EncryptedSharedPreferences.create(
        context,
        "agent_venta_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}
```

**Encrypted Database:**
```kotlin
// Add dependency
implementation "net.zetetic:android-database-sqlcipher:4.5.4"
implementation "androidx.sqlite:sqlite-ktx:2.4.0"

// In GlobalModule
@Provides
@Singleton
fun provideAppDatabase(
    @ApplicationContext context: Context
): AppDatabase {
    val passphrase = getOrCreateDatabasePassphrase(context)
    val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase))

    return Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "agent_venta_db"
    )
        .openHelperFactory(factory)
        .addMigrations(*MIGRATIONS)
        .build()
}

private fun getOrCreateDatabasePassphrase(context: Context): CharArray {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "db_passphrase_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var passphrase = encryptedPrefs.getString("db_passphrase", null)
    if (passphrase == null) {
        // Generate new passphrase
        passphrase = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        encryptedPrefs.edit()
            .putString("db_passphrase", passphrase)
            .apply()
    }

    return passphrase.toCharArray()
}
```

### 7.2 Network Security Configuration

**Create network_security_config.xml:**
```xml
<!-- res/xml/network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- Production configuration -->
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>

    <!-- Certificate pinning for API endpoints -->
    <domain-config>
        <domain includeSubdomains="true">programmer.com.ua</domain>
        <pin-set expiration="2026-01-01">
            <!-- Add your certificate pins here -->
            <pin digest="SHA-256">base64_encoded_pin_1</pin>
            <pin digest="SHA-256">base64_encoded_pin_2</pin>
        </pin-set>
    </domain-config>

    <!-- Debug configuration (localhost, test servers) -->
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">localhost</domain>
        <domain includeSubdomains="true">10.0.2.2</domain>
        <domain includeSubdomains="true">hoot.com.ua</domain>
    </domain-config>
</network-security-config>
```

**Reference in AndroidManifest.xml:**
```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ... >
```

### 7.3 Secure Credential Storage

**Problem: Credentials in Memory (HttpAuthInterceptor.kt)**

**Current:**
```kotlin
fun setCredentials(user: String, pass: String) {
    credentials = okhttp3.Credentials.basic(user, pass, Charsets.UTF_8)
}
```

**Solution: Android Keystore**
```kotlin
class SecureCredentialStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private val keyAlias = "agent_venta_credentials"

    init {
        if (!keyStore.containsAlias(keyAlias)) {
            createKey()
        }
    }

    private fun createKey() {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
        )

        keyGenerator.generateKey()
    }

    fun encryptCredentials(username: String, password: String): EncryptedCredentials {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = keyStore.getKey(keyAlias, null) as SecretKey
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val credentials = "$username:$password"
        val encryptedBytes = cipher.doFinal(credentials.toByteArray())
        val iv = cipher.iv

        return EncryptedCredentials(
            encryptedData = Base64.encodeToString(encryptedBytes, Base64.DEFAULT),
            iv = Base64.encodeToString(iv, Base64.DEFAULT)
        )
    }

    fun decryptCredentials(encrypted: EncryptedCredentials): Pair<String, String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = keyStore.getKey(keyAlias, null) as SecretKey

        val iv = Base64.decode(encrypted.iv, Base64.DEFAULT)
        val encryptedData = Base64.decode(encrypted.encryptedData, Base64.DEFAULT)

        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val decryptedBytes = cipher.doFinal(encryptedData)
        val credentials = String(decryptedBytes)

        val parts = credentials.split(":")
        return Pair(parts[0], parts[1])
    }
}

data class EncryptedCredentials(
    val encryptedData: String,
    val iv: String
)
```

### 7.4 Remove Hardcoded Secrets

**Problem: BuildConfig References (SharedViewModel.kt lines 249-257)**

**Actions:**
1. Remove commented code with credentials
2. Audit git history: `git log -p --all -S "FIREBASE_PASSWORD"`
3. Rotate exposed credentials
4. Use environment variables in CI/CD

**local.properties (gitignored):**
```properties
FIREBASE_API_KEY=your_key_here
LICENSE_API_KEY=your_key_here
```

**build.gradle:**
```gradle
android {
    defaultConfig {
        // Load from local.properties
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())

        buildConfigField("String", "FIREBASE_API_KEY", "\"${properties.getProperty("FIREBASE_API_KEY", "")}\"")
        buildConfigField("String", "LICENSE_API_KEY", "\"${properties.getProperty("LICENSE_API_KEY", "")}\"")
    }
}
```

---

## 8. Code Organization Refactoring

### 8.1 Clean Architecture Package Structure

**Current Structure Problems:**
- Mixed concerns in `dao` package
- Repository implementations in wrong location
- UI models mixed with database entities

**Recommended Structure:**
```
/app/src/main/java/ua/com/programmer/agentventa/
├── /data                           # Data layer
│   ├── /local                      # Local data sources
│   │   ├── /dao                    # Room DAOs only
│   │   │   ├── OrderDao.kt
│   │   │   ├── ClientDao.kt
│   │   │   └── ...
│   │   ├── /entity                 # Room entities only
│   │   │   ├── Order.kt
│   │   │   ├── Client.kt
│   │   │   └── ...
│   │   └── AppDatabase.kt
│   ├── /remote                     # Remote data sources
│   │   ├── /api                    # Retrofit interfaces
│   │   │   └── HttpClientApi.kt
│   │   ├── /dto                    # Network DTOs
│   │   │   ├── OrderDto.kt
│   │   │   └── ...
│   │   └── /interceptor            # OkHttp interceptors
│   │       ├── HttpAuthInterceptor.kt
│   │       └── TokenRefresh.kt
│   ├── /repository                 # Repository implementations
│   │   ├── OrderRepositoryImpl.kt
│   │   ├── ClientRepositoryImpl.kt
│   │   ├── NetworkRepositoryImpl.kt
│   │   └── ...
│   └── /mapper                     # Data mappers
│       ├── OrderMapper.kt
│       └── ...
├── /domain                         # Domain/Business logic layer
│   ├── /model                      # Domain models
│   │   ├── OrderDomain.kt
│   │   ├── ClientDomain.kt
│   │   └── ...
│   ├── /repository                 # Repository interfaces
│   │   ├── OrderRepository.kt
│   │   ├── ClientRepository.kt
│   │   └── ...
│   ├── /usecase                    # Use cases/Interactors
│   │   ├── /order
│   │   │   ├── GetOrdersUseCase.kt
│   │   │   ├── SaveOrderUseCase.kt
│   │   │   ├── DeleteOrderUseCase.kt
│   │   │   └── ValidateOrderUseCase.kt
│   │   ├── /sync
│   │   │   ├── SyncDataUseCase.kt
│   │   │   └── RefreshTokenUseCase.kt
│   │   └── ...
│   └── /error                      # Error definitions
│       └── DomainException.kt
├── /presentation                   # Presentation layer
│   ├── /features                   # Feature modules
│   │   ├── /orders
│   │   │   ├── /list
│   │   │   │   ├── OrderListFragment.kt
│   │   │   │   ├── OrderListViewModel.kt
│   │   │   │   ├── OrderListAdapter.kt
│   │   │   │   └── OrderListUiState.kt
│   │   │   └── /detail
│   │   │       ├── OrderDetailFragment.kt
│   │   │       ├── OrderDetailViewModel.kt
│   │   │       └── OrderDetailUiState.kt
│   │   ├── /clients
│   │   │   ├── /list
│   │   │   └── /detail
│   │   ├── /products
│   │   ├── /sync
│   │   └── /settings
│   ├── /common                     # Shared UI components
│   │   ├── /adapter
│   │   │   └── BaseListAdapter.kt
│   │   ├── /viewmodel
│   │   │   └── BaseViewModel.kt
│   │   └── /dialog
│   │       └── ErrorDialog.kt
│   ├── /navigation                 # Navigation setup
│   └── MainActivity.kt
├── /di                             # Dependency Injection
│   ├── AppModule.kt
│   ├── DataModule.kt
│   ├── DomainModule.kt
│   └── PresentationModule.kt
└── /util                           # Utilities
    ├── /extension                  # Extension functions
    │   ├── DateExtensions.kt
    │   ├── StringExtensions.kt
    │   └── ...
    ├── /formatter
    │   ├── DateFormatter.kt
    │   └── CurrencyFormatter.kt
    └── Constants.kt
```

### 8.2 Migration Plan

**Phase 1: Create New Structure (Week 1)**
1. Create new package hierarchy
2. Move utility classes first
3. Update imports

**Phase 2: Migrate Data Layer (Week 2-3)**
1. Move DAOs to `/data/local/dao/`
2. Move entities to `/data/local/entity/`
3. Create DTOs in `/data/remote/dto/`
4. Move repository implementations to `/data/repository/`
5. Create data mappers

**Phase 3: Create Domain Layer (Week 3-4)**
1. Define domain models
2. Create repository interfaces in domain
3. Implement use cases
4. Define domain exceptions

**Phase 4: Refactor Presentation (Week 4-5)**
1. Organize features by module
2. Create UI state classes
3. Refactor ViewModels to use use cases
4. Extract common UI components

**Phase 5: Update DI (Week 5)**
1. Reorganize Hilt modules
2. Update bindings
3. Test all injection points

### 8.3 Naming Convention Standardization

**Current Issues:**
- Generic names: `ListViewModel.kt`, `Adapter.kt`
- Inconsistent prefixes: `LClient`, `LProduct`

**Standards:**

**ViewModels:**
- List screens: `{Feature}ListViewModel` (e.g., `OrderListViewModel`)
- Detail screens: `{Feature}DetailViewModel` or `{Feature}ViewModel`
- Shared: `{Purpose}ViewModel` (e.g., `AccountStateViewModel`)

**Fragments:**
- `{Feature}{Purpose}Fragment` (e.g., `OrderListFragment`, `OrderDetailFragment`)

**Adapters:**
- `{Feature}ListAdapter` (e.g., `OrderListAdapter`)

**Models:**
- Entities: `{Name}Entity` (e.g., `OrderEntity`)
- DTOs: `{Name}Dto` (e.g., `OrderDto`)
- Domain: `{Name}` (e.g., `Order`)
- UI: `{Name}UiModel` or `{Name}UiState`

**Refactoring Example:**
```kotlin
// Before
data class LClient(...)          // Unclear prefix

// After
data class ClientEntity(...)     // Database entity
data class ClientDto(...)        // Network DTO
data class Client(...)           // Domain model
data class ClientUiModel(...)    // UI model
```

---

## 9. Implementation Priorities

### Phase 1: Critical Fixes (2-3 weeks)

**Security & Stability:**
1. ✅ Implement EncryptedSharedPreferences for credentials
2. ✅ Add database encryption with SQLCipher
3. ✅ Remove `runBlocking` from NetworkRepositoryImpl
4. ✅ Add network security config
5. ✅ Implement proper error handling in all ViewModels

**Estimated Effort:** 15-20 hours

### Phase 2: Architecture Improvements (3-4 weeks)

**Modern Patterns:**
1. ✅ Migrate LiveData to StateFlow/SharedFlow
2. ✅ Implement domain layer with use cases
3. ✅ Extract SharedViewModel into focused components
4. ✅ Refactor NetworkRepositoryImpl
5. ✅ Create Result wrapper and error types

**Estimated Effort:** 25-30 hours

### Phase 3: Testing Infrastructure (4-6 weeks)

**Test Coverage:**
1. ✅ Add test dependencies
2. ✅ Write unit tests for ViewModels (target: 80%)
3. ✅ Write unit tests for use cases (target: 90%)
4. ✅ Write integration tests for repositories (target: 60%)
5. ✅ Write UI tests for critical flows (target: 30%)

**Estimated Effort:** 40-50 hours

### Phase 4: Performance & Quality (3-4 weeks)

**Optimization:**
1. ✅ Add database indexes
2. ✅ Implement Paging 3 for large lists
3. ✅ Optimize image loading with compression
4. ✅ Implement FTS for search
5. ✅ Add memory leak prevention

**Estimated Effort:** 20-25 hours

### Phase 5: Code Organization (4-5 weeks)

**Clean Architecture:**
1. ✅ Reorganize package structure
2. ✅ Extract base classes to reduce duplication
3. ✅ Standardize naming conventions
4. ✅ Add comprehensive documentation
5. ✅ Remove deprecated code and TODOs

**Estimated Effort:** 30-35 hours

---

## 10. Metrics & Success Criteria

### Code Quality Metrics

**Before Refactoring:**
- Test Coverage: 0%
- Cyclomatic Complexity: High (God classes with 400+ lines)
- Code Duplication: ~25%
- Security Score: Medium Risk
- Performance: ANR risk from blocking calls

**After Refactoring (Target):**
- Test Coverage: 70%+
- Cyclomatic Complexity: Low (classes < 200 lines)
- Code Duplication: < 10%
- Security Score: Low Risk
- Performance: No blocking calls

### Validation Checklist

**Phase 1 Complete:**
- [ ] All credentials stored in EncryptedSharedPreferences
- [ ] Database encrypted with SQLCipher
- [ ] Zero `runBlocking` calls in production code
- [ ] Network security config implemented
- [ ] All ViewModels have error handling

**Phase 2 Complete:**
- [ ] All LiveData migrated to StateFlow/SharedFlow
- [ ] Domain layer with use cases created
- [ ] SharedViewModel split into focused components
- [ ] Result wrapper used consistently
- [ ] No direct repository calls from ViewModels

**Phase 3 Complete:**
- [ ] Unit tests for all ViewModels
- [ ] Unit tests for all use cases
- [ ] Integration tests for critical repositories
- [ ] UI tests for main user flows
- [ ] CI/CD pipeline running tests

**Phase 4 Complete:**
- [ ] All database tables have appropriate indexes
- [ ] Paging 3 implemented for large lists
- [ ] Images compressed before upload
- [ ] Search using FTS
- [ ] LeakCanary shows no leaks

**Phase 5 Complete:**
- [ ] Clean architecture package structure
- [ ] Base classes reduce duplication
- [ ] All classes follow naming conventions
- [ ] KDoc on all public APIs
- [ ] Zero TODO comments

---

## 11. Conclusion

The AgentVenta application has a solid foundation with modern Android technologies but suffers from significant technical debt that impacts security, performance, and maintainability.

**Key Takeaways:**

1. **Security is the top priority** - Unencrypted storage of sensitive data and blocking calls pose immediate risks

2. **Testing is non-existent** - Zero test coverage makes refactoring risky and regression likely

3. **Architecture needs modernization** - Heavy LiveData usage, lack of domain layer, and God classes hinder maintainability

4. **Performance can be significantly improved** - Database optimization, proper coroutine usage, and image handling will enhance UX

5. **Code organization requires restructuring** - Clean architecture will make the codebase more navigable and reduce onboarding time

**Recommended Approach:**
- Start with Phase 1 (security) immediately
- Run Phases 2-3 in parallel (architecture + testing)
- Complete Phases 4-5 iteratively

**Total Estimated Effort:** 130-160 hours (16-20 working days for 1 developer)

**ROI:**
- Reduced bug count and crash rate
- Faster feature development
- Easier onboarding for new developers
- Improved app performance and user satisfaction
- Better security posture and compliance

This refactoring plan provides a clear roadmap to transform AgentVenta from a functional but debt-laden application to a modern, maintainable, and secure codebase following Android best practices.

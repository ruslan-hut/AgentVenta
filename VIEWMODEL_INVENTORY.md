# ViewModel Inventory

Complete list of all ViewModels in the AgentVenta project, organized by category.

**Total ViewModels**: 30

---

## Document ViewModels (10)

### Base Classes
1. **DocumentViewModel.kt** - Base class for document management
   - `documents/common/DocumentViewModel.kt`
   - Generic type parameter `<T : Document>`

2. **DocumentListViewModel.kt** - Base class for document lists
   - `documents/common/DocumentListViewModel.kt`

### Order Management (3)
3. **OrderViewModel.kt** - Order document editing
   - `documents/order/OrderViewModel.kt`
   - **Complexity**: Very High
   - **Dependencies**: OrderRepository, ProductRepository, 3 Use Cases, Logger

4. **OrderListViewModel.kt** - Order list display
   - `documents/order/OrderListViewModel.kt`
   - **Complexity**: Medium

### Cash Management (2)
5. **CashViewModel.kt** - Cash receipt editing
   - `documents/cash/CashViewModel.kt`
   - **Complexity**: Medium

6. **CashListViewModel.kt** - Cash receipt list
   - `documents/cash/CashListViewModel.kt`
   - **Complexity**: Medium

### Task Management (2)
7. **TaskViewModel.kt** - Task document editing
   - `documents/task/TaskViewModel.kt`
   - **Complexity**: Medium

8. **TaskListViewModel.kt** - Task list display
   - `documents/task/TaskListViewModel.kt`
   - **Complexity**: Medium

---

## Shared ViewModels (2)

9. **SharedViewModel.kt** - Cross-fragment state sharing
   - `shared/SharedViewModel.kt`
   - **Complexity**: Very High
   - **Dependencies**: FilesRepository, Logger, ImageLoadingManager, OrderRepository, CommonRepository, SharedPreferences, AccountStateManager, SyncManager
   - **Key Features**: Account state, barcode handling, shared parameters, image loading, action callbacks, sync state

10. **AccountStateViewModel.kt** - Account state management
    - `shared/AccountStateViewModel.kt`
    - **Complexity**: High

---

## Catalog ViewModels - Clients (4)

11. **ClientViewModel.kt** - Client details view
    - `catalogs/client/ClientViewModel.kt`
    - **Complexity**: Medium
    - **Features**: Client info, debts, location

12. **ClientListViewModel.kt** - Client catalog browsing
    - `catalogs/client/ClientListViewModel.kt`
    - **Complexity**: Medium
    - **Features**: Search, filtering, pagination

13. **ClientImageViewModel.kt** - Client photo management
    - `catalogs/client/ClientImageViewModel.kt`
    - **Complexity**: Low
    - **Features**: Camera capture, image upload, deletion

14. **ClientsMapViewModel.kt** - Client map visualization
    - `catalogs/map/clients/ClientsMapViewModel.kt`
    - **Complexity**: High
    - **Features**: Map markers, clustering, location data

---

## Catalog ViewModels - Products (3)

15. **ProductViewModel.kt** - Product details view
    - `catalogs/product/ProductViewModel.kt`
    - **Complexity**: Medium
    - **Features**: Product info, prices, stock levels

16. **ProductListViewModel.kt** - Product catalog browsing
    - `catalogs/product/ProductListViewModel.kt`
    - **Complexity**: High
    - **Features**: Groups, search, filtering, sorting, stock filtering

17. **ProductImageViewModel.kt** - Product image display
    - `catalogs/product/ProductImageViewModel.kt`
    - **Complexity**: Low

---

## Catalog ViewModels - Other (3)

18. **DebtViewModel.kt** - Client debt details
    - `catalogs/debt/DebtViewModel.kt`
    - **Complexity**: Medium

19. **Company ListViewModel.kt** - Company selection
    - `catalogs/company/ListViewModel.kt`
    - **Complexity**: Low

20. **Store ListViewModel.kt** - Store selection
    - `catalogs/store/ListViewModel.kt`
    - **Complexity**: Low

---

## Settings ViewModels (4)

21. **SyncViewModel.kt** - Data synchronization
    - `settings/SyncViewModel.kt`
    - **Complexity**: Low (just state holder)
    - **Note**: Very simple, only holds account reference

22. **UserAccountViewModel.kt** - Account editing
    - `settings/UserAccountViewModel.kt`
    - **Complexity**: Medium
    - **Features**: CRUD operations, validation

23. **UserAccountListViewModel.kt** - Account list
    - `settings/UserAccountListViewModel.kt`
    - **Complexity**: Medium

24. **OptionsViewModel.kt** - App settings
    - `settings/OptionsViewModel.kt`
    - **Complexity**: Low

---

## Location ViewModels (3)

25. **LocationHistoryViewModel.kt** - GPS tracking history
    - `catalogs/map/history/LocationHistoryViewModel.kt`
    - **Complexity**: Medium
    - **Features**: Location list, map display, filtering

26. **LocationPickupViewModel.kt** - Manual location selection
    - `catalogs/locations/pickup/LocationPickupViewModel.kt`
    - **Complexity**: Medium
    - **Features**: Map picker, geocoding

---

## Utility ViewModels (4)

27. **PickerViewModel.kt** - Generic picker component
    - `catalogs/picker/PickerViewModel.kt`
    - **Complexity**: Low
    - **Features**: Generic selection dialog

28. **FiscalViewModel.kt** - Fiscal receipt integration
    - `fiscal/FiscalViewModel.kt`
    - **Complexity**: High
    - **Features**: Checkbox PRRO integration, receipt generation

29. **PrinterViewModel.kt** - Bluetooth printer
    - `printer/PrinterViewModel.kt`
    - **Complexity**: Medium
    - **Features**: Printer discovery, connection, printing

30. **LogViewModel.kt** - Application logs
    - `catalogs/logger/LogViewModel.kt`
    - **Complexity**: Low
    - **Features**: Log viewing, filtering

---

## Complexity Distribution

### Very High (2)
- OrderViewModel
- SharedViewModel

### High (4)
- AccountStateViewModel
- ProductListViewModel
- ClientsMapViewModel
- FiscalViewModel

### Medium (14)
- DocumentViewModel, DocumentListViewModel
- OrderListViewModel
- CashViewModel, CashListViewModel
- TaskViewModel, TaskListViewModel
- ClientViewModel, ClientListViewModel
- ProductViewModel
- DebtViewModel
- UserAccountViewModel, UserAccountListViewModel
- LocationHistoryViewModel, LocationPickupViewModel
- PrinterViewModel

### Low (10)
- ClientImageViewModel
- ProductImageViewModel
- Company ListViewModel
- Store ListViewModel
- SyncViewModel
- OptionsViewModel
- PickerViewModel
- LogViewModel

---

## Testing Priority Matrix

### Critical (Must Test First)
- DocumentViewModel (base class)
- OrderViewModel (most complex business logic)
- SharedViewModel (shared state)
- AccountStateViewModel (core infrastructure)

### High Priority
- All List ViewModels (DocumentList, OrderList, CashList, TaskList)
- CashViewModel, TaskViewModel
- ClientViewModel, ProductViewModel
- UserAccountViewModel

### Medium Priority
- ClientListViewModel, ProductListViewModel
- FiscalViewModel
- LocationHistoryViewModel
- DebtViewModel

### Low Priority
- Image ViewModels
- Picker ViewModels
- Settings ViewModels (simple state holders)
- LogViewModel

---

## Common Patterns

### Pattern 1: Repository + Logger
Most ViewModels follow this pattern:
```kotlin
@HiltViewModel
class SomeViewModel @Inject constructor(
    private val repository: SomeRepository,
    private val logger: Logger
) : ViewModel()
```

### Pattern 2: DocumentViewModel Inheritance
Document-based ViewModels extend base:
```kotlin
@HiltViewModel
class OrderViewModel @Inject constructor(
    orderRepository: OrderRepository,
    // ... other deps
    logger: Logger
) : DocumentViewModel<Order>(
    repository = orderRepository,
    logger = logger,
    logTag = "OrderVM",
    emptyDocument = { Order(guid = "") }
)
```

### Pattern 3: StateFlow + LiveData Dual Exposure
For backward compatibility:
```kotlin
private val _state = MutableStateFlow(InitialState)
val stateFlow: StateFlow<State> = _state.asStateFlow()
val state: LiveData<State> = _state.asLiveData()
```

### Pattern 4: FlatMapLatest for Reactive Queries
```kotlin
private val _documentGuid = MutableStateFlow("")
val document: StateFlow<Document> = _documentGuid
    .flatMapLatest { guid -> repository.getDocument(guid) }
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyDocument)
```

---

## Dependencies Summary

### Most Common Dependencies
1. **Repository** (all ViewModels)
2. **Logger** (all ViewModels)
3. **UserAccountRepository** (indirect via repositories)
4. **SharedPreferences** (SharedViewModel, settings)
5. **Use Cases** (OrderViewModel, domain logic)

### Specialized Dependencies
- **ImageLoadingManager** - SharedViewModel
- **AccountStateManager** - SharedViewModel
- **SyncManager** - SharedViewModel
- **GeocodeHelper** - Location ViewModels
- **CheckboxApi** - FiscalViewModel
- **BluetoothManager** - PrinterViewModel

---

## Test File Structure

Recommended structure for test files:

```
app/src/test/java/ua/com/programmer/agentventa/
├── util/
│   ├── MainDispatcherRule.kt ✅
│   ├── MainDispatcherRuleTest.kt ✅
│   ├── FlowTestExtensions.kt
│   └── LiveDataTestExtensions.kt
├── fake/
│   ├── FakeUserAccountRepository.kt
│   ├── FakeOrderRepository.kt
│   ├── FakeClientRepository.kt
│   ├── FakeProductRepository.kt
│   └── ...
├── fixtures/
│   └── TestFixtures.kt
├── documents/
│   ├── common/
│   │   ├── DocumentViewModelTest.kt
│   │   └── DocumentListViewModelTest.kt
│   ├── order/
│   │   ├── OrderViewModelTest.kt
│   │   └── OrderListViewModelTest.kt
│   ├── cash/
│   │   ├── CashViewModelTest.kt
│   │   └── CashListViewModelTest.kt
│   └── task/
│       ├── TaskViewModelTest.kt
│       └── TaskListViewModelTest.kt
├── shared/
│   ├── SharedViewModelTest.kt
│   └── AccountStateViewModelTest.kt
├── catalogs/
│   ├── client/
│   │   ├── ClientViewModelTest.kt
│   │   └── ClientListViewModelTest.kt
│   └── product/
│       ├── ProductViewModelTest.kt
│       └── ProductListViewModelTest.kt
└── settings/
    ├── SyncViewModelTest.kt
    ├── UserAccountViewModelTest.kt
    └── UserAccountListViewModelTest.kt
```

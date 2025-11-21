# ViewModel Testing Plan

## Overview
This document outlines the comprehensive testing strategy for all ViewModels in the AgentVenta application. Tests will ensure proper state management, business logic execution, error handling, and Flow/LiveData emissions.

## Testing Infrastructure

### Core Components
- **MainDispatcherRule**: JUnit rule for coroutine testing ✅ (Completed)
- **Test Dependencies**: JUnit, Mockito-Kotlin, Turbine, Truth, Coroutines-Test ✅ (Completed)

### Required Test Utilities (To Create)
1. **Fake Repositories**: In-memory implementations of repositories for deterministic testing
2. **Test Fixtures**: Sample data (Orders, Clients, Products, UserAccounts)
3. **Test Extensions**: Helper functions for Flow/LiveData testing
4. **Mock Use Cases**: Simplified use case implementations for isolated testing

---

## Phase 1: Foundation (Priority: Critical)

### 1.1 Create Test Infrastructure
- [ ] **FakeUserAccountRepository** - Provides test account with known GUID
- [ ] **FakeOrderRepository** - In-memory order storage with Flow support
- [ ] **FakeCashRepository** - In-memory cash document storage
- [ ] **FakeTaskRepository** - In-memory task document storage
- [ ] **FakeClientRepository** - Test client data
- [ ] **FakeProductRepository** - Test product catalog
- [ ] **FakeNetworkRepository** - Simulated sync operations

### 1.2 Create Test Fixtures
- [ ] **TestFixtures.kt** - Centralized sample data
  - Sample UserAccount(s)
  - Sample Orders with content
  - Sample Clients with debts
  - Sample Products with prices
  - Sample DocumentTotals
  - Error scenarios

### 1.3 Test Utilities
- [ ] **FlowTestExtensions.kt** - Helper functions for Flow testing with Turbine
- [ ] **LiveDataTestExtensions.kt** - LiveData observation utilities
- [ ] **CoroutineTestExtensions.kt** - Coroutine testing helpers

---

## Phase 2: Base/Common ViewModels (Priority: High)

### 2.1 DocumentViewModel Tests
**File**: `DocumentViewModelTest.kt`

**Test Coverage**:
- [ ] Initial state is empty document
- [ ] loadDocument() populates currentDocument StateFlow
- [ ] saveDocument() calls repository save method
- [ ] deleteDocument() removes document and updates state
- [ ] Error handling for repository failures
- [ ] Loading state management
- [ ] Document validation integration

**Dependencies to Mock**:
- DocumentRepository (use FakeOrderRepository for concrete testing)
- Logger

### 2.2 DocumentListViewModel Tests
**File**: `DocumentListViewModelTest.kt`

**Test Coverage**:
- [ ] Documents list loads from repository
- [ ] Filter updates trigger new queries
- [ ] Date filtering works correctly
- [ ] Document totals calculation
- [ ] Empty state handling
- [ ] Error state handling
- [ ] Flow transformations (flatMapLatest)

---

## Phase 3: Document ViewModels (Priority: High)

### 3.1 OrderViewModel Tests
**File**: `OrderViewModelTest.kt`

**Complexity**: High (inherits DocumentViewModel, has use cases, multiple repositories)

**Test Coverage**:
- [ ] **State Management**
  - Initial order state
  - Price type selection updates
  - Order content flow emits correctly
  - Document totals calculation
- [ ] **Business Logic**
  - Adding products to order
  - Updating quantities
  - Client selection updates order
  - Company/Store selection
  - Price calculation with different price types
- [ ] **Use Cases Integration**
  - ValidateOrderUseCase - validation errors displayed
  - SaveOrderUseCase - successful save workflow
  - EnableOrderEditUseCase - edit mode toggles
- [ ] **Barcode Scanning**
  - Product lookup by barcode
  - Quantity increment on duplicate scan
  - Invalid barcode handling
- [ ] **Location Updates**
  - Location attached to order
  - Distance calculation to client
- [ ] **Error Scenarios**
  - Network errors during save
  - Invalid product selection
  - Missing required fields

**Dependencies**:
- FakeOrderRepository
- FakeProductRepository
- Mock ValidateOrderUseCase
- Mock SaveOrderUseCase
- Mock EnableOrderEditUseCase
- Mock Logger

### 3.2 CashViewModel Tests
**File**: `CashViewModelTest.kt`

**Complexity**: Medium

**Test Coverage**:
- [ ] Cash document creation
- [ ] Payment type selection
- [ ] Amount calculation
- [ ] Client selection
- [ ] Save/delete operations
- [ ] Validation logic

**Dependencies**:
- FakeCashRepository
- Mock Logger

### 3.3 TaskViewModel Tests
**File**: `TaskViewModelTest.kt`

**Complexity**: Medium

**Test Coverage**:
- [ ] Task document creation
- [ ] Task type selection
- [ ] Client association
- [ ] Completion status toggle
- [ ] Save/delete operations

**Dependencies**:
- FakeTaskRepository
- Mock Logger

### 3.4 List ViewModels
- [ ] **OrderListViewModelTest.kt** - Order list, filtering, date range
- [ ] **CashListViewModelTest.kt** - Cash list, filtering
- [ ] **TaskListViewModelTest.kt** - Task list, filtering

---

## Phase 4: Shared ViewModels (Priority: High)

### 4.1 SharedViewModel Tests
**File**: `SharedViewModelTest.kt`

**Complexity**: Very High (many dependencies, complex state)

**Test Coverage**:
- [ ] **Account State**
  - Current account flow updates
  - Account switching
  - Options/PriceTypes/PaymentTypes access
- [ ] **Barcode Handling**
  - Barcode emission and consumption
  - Ignore barcode mode
- [ ] **Shared Parameters**
  - Document GUID tracking
  - Price type selection
  - Company/Store selection
  - Filter text updates
- [ ] **Image Loading**
  - Client image loading
  - Product image loading
  - Authentication headers
- [ ] **Action Callbacks**
  - Client selection callback
  - Product selection callback
  - Company selection callback
  - Store selection callback
- [ ] **Sync State**
  - Sync progress monitoring
  - Error state handling

**Dependencies**:
- FakeFilesRepository
- Mock Logger
- Mock ImageLoadingManager
- FakeOrderRepository
- FakeCommonRepository
- Mock SharedPreferences
- Mock AccountStateManager
- Mock SyncManager

### 4.2 AccountStateViewModel Tests
**File**: `AccountStateViewModelTest.kt`

**Test Coverage**:
- [ ] Current account state management
- [ ] Account switching logic
- [ ] Options loading
- [ ] Price types loading
- [ ] Payment types loading

---

## Phase 5: Catalog ViewModels (Priority: Medium)

### 5.1 Client ViewModels
- [ ] **ClientViewModelTest.kt** - Client details, debt display, location
- [ ] **ClientListViewModelTest.kt** - Client list, search, filtering
- [ ] **ClientImageViewModelTest.kt** - Image capture, upload, deletion

### 5.2 Product ViewModels
- [ ] **ProductViewModelTest.kt** - Product details, prices, stock levels
- [ ] **ProductListViewModelTest.kt** - Product catalog, groups, search, sorting
- [ ] **ProductImageViewModelTest.kt** - Product image display

---

## Phase 6: Settings ViewModels (Priority: Medium)

### 6.1 Sync and Account Management
- [ ] **SyncViewModelTest.kt** - Sync state management, account tracking
- [ ] **UserAccountViewModelTest.kt** - Account CRUD operations, validation
- [ ] **UserAccountListViewModelTest.kt** - Account list, selection, deletion
- [ ] **OptionsViewModelTest.kt** - Settings management

---

## Phase 7: Specialized ViewModels (Priority: Low)

### 7.1 Location ViewModels
- [ ] **ClientsMapViewModelTest.kt** - Map markers, client locations
- [ ] **LocationHistoryViewModelTest.kt** - Location history display
- [ ] **LocationPickupViewModelTest.kt** - Manual location selection

### 7.2 Financial ViewModels
- [ ] **DebtViewModelTest.kt** - Debt calculation, history
- [ ] **FiscalViewModelTest.kt** - Fiscal receipt generation

### 7.3 Utility ViewModels
- [ ] **PickerViewModelTest.kt** - Generic picker functionality
- [ ] **PrinterViewModelTest.kt** - Bluetooth printer communication
- [ ] **LogViewModelTest.kt** - Log viewing and filtering

### 7.4 Company/Store ViewModels
- [ ] **Company ListViewModelTest.kt** - Company selection
- [ ] **Store ListViewModelTest.kt** - Store selection

---

## Testing Patterns and Best Practices

### Pattern 1: Flow Testing with Turbine
```kotlin
@Test
fun `flow emits expected values`() = runTest {
    viewModel.someFlow.test {
        assertThat(awaitItem()).isEqualTo(expectedValue)
        cancelAndIgnoreRemainingEvents()
    }
}
```

### Pattern 2: StateFlow Testing
```kotlin
@Test
fun `state updates correctly`() = runTest {
    // Arrange
    val expectedState = SomeState()

    // Act
    viewModel.updateState()

    // Assert
    assertThat(viewModel.stateFlow.value).isEqualTo(expectedState)
}
```

### Pattern 3: LiveData Testing
```kotlin
@Test
fun `livedata observes correctly`() = runTest {
    val observer = viewModel.someLiveData.test()

    viewModel.triggerAction()

    observer.assertValue(expectedValue)
}
```

### Pattern 4: Error Handling
```kotlin
@Test
fun `error state is set on repository failure`() = runTest {
    // Arrange
    fakeRepository.setError(TestException())

    // Act
    viewModel.loadData()

    // Assert
    assertThat(viewModel.errorState.value).isNotNull()
}
```

### Pattern 5: Use Case Testing
```kotlin
@Test
fun `use case success updates state correctly`() = runTest {
    // Arrange
    val mockUseCase = mock<SomeUseCase> {
        onBlocking { invoke(any()) } doReturn Result.Success(data)
    }
    val viewModel = SomeViewModel(mockUseCase)

    // Act
    viewModel.executeAction()

    // Assert
    verify(mockUseCase).invoke(expectedParam)
    assertThat(viewModel.state.value).isEqualTo(expectedState)
}
```

---

## Test Coverage Goals

### Minimum Coverage per ViewModel
- **State Management**: 100% (all StateFlow/LiveData)
- **Public Methods**: 90%
- **Error Paths**: 80%
- **Edge Cases**: 70%

### Priority Classification
- **Critical**: Document ViewModels, SharedViewModel (core business logic)
- **High**: Account/Sync ViewModels, Catalog ViewModels
- **Medium**: Settings ViewModels
- **Low**: Specialized/Utility ViewModels

---

## Execution Strategy

### Week 1: Foundation
1. Create all fake repositories
2. Create test fixtures
3. Set up test utilities
4. Write DocumentViewModel tests (base class)

### Week 2: Core Documents
1. OrderViewModel tests
2. CashViewModel tests
3. TaskViewModel tests
4. List ViewModel tests

### Week 3: Shared and Catalogs
1. SharedViewModel tests
2. ClientViewModel tests
3. ProductViewModel tests
4. List ViewModel tests

### Week 4: Settings and Specialized
1. Settings ViewModels
2. Location ViewModels
3. Fiscal/Printer ViewModels
4. Remaining utility ViewModels

---

## Success Criteria

- [ ] All ViewModels have test files
- [ ] All critical paths tested
- [ ] All StateFlow emissions verified
- [ ] Error handling tested
- [ ] Edge cases covered
- [ ] Test execution time < 30 seconds total
- [ ] No flaky tests
- [ ] CI/CD integration complete

---

## Notes

### Testing Challenges
1. **Complex Dependencies**: Some ViewModels have 5+ dependencies - use fakes where possible
2. **Flow Chains**: Test flatMapLatest/combine operators with Turbine
3. **SharedPreferences**: Mock to avoid Android dependencies
4. **Use Cases**: Mock for isolation, but test integration scenarios too
5. **Coroutine Scope**: Use TestScope and MainDispatcherRule

### Performance Considerations
- Keep repository fakes lightweight (in-memory)
- Use UnconfinedTestDispatcher for simple tests
- Use StandardTestDispatcher when testing delays/timing
- Avoid Thread.sleep() - use advanceTimeBy() instead

### Maintenance
- Update test fixtures when entities change
- Keep fake repositories in sync with real interfaces
- Document complex test scenarios
- Refactor common test patterns into utilities

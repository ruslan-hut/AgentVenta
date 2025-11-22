# ViewModel Testing Plan

## Overview
This document outlines the comprehensive testing strategy for all ViewModels in the AgentVenta application. Tests will ensure proper state management, business logic execution, error handling, and Flow/LiveData emissions.

## Testing Infrastructure

### Core Components
- **MainDispatcherRule**: JUnit rule for coroutine testing ✅ (Completed)
- **Test Dependencies**: JUnit, Mockito-Kotlin, Turbine, Truth, Coroutines-Test ✅ (Completed)

### Required Test Utilities ✅ (COMPLETED)
1. **Fake Repositories**: In-memory implementations of repositories for deterministic testing ✅
2. **Test Fixtures**: Sample data (Orders, Clients, Products, UserAccounts) ✅
3. **Test Extensions**: Helper functions for Flow/LiveData testing ✅
4. **Mock Use Cases**: Simplified use case implementations for isolated testing (TBD as needed)

---

## Phase 1: Foundation (Priority: Critical) ✅ COMPLETED

### 1.1 Create Test Infrastructure ✅
- ✅ **FakeUserAccountRepository** - Provides test account with known GUID (3.6KB, 100+ lines)
- ✅ **FakeOrderRepository** - In-memory order storage with Flow support (10.8KB, 300+ lines)
- ✅ **FakeCashRepository** - In-memory cash document storage (4.7KB, 150+ lines)
- ✅ **FakeTaskRepository** - In-memory task document storage (3.5KB, 100+ lines)
- ✅ **FakeClientRepository** - Test client data (4.0KB, 120+ lines)
- ✅ **FakeProductRepository** - Test product catalog (4.8KB, 150+ lines)
- ✅ **FakeNetworkRepository** - Simulated sync operations (4.3KB, 130+ lines)

### 1.2 Create Test Fixtures ✅
- ✅ **TestFixtures.kt** - Centralized sample data (737 lines)
  - Sample UserAccount(s) ✅
  - Sample Orders with content ✅
  - Sample Clients with debts ✅
  - Sample Products with prices ✅
  - Sample DocumentTotals ✅
  - Error scenarios ✅

### 1.3 Test Utilities ✅
- ✅ **FlowTestExtensions.kt** - Helper functions for Flow testing with Turbine (379 lines)
- ✅ **LiveDataTestExtensions.kt** - LiveData observation utilities (427 lines)
- ✅ **CoroutineTestExtensions.kt** - Coroutine testing helpers (461 lines)

---

## Phase 2: Base/Common ViewModels (Priority: High) ✅ COMPLETED

### 2.1 DocumentViewModel Tests ✅
**File**: `DocumentViewModelTest.kt` (635 lines, 35 test cases)

**Test Coverage**:
- ✅ Initial state is empty document (5 tests)
- ✅ loadDocument() populates currentDocument StateFlow (6 tests)
- ✅ saveDocument() calls repository save method (6 tests)
- ✅ deleteDocument() removes document and updates state (3 tests)
- ✅ Error handling for repository failures (4 tests)
- ✅ Loading state management (tested throughout)
- ✅ Document validation integration (6 abstract method tests)
- ✅ Cleanup tests (2 tests)
- ✅ Edge cases and reactive updates (3 tests)

**Dependencies**:
- FakeOrderRepository (concrete implementation)
- Mockito Logger

### 2.2 DocumentListViewModel Tests ✅
**File**: `DocumentListViewModelTest.kt` (694 lines, 38 test cases)

**Test Coverage**:
- ✅ Documents list loads from repository (4 tests)
- ✅ Filter updates trigger new queries (8 tests)
- ✅ Date filtering works correctly (4 tests)
- ✅ Document totals calculation (6 tests)
- ✅ Empty state handling (included in edge cases)
- ✅ Error state handling (tested throughout)
- ✅ Flow transformations (flatMapLatest) (2 tests)
- ✅ UI State management (10 tests)
- ✅ Current account integration (2 tests)
- ✅ Edge cases (3 tests)

**Phase 2 Total**: 73 test cases, 1,329 lines of test code

---

## Phase 3: Document ViewModels (Priority: High) ✅ COMPLETED

### 3.1 OrderViewModel Tests ✅
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

### 3.2 CashViewModel Tests ✅
**File**: `CashViewModelTest.kt` (750 lines, 36 test cases)

**Complexity**: Medium

**Test Coverage**:
- ✅ Cash document creation (3 tests)
- ✅ Amount calculation and editing (4 tests)
- ✅ Fiscal flag handling (2 tests)
- ✅ Client selection (2 tests)
- ✅ Notes editing with flag reset (1 test)
- ✅ Save/delete operations (5 tests)
- ✅ Validation logic (4 tests)
- ✅ Enable edit mode (3 tests)
- ✅ Abstract method implementations (2 tests)
- ✅ Edge cases and error handling (10 tests)

**Dependencies**:
- FakeCashRepository
- Real Use Cases (ValidateCashUseCase, SaveCashUseCase, EnableCashEditUseCase)
- Mock Logger

### 3.3 TaskViewModel Tests ✅
**File**: `TaskViewModelTest.kt` (700 lines, 35 test cases)

**Complexity**: Medium

**Test Coverage**:
- ✅ Task document creation (3 tests)
- ✅ Task loading and state management (4 tests)
- ✅ Description and notes editing (4 tests)
- ✅ Completion status toggle (4 tests)
- ✅ Validation logic (4 tests)
- ✅ Save/delete operations (6 tests)
- ✅ Abstract method implementations (3 tests)
- ✅ Edge cases and error handling (10 tests)

**Dependencies**:
- FakeTaskRepository ✅
- Real Use Cases (ValidateTaskUseCase, SaveTaskUseCase, MarkTaskDoneUseCase) ✅
- Mock Logger ✅

### 3.4 List ViewModels ✅
- ✅ **OrderListViewModelTest.kt** - Order list, filtering, date range, copy order (750 lines, 42 test cases)
- ✅ **CashListViewModelTest.kt** - Cash list, filtering, fiscal documents (650 lines, 38 test cases)
- ✅ **TaskListViewModelTest.kt** - Task list, filtering, done/not done status (700 lines, 40 test cases)

**Phase 3 Total**: 192 test cases, ~4,550 lines of test code

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

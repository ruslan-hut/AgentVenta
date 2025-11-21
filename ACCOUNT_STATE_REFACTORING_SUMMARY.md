# AccountStateManager Extraction Summary

## Overview
Extracted account state management from SharedViewModel into dedicated components following Single Responsibility Principle (SRP) with StateFlow for reactive state.

## Files Created

### 1. `AccountStateManager.kt` (Singleton)
**Location:** `/app/src/main/java/ua/com/programmer/agentventa/shared/AccountStateManager.kt`

Centralized account state management:
- **StateFlows** for reactive state:
  - `currentAccount: StateFlow<UserAccount>`
  - `options: StateFlow<UserOptions>`
  - `priceTypes: StateFlow<List<PriceType>>`
  - `paymentTypes: StateFlow<List<PaymentType>>`
  - `companies: StateFlow<List<Company>>`
  - `stores: StateFlow<List<Store>>`
  - `defaultCompany: StateFlow<Company>`
  - `defaultStore: StateFlow<Store>`

- **Responsibilities:**
  - Observes `UserAccountRepository.currentAccount`
  - Loads account-specific data (price types, payment types, companies, stores)
  - Sets up demo account if needed
  - Sends user info to license manager
  - Sets Firebase Crashlytics user ID
  - Provides listener mechanism for account changes

### 2. `AccountStateViewModel.kt`
**Location:** `/app/src/main/java/ua/com/programmer/agentventa/shared/AccountStateViewModel.kt`

Lightweight ViewModel exposing AccountStateManager's StateFlows to UI:
- Can be used by fragments that need account state without SharedViewModel
- All state delegated to AccountStateManager singleton
- Provides lookup helper methods

## Files Modified

### `SharedViewModel.kt`
**Changes:**
- Removed direct account management code (~70 lines)
- Removed `UserAccountRepository` dependency
- Removed `LicenseManager` dependency
- Added `AccountStateManager` dependency
- Delegates to `AccountStateManager` for:
  - `options` property
  - `priceTypes` property
  - `paymentTypes` property
  - `getPriceTypeCode()`, `getPriceDescription()`, `getPaymentType()`
  - `findCompany()`, `findStore()`
  - `getCompanies()`, `getStores()`
- Simplified `init` block - uses `addAccountChangeListener()` callback
- Removed Firebase and license management code

## Architecture Benefits

### 1. Single Responsibility
- **AccountStateManager**: Account state and switching logic
- **SharedViewModel**: Cross-fragment UI state and actions
- **AccountStateViewModel**: Account state exposure for fragments

### 2. StateFlow Adoption
- Modern reactive state management
- Better null safety
- Improved lifecycle awareness
- Hot streams for shared state

### 3. Centralized Account Logic
- Single source of truth for account state
- Prevents duplicate account observation
- Consistent state across all ViewModels

### 4. Testability
- AccountStateManager can be mocked for testing
- ViewModels have fewer dependencies
- Clear interfaces for account operations

## Migration Path for Existing Code

Fragments currently using `SharedViewModel` for account state can:

1. **Continue using SharedViewModel** - Works as before via delegation
2. **Use AccountStateViewModel** - For fragments only needing account state
3. **Inject AccountStateManager** - For non-ViewModel classes

### Example Usage

```kotlin
// Option 1: Via SharedViewModel (existing code continues to work)
@HiltViewModel
class SomeViewModel @Inject constructor(
    private val sharedViewModel: SharedViewModel
) {
    val options = sharedViewModel.options // Works as before
}

// Option 2: Direct AccountStateViewModel
@HiltViewModel
class SomeFragment : Fragment() {
    private val accountState: AccountStateViewModel by viewModels()

    override fun onViewCreated(...) {
        viewLifecycleOwner.lifecycleScope.launch {
            accountState.currentAccount.collect { account ->
                // React to account changes
            }
        }
    }
}

// Option 3: Inject AccountStateManager (for services, etc.)
class SomeService @Inject constructor(
    private val accountStateManager: AccountStateManager
) {
    fun doSomething() {
        val account = accountStateManager.currentAccount.value
    }
}
```

## Code Reduction

| Component | Before | After | Reduction |
|-----------|--------|-------|-----------|
| SharedViewModel | 395 lines | 312 lines | ~83 lines |
| Account logic | Embedded | Extracted | Cleaner SRP |

## Dependencies

AccountStateManager is automatically provided by Hilt via `@Singleton @Inject constructor`.

No changes needed to GlobalModule or NetworkModule.

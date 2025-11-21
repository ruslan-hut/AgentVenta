# LiveData to StateFlow Migration Summary

## Overview
Migrated ViewModels from LiveData to StateFlow for better lifecycle handling, null safety, and modern reactive patterns. Added EventChannel for one-time UI events.

## Files Created

### `UiState.kt`
**Location:** `/app/src/main/java/ua/com/programmer/agentventa/shared/UiState.kt`

Utilities for StateFlow-based state management:

1. **`UiState<T>`** - Sealed class for loading/success/error states:
```kotlin
sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
```

2. **`EventChannel<T>`** - Channel-based one-time event emitter:
```kotlin
class EventChannel<T> {
    val flow: Flow<T>
    fun send(event: T)
    suspend fun emit(event: T)
}
```

3. **`UiEvent`** - Common UI events (toast, snackbar, navigate)

4. **`DocumentEvent`** - Document-specific events (save, delete, navigate)

## Files Modified

### `DocumentViewModel.kt`
**Changes:**
- `_documentGuid`: `MutableLiveData<String>` → `MutableStateFlow<String>`
- `document`: `LiveData<T>` → `StateFlow<T>` via `flatMapLatest`
- `saveResult`: `MutableLiveData<Boolean?>` → `EventChannel<DocumentEvent>`
- Added `isLoading: StateFlow<Boolean>`
- Added `events: Flow<DocumentEvent>` for one-time events

**Before:**
```kotlin
val document: LiveData<T> = _documentGuid.switchMap {
    repository.getDocument(it).asLiveData()
}
val saveResult = MutableLiveData<Boolean?>()
```

**After:**
```kotlin
val document: StateFlow<T> = _documentGuid
    .flatMapLatest { guid ->
        if (guid.isEmpty()) flowOf(emptyDocument())
        else repository.getDocument(guid)
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyDocument())

protected val _events = EventChannel<DocumentEvent>()
val events = _events.flow
```

### `DocumentListViewModel.kt`
**Changes:**
- All `MutableLiveData` → `MutableStateFlow`
- Added `DocumentListUiState` data class for combined UI state
- `documents`/`totals`: `LiveData` → `StateFlow` via `flatMapLatest`
- Combined filters using `combine()` operator

**New UI State:**
```kotlin
data class DocumentListUiState(
    val documentsCount: String = "-",
    val returnsCount: String = "-",
    val totalWeight: String = "0.0",
    val totalSum: String = "0.00",
    val noDataVisible: Boolean = true,
    val searchVisible: Boolean = false,
    val searchText: String = "",
    val listDate: Date? = Date()
)
```

### `OrderViewModel.kt`
**Changes:**
- `selectedPriceType`: `MutableLiveData` → `MutableStateFlow`
- `navigateToPage`: `MutableLiveData` → Uses `_events.send(DocumentEvent.NavigateToPage(page))`
- `currentContent`: `LiveData` → `StateFlow` via `flatMapLatest`

### `SharedViewModel.kt`
**Changes:**
- All state → `MutableStateFlow`
- Added `SyncEvent` sealed class for sync operation events
- Added `_syncEvents: EventChannel<SyncEvent>`
- `documentTotals`: `LiveData` → `StateFlow` via `flatMapLatest`

## Migration Patterns

### 1. Simple State
```kotlin
// Before
val myState = MutableLiveData("initial")

// After
private val _myState = MutableStateFlow("initial")
val myState: StateFlow<String> = _myState.asStateFlow()
```

### 2. Derived State (switchMap → flatMapLatest)
```kotlin
// Before
val derived = sourceId.switchMap { id ->
    repository.getData(id).asLiveData()
}

// After
val derived: StateFlow<Data> = sourceId
    .flatMapLatest { id ->
        if (id.isEmpty()) flowOf(defaultValue)
        else repository.getData(id)
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), defaultValue)
```

### 3. One-Time Events
```kotlin
// Before
val saveResult = MutableLiveData<Boolean?>()
fun save() {
    saveResult.value = true
    saveResult.value = null // Reset
}

// After
private val _events = EventChannel<MyEvent>()
val events = _events.flow
fun save() {
    _events.send(MyEvent.SaveSuccess)
}
```

### 4. Combined State (MediatorLiveData → combine)
```kotlin
// Before
private val mediator = MediatorLiveData<Params>().apply {
    addSource(filter) { value = value?.copy(filter = it) }
    addSource(date) { value = value?.copy(date = it) }
}

// After
private val filterParams = combine(filter, date) { f, d ->
    Params(f, d)
}
```

## Fragment Usage

### Collecting StateFlow
```kotlin
// In onViewCreated
viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.document.collect { doc ->
            updateUI(doc)
        }
    }
}
```

### Collecting Events
```kotlin
viewLifecycleOwner.lifecycleScope.launch {
    viewModel.events.collect { event ->
        when (event) {
            is DocumentEvent.SaveSuccess -> navigateBack()
            is DocumentEvent.SaveError -> showError(event.message)
            is DocumentEvent.NavigateToPage -> navigateToPage(event.page)
            DocumentEvent.DeleteSuccess -> navigateBack()
        }
    }
}
```

## Benefits

1. **Null Safety**: StateFlow always has a value, no null checks needed
2. **Lifecycle Awareness**: `WhileSubscribed` stops collection when view is stopped
3. **One-Time Events**: EventChannel ensures events are consumed only once
4. **Better Testing**: StateFlow is easier to test than LiveData
5. **Consistent API**: All reactive state uses Kotlin Flow
6. **No Memory Leaks**: Proper lifecycle handling with `repeatOnLifecycle`

## Key Differences

| Aspect | LiveData | StateFlow |
|--------|----------|-----------|
| Initial Value | Optional | Required |
| Null Safety | Nullable | Non-null by default |
| Lifecycle | Auto-managed | Manual with `repeatOnLifecycle` |
| Hot/Cold | Hot | Hot |
| Operators | Limited | Full Flow operators |
| Threading | Main thread | Any dispatcher |

## Gradual Migration

Existing LiveData observers continue to work. Use these extension functions for gradual migration:

```kotlin
// StateFlow to LiveData (for existing observers)
val liveData = stateFlow.asLiveData()

// LiveData to StateFlow (for new code)
val stateFlow = liveData.asFlow().stateIn(...)
```

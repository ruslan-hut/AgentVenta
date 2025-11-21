# DocumentViewModel Base Class Refactoring Summary

## Overview
Created a generic base `DocumentViewModel<T>` to eliminate duplication across Order, Cash, and Task ViewModels.

## Files Created

### `DocumentViewModel.kt`
**Location:** `/app/src/main/java/ua/com/programmer/agentventa/documents/common/DocumentViewModel.kt`

Abstract base class providing:
- **Document GUID state management** (`_documentGuid: MutableLiveData<String>`)
- **Document observable** (`document: LiveData<T>` via switchMap)
- **Current document accessor** (`currentDocument: T`)
- **Save result** (`saveResult: MutableLiveData<Boolean?>`)

**Common methods:**
- `setCurrentDocument(id: String?)` - Set document by GUID or create new
- `getGuid()` - Get current document GUID
- `initNewDocument()` - Create new document via repository
- `updateDocument(updated: T)` - Update document in database
- `updateDocumentWithResult(updated: T)` - Update with result notification
- `deleteDocument()` - Delete current document
- `onDestroy()` - Clean up on destroy

**Abstract methods (must implement):**
- `getDocumentGuid(document: T): String` - Extract GUID from entity
- `markAsProcessed(document: T): T` - Create processed version
- `enableEdit()` - Enable editing
- `isNotEditable(): Boolean` - Check edit status
- `onEditNotes(notes: String)` - Edit notes field

## Files Modified

### `CashViewModel.kt`
**Before:** 121 lines, standalone ViewModel
**After:** 71 lines, extends `DocumentViewModel<Cash>`

**Removed duplicated code:**
- `_documentGuid` declaration
- `document` switchMap
- `updateDocument()` method
- `setCurrentDocument()` method
- `initNewDocument()` method
- `deleteDocument()` method
- `onDestroy()` method

### `TaskViewModel.kt`
**Before:** 94 lines, standalone ViewModel
**After:** 49 lines, extends `DocumentViewModel<Task>`

**Removed duplicated code:**
- Same as CashViewModel

### `OrderViewModel.kt`
**Before:** 336 lines, standalone ViewModel
**After:** 269 lines, extends `DocumentViewModel<Order>`

**Removed duplicated code:**
- Common CRUD operations
- Kept order-specific methods (products, barcode, fiscal, etc.)

## Code Reduction Summary

| ViewModel | Before | After | Reduction |
|-----------|--------|-------|-----------|
| CashViewModel | 121 lines | 71 lines | -50 lines (41%) |
| TaskViewModel | 94 lines | 49 lines | -45 lines (48%) |
| OrderViewModel | 336 lines | 269 lines | -67 lines (20%) |
| **Total** | 551 lines | 389 lines | **-162 lines (29%)** |

Plus ~130 lines in base class = net reduction of ~32 lines, but with much better organization.

## Architecture Benefits

### 1. DRY Principle
- Single implementation of common CRUD operations
- No copy-paste bugs
- Easier maintenance

### 2. Consistent API
All document ViewModels share:
```kotlin
// Common interface
viewModel.setCurrentDocument(guid)
viewModel.document.observe(...)
viewModel.deleteDocument()
viewModel.enableEdit()
viewModel.isNotEditable()
viewModel.onEditNotes(notes)
viewModel.onDestroy()
```

### 3. Type Safety
Generic `DocumentViewModel<T>` ensures type-safe operations:
```kotlin
class CashViewModel : DocumentViewModel<Cash>
class OrderViewModel : DocumentViewModel<Order>
class TaskViewModel : DocumentViewModel<Task>
```

### 4. Extensibility
Easy to add new document types:
```kotlin
@HiltViewModel
class NewDocumentViewModel @Inject constructor(
    repository: NewDocumentRepository,
    logger: Logger
) : DocumentViewModel<NewDocument>(
    repository = repository,
    logger = logger,
    logTag = "NewDocVM",
    emptyDocument = { NewDocument(guid = "") }
) {
    override fun getDocumentGuid(document: NewDocument) = document.guid
    override fun markAsProcessed(document: NewDocument) = document.copy(isProcessed = 1)
    override fun enableEdit() { /* ... */ }
    override fun isNotEditable() = currentDocument.isProcessed > 0
    override fun onEditNotes(notes: String) { /* ... */ }
}
```

## Usage Pattern

```kotlin
// In Fragment
@AndroidEntryPoint
class CashFragment : Fragment() {
    private val viewModel: CashViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Common operations work the same across all document types
        viewModel.setCurrentDocument(args.documentGuid)

        viewModel.document.observe(viewLifecycleOwner) { cash ->
            // Update UI
        }

        viewModel.saveResult.observe(viewLifecycleOwner) { saved ->
            if (saved == true) findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        viewModel.onDestroy()
        super.onDestroyView()
    }
}
```

# AgentVenta Code Reorganization Plan

> Clean Architecture Migration - Step by Step
> Created: November 2024

## Overview

This plan reorganizes the codebase from the current structure to Clean Architecture layers. Each step is independent and can be completed in a single session, with verification between steps.

---

## Current Structure Analysis

```
/agentventa
├── /dao/entity/          # Room entities
├── /dao/cloud/           # Network DTOs
├── /dao/                 # Room DAOs
├── /dao/impl/            # Repository implementations
├── /repository/          # Repository interfaces
├── /domain/              # Domain layer (already structured)
├── /http/                # Network layer
├── /documents/           # Document screens (Order, Cash, Task)
├── /catalogs/            # Catalog screens (Client, Product, etc.)
├── /shared/              # Shared ViewModels and managers
├── /settings/            # Settings screens
├── /fiscal/              # Fiscal integration
├── /geo/                 # Location services
├── /camera/              # Camera functionality
├── /printer/             # Printer functionality
├── /logger/              # Logging
├── /license/             # License management
├── /extensions/          # Kotlin extensions
├── /utility/             # Utilities
└── /di/                  # Dependency injection
```

---

## Target Structure

```
/agentventa
├── /data/
│   ├── /local/
│   │   ├── /dao/         # Room DAOs (interfaces)
│   │   ├── /entity/      # Room entities
│   │   └── /database/    # AppDatabase
│   ├── /remote/
│   │   ├── /api/         # Retrofit APIs
│   │   ├── /dto/         # Network DTOs
│   │   └── /interceptor/ # Network interceptors
│   └── /repository/      # Repository implementations
│
├── /domain/
│   ├── /model/           # Domain models (if needed)
│   ├── /repository/      # Repository interfaces
│   ├── /usecase/         # Use cases (already exists)
│   └── /result/          # Result wrappers (already exists)
│
├── /presentation/
│   ├── /features/
│   │   ├── /order/       # Order feature
│   │   ├── /cash/        # Cash feature
│   │   ├── /task/        # Task feature
│   │   ├── /client/      # Client feature
│   │   ├── /product/     # Product feature
│   │   ├── /sync/        # Sync/Settings feature
│   │   └── /fiscal/      # Fiscal feature
│   ├── /common/          # Shared UI components
│   │   ├── /adapter/     # Base adapters
│   │   └── /viewmodel/   # Base ViewModels
│   └── /main/            # MainActivity, Application
│
├── /infrastructure/
│   ├── /camera/          # Camera implementation
│   ├── /printer/         # Printer implementation
│   ├── /location/        # Location services (geo)
│   ├── /logger/          # Logging implementation
│   └── /license/         # License management
│
├── /di/                  # Dependency injection modules
├── /extensions/          # Kotlin extensions
└── /utility/             # Utilities and constants
```

---

## Migration Steps (13 Steps)

### **Step 1: Create New Package Structure** ✓ Safe, No Breaking Changes
**Time**: 5 minutes
**Risk**: None (just creating folders)

- Create all new package directories
- No files moved yet
- Verify structure in IDE

**Files affected**: 0
**Breaking changes**: None

---

### **Step 2: Move Data Layer - Entities** ⚠️ Medium Risk
**Time**: 30-45 minutes
**Risk**: Medium (many imports to update)

**Actions**:
- Move `/dao/entity/*.kt` → `/data/local/entity/`
- Update package declarations
- Update all imports across codebase

**Files to move**: ~30 entity files
- Order.kt, Cash.kt, Task.kt
- Client.kt, Product.kt, Company.kt
- OrderContent.kt, ProductPrice.kt, Rest.kt
- UserAccount.kt, LogEvent.kt
- etc.

**Files to update**: ~150 files with imports

**Verification**:
```bash
./gradlew compileStandartDebugKotlin
```

---

### **Step 3: Move Data Layer - DAOs** ⚠️ Medium Risk
**Time**: 20-30 minutes
**Risk**: Medium

**Actions**:
- Move `/dao/*Dao.kt` → `/data/local/dao/`
- Move `/dao/AppDatabase.kt` → `/data/local/database/`
- Update package declarations
- Update imports

**Files to move**: ~20 DAO files
- OrderDao.kt, CashDao.kt, TaskDao.kt
- ClientDao.kt, ProductDao.kt
- UserAccountDao.kt, etc.

**Files to update**: ~80 files with imports

**Verification**:
```bash
./gradlew compileStandartDebugKotlin
```

---

### **Step 4: Move Data Layer - Network DTOs** ✓ Low Risk
**Time**: 15-20 minutes
**Risk**: Low (fewer dependencies)

**Actions**:
- Move `/dao/cloud/*.kt` → `/data/remote/dto/`
- Rename files to `*Dto.kt` suffix
- Update package declarations
- Update imports

**Files to move**: ~15 DTO files
- CloudOrder.kt → OrderDto.kt
- CloudClient.kt → ClientDto.kt
- CloudProduct.kt → ProductDto.kt
- etc.

**Files to update**: ~30 files (mostly in repositories)

**Verification**:
```bash
./gradlew compileStandartDebugKotlin
```

---

### **Step 5: Move Data Layer - API Interfaces** ✓ Low Risk
**Time**: 10-15 minutes
**Risk**: Low

**Actions**:
- Move `/http/HttpClientApi.kt` → `/data/remote/api/`
- Move `/http/*Interceptor.kt` → `/data/remote/interceptor/`
- Update package declarations
- Update imports

**Files to move**: ~3 files
- HttpClientApi.kt
- HttpAuthInterceptor.kt
- (any other network files)

**Files to update**: ~20 files

**Verification**:
```bash
./gradlew compileStandartDebugKotlin
```

---

### **Step 6: Move Data Layer - Repositories** ⚠️ Medium Risk
**Time**: 30-40 minutes
**Risk**: Medium

**Actions**:
- Move `/dao/impl/*RepositoryImpl.kt` → `/data/repository/`
- Keep `/repository/*Repository.kt` → will move to domain in Step 7
- Update package declarations
- Update imports

**Files to move**: ~15 repository implementations
- OrderRepositoryImpl.kt
- CashRepositoryImpl.kt
- TaskRepositoryImpl.kt
- ClientRepositoryImpl.kt
- ProductRepositoryImpl.kt
- NetworkRepositoryImpl.kt
- etc.

**Files to update**: ~50 files (DI modules, ViewModels)

**Verification**:
```bash
./gradlew compileStandartDebugKotlin
```

---

### **Step 7: Move Domain Layer - Repository Interfaces** ✓ Low Risk
**Time**: 10-15 minutes
**Risk**: Low

**Actions**:
- Move `/repository/*.kt` → `/domain/repository/`
- Update package declarations
- Update imports

**Files to move**: ~10 repository interfaces
- OrderRepository.kt
- CashRepository.kt
- TaskRepository.kt
- DocumentRepository.kt
- etc.

**Files to update**: ~40 files

**Verification**:
```bash
./gradlew compileStandartDebugKotlin
```

---

### **Step 8: Move Presentation - Order Feature** ⚠️ Medium Risk
**Time**: 20-30 minutes
**Risk**: Medium

**Actions**:
- Move `/documents/order/` → `/presentation/features/order/`
- Update package declarations
- Update imports
- Update navigation references

**Files to move**: ~8 files
- OrderFragment.kt
- OrderViewModel.kt
- OrderListFragment.kt
- OrderListViewModel.kt
- OrderAdapter.kt
- OrderContentFragment.kt
- etc.

**Files to update**: ~30 files (navigation, DI)

**Verification**:
```bash
./gradlew compileStandartDebugKotlin
```

---

### **Step 9: Move Presentation - Cash Feature** ✓ Low Risk
**Time**: 15-20 minutes
**Risk**: Low (smaller feature)

**Actions**:
- Move `/documents/cash/` → `/presentation/features/cash/`
- Update package declarations
- Update imports
- Update navigation references

**Files to move**: ~6 files
**Files to update**: ~20 files

**Verification**:
```bash
./gradlew compileStandartDebugKotlin
```

---

### **Step 10: Move Presentation - Task Feature** ✓ Low Risk
**Time**: 15-20 minutes
**Risk**: Low

**Actions**:
- Move `/documents/task/` → `/presentation/features/task/`
- Update package declarations
- Update imports
- Update navigation references

**Files to move**: ~6 files
**Files to update**: ~20 files

**Verification**:
```bash
./gradlew compileStandartDebugKotlin
```

---

### **Step 11: Move Presentation - Catalog Features** ⚠️ Medium Risk
**Time**: 40-60 minutes
**Risk**: Medium (many features)

**Actions**:
- Move `/catalogs/client/` → `/presentation/features/client/`
- Move `/catalogs/product/` → `/presentation/features/product/`
- Move `/catalogs/debt/` → `/presentation/features/debt/`
- Move `/catalogs/map/` → `/presentation/features/map/`
- Move `/catalogs/locations/` → `/presentation/features/locations/`
- Update package declarations
- Update imports
- Update navigation references

**Files to move**: ~40 files
**Files to update**: ~80 files

**Verification**:
```bash
./gradlew compileStandartDebugKotlin
```

---

### **Step 12: Move Infrastructure & Common** ✓ Low Risk
**Time**: 20-30 minutes
**Risk**: Low

**Actions**:
- Move `/camera/` → `/infrastructure/camera/`
- Move `/printer/` → `/infrastructure/printer/`
- Move `/geo/` → `/infrastructure/location/`
- Move `/logger/` → `/infrastructure/logger/`
- Move `/license/` → `/infrastructure/license/`
- Move `/shared/` → `/presentation/common/viewmodel/`
- Move `/fiscal/` → `/presentation/features/fiscal/`
- Move `MainActivity.kt` → `/presentation/main/`
- Move `AgentApplication.kt` → `/presentation/main/`

**Files to move**: ~40 files
**Files to update**: ~60 files

**Verification**:
```bash
./gradlew compileStandartDebugKotlin
```

---

### **Step 13: Update Build Configuration & Final Verification** ✓ Safe
**Time**: 15-20 minutes
**Risk**: Low

**Actions**:
- Update ProGuard rules with new package names
- Update navigation graph references if needed
- Update AndroidManifest.xml references
- Run full build and tests
- Update refactoring_plan.md

**Files to update**: ~5 files

**Verification**:
```bash
./gradlew clean
./gradlew assembleStandartDebug
./gradlew testStandartDebugUnitTest
```

---

## Execution Strategy

### Before Starting
1. ✅ Commit all current changes
2. ✅ Create new branch: `refactor/clean-architecture`
3. ✅ Run full build to ensure clean starting point
4. ✅ Create backup: `git tag before-reorganization`

### During Each Step
1. ✅ Complete one step at a time
2. ✅ Run verification build after each step
3. ✅ Commit changes with descriptive message
4. ✅ If build fails, rollback and fix before proceeding

### After Completion
1. ✅ Run full test suite
2. ✅ Test app on device/emulator
3. ✅ Review all changes
4. ✅ Merge to main branch

---

## Risk Mitigation

### High-Risk Steps (2, 3, 6, 8, 11)
- Do during dedicated time blocks
- Keep IDE refactoring tools open for find/replace
- Use git to track changes carefully
- Test thoroughly before moving to next step

### Medium-Risk Steps (4, 5, 9, 10)
- Should be straightforward
- Fewer dependencies to update
- Quick verification

### Low-Risk Steps (1, 7, 12, 13)
- Safe to execute
- Minimal breaking changes
- Easy to verify

---

## IDE Refactoring Tools

### IntelliJ/Android Studio Features to Use:
1. **Move Package**: Right-click package → Refactor → Move
2. **Find/Replace in Path**: Ctrl+Shift+R (Windows) / Cmd+Shift+R (Mac)
3. **Safe Delete**: Delete with usage search
4. **Optimize Imports**: Ctrl+Alt+O (Windows) / Cmd+Alt+O (Mac)

### Git Commands:
```bash
# Move files with git mv to preserve history
git mv old/path new/path

# Commit each step
git add .
git commit -m "Step X: Move [component] to Clean Architecture structure"
```

---

## Estimated Total Time

| Steps | Time Range | Risk |
|-------|-----------|------|
| Steps 1-4 | 1.5 - 2 hours | Low-Medium |
| Steps 5-7 | 1 - 1.5 hours | Low-Medium |
| Steps 8-11 | 2.5 - 3.5 hours | Medium-High |
| Steps 12-13 | 0.5 - 1 hour | Low |
| **Total** | **5.5 - 8 hours** | - |

**Recommended**: Split across 2-3 sessions with breaks between high-risk steps.

---

## Rollback Plan

If any step causes critical issues:

```bash
# Rollback last commit
git reset --hard HEAD~1

# Or rollback to before reorganization
git reset --hard before-reorganization

# Or discard specific files
git checkout HEAD -- path/to/file
```

---

## Benefits After Completion

✅ **Clear separation of concerns** - Data, Domain, Presentation layers
✅ **Better testability** - Each layer can be tested independently
✅ **Easier navigation** - Features grouped by business logic
✅ **Improved maintainability** - Standard Clean Architecture patterns
✅ **Team onboarding** - Familiar structure for Android developers
✅ **Scalability** - Easy to add new features in isolated packages

---

## Next Steps

1. Review this plan
2. Choose starting point (recommend Step 1)
3. Execute steps incrementally
4. Update this document with actual time/issues encountered
5. Celebrate completion! 🎉

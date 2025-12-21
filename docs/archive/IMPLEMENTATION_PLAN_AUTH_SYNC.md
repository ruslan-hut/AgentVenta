# Implementation Plan: Google/Email Authentication with Firebase Settings Sync

## Overview
Add Firebase Authentication (Google Sign-In & Email/Password) and sync UserAccount connection settings across devices using Firestore.

## Current State Analysis

### Existing Architecture
- **Multi-account system**: Each `UserAccount` represents a 1C database connection
- **Local storage**: Room database with `user_accounts` table
- **Account fields**: guid, description, dbServer, dbName, dbUser, dbPassword, token, options
- **Current account**: Marked with `is_current=1` flag
- **No authentication**: App currently starts directly at OrderListFragment
- **Firebase**: Already integrated (firebase-auth, firebase-firestore dependencies present)

### Key Files
- Entity: `/data/local/entity/UserAccount.kt`
- Repository Interface: `/domain/repository/UserAccountRepository.kt`
- Repository Implementation: `/data/repository/UserAccountRepositoryImpl.kt`
- UI: `/presentation/features/settings/UserAccountFragment.kt`
- Main Activity: `/presentation/main/MainActivity.kt`

---

## Architecture Changes

### 1. Authentication Layer (New)

#### Files to Create

**`/domain/repository/AuthRepository.kt`**
```kotlin
interface AuthRepository {
    val authState: Flow<AuthState>
    val currentUserId: Flow<String?>
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser>
    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser>
    suspend fun signUpWithEmail(email: String, password: String): Result<FirebaseUser>
    suspend fun signOut()
    suspend fun getCurrentUser(): FirebaseUser?
    fun isAuthenticated(): Boolean
}

sealed class AuthState {
    object Unauthenticated : AuthState()
    data class Authenticated(val user: FirebaseUser) : AuthState()
    object Anonymous : AuthState()
    data class Error(val message: String) : AuthState()
}
```

**`/data/repository/AuthRepositoryImpl.kt`**
- Implements FirebaseAuth integration
- Manages auth state Flow
- Handles Google Sign-In token exchange
- Handles email/password authentication
- Provides anonymous mode support

**`/di/AuthModule.kt`**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideAuthRepository(firebaseAuth: FirebaseAuth): AuthRepository {
        return AuthRepositoryImpl(firebaseAuth)
    }
}
```

**`/presentation/features/auth/LoginFragment.kt`**
- Google Sign-In button (One Tap sign-in)
- Email/Password input fields with validation
- "Sign Up" / "Sign In" toggle
- "Skip" button for anonymous mode (local-only)
- Privacy policy / Terms link
- Loading states and error messages

**`/presentation/features/auth/LoginViewModel.kt`**
```kotlin
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsSyncRepository: SettingsSyncRepository
) : ViewModel() {
    val authState = authRepository.authState

    fun signInWithGoogle(idToken: String)
    fun signInWithEmail(email: String, password: String)
    fun signUpWithEmail(email: String, password: String)
    fun skipLogin() // Sets anonymous mode flag
}
```

#### UI Layout Files
- `/res/layout/fragment_login.xml` - Login screen UI
- `/res/navigation/navigation.xml` - Add LoginFragment as conditional start destination

---

### 2. Settings Sync Layer (New)

#### Files to Create

**`/domain/repository/SettingsSyncRepository.kt`**
```kotlin
interface SettingsSyncRepository {
    suspend fun uploadAccount(account: UserAccount): Result<Unit>
    suspend fun uploadAllAccounts(accounts: List<UserAccount>): Result<Unit>
    suspend fun downloadAccounts(): Result<List<UserAccount>>
    suspend fun deleteAccount(accountGuid: String): Result<Unit>
    suspend fun syncOnLogin(): Result<SyncResult>
}

data class SyncResult(
    val downloaded: Int,
    val uploaded: Int,
    val conflicts: Int,
    val errors: List<String>
)
```

**`/data/repository/SettingsSyncRepositoryImpl.kt`**
- Implements Firestore operations
- Encrypts passwords before upload
- Decrypts passwords after download
- Handles merge conflicts (timestamp-based)
- Manages offline queue

**`/data/remote/model/UserAccountCloud.kt`**
```kotlin
data class UserAccountCloud(
    val guid: String = "",
    val description: String = "",
    val dbServer: String = "",
    val dbName: String = "",
    val dbUser: String = "",
    val dbPasswordEncrypted: String = "", // Encrypted with AES-256
    val dataFormat: String = "",
    val license: String = "",
    val options: String = "",
    val updatedAt: Long = 0L, // Timestamp for conflict resolution
    val isSynced: Boolean = true
) {
    fun toUserAccount(decryptedPassword: String): UserAccount
    companion object {
        fun fromUserAccount(account: UserAccount, encryptedPassword: String): UserAccountCloud
    }
}
```

#### Firestore Structure
```
/users/{userId}/accounts/{accountGuid}
  ├─ guid: String
  ├─ description: String
  ├─ dbServer: String
  ├─ dbName: String
  ├─ dbUser: String
  ├─ dbPasswordEncrypted: String (AES-256 encrypted)
  ├─ dataFormat: String
  ├─ license: String
  ├─ options: String (JSON)
  ├─ updatedAt: Timestamp (milliseconds)
  └─ isSynced: Boolean

/users/{userId}/metadata
  ├─ lastSyncTime: Timestamp
  └─ deviceId: String
```

---

### 3. Encryption Layer (New)

#### Files to Create

**`/utility/CryptoHelper.kt`**
```kotlin
class CryptoHelper @Inject constructor() {

    // Generates or retrieves AES key from Android Keystore
    private fun getOrCreateKey(): SecretKey

    // Encrypts password using AES-256-GCM
    fun encrypt(plainText: String): String

    // Decrypts password
    fun decrypt(encryptedText: String): String

    companion object {
        private const val KEY_ALIAS = "agentventa_sync_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
```

**Security approach:**
- Use Android Keystore for key management
- AES-256-GCM for encryption (authenticated encryption)
- Key never leaves device, stored in hardware-backed Keystore
- IV (Initialization Vector) stored with ciphertext
- Format: `{IV}:{CipherText}` (Base64 encoded)

---

### 4. Bidirectional Sync Logic

#### Sync Strategy

**On Login (Initial Sync):**
1. Download all accounts from Firestore
2. Compare with local Room accounts by `guid`
3. Conflict resolution:
   - If account exists in both: Compare `updatedAt` timestamps, keep newer
   - If only in Firestore: Insert to local Room
   - If only in local: Upload to Firestore
4. Mark all accounts as synced

**On Account Save:**
1. Save to local Room database (existing logic)
2. Immediately upload to Firestore (async)
3. If offline: Queue for later upload
4. Update `updatedAt` timestamp

**On Account Delete:**
1. Delete from local Room (existing logic)
2. Delete from Firestore (async)
3. If offline: Queue for later deletion

**Offline Queue:**
- Store pending operations in Room table: `sync_queue`
- Retry on network reconnection
- Exponential backoff for failures

#### Files to Modify

**`/data/repository/UserAccountRepositoryImpl.kt`**
```kotlin
class UserAccountRepositoryImpl @Inject constructor(
    private val userAccountDao: UserAccountDao,
    private val settingsSyncRepository: SettingsSyncRepository, // NEW
    private val authRepository: AuthRepository, // NEW
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
): UserAccountRepository {

    override suspend fun saveAccount(account: UserAccount): Long {
        // Add updatedAt timestamp
        val timestamp = System.currentTimeMillis()
        val accountWithTimestamp = account.copy(/* add timestamp field */)

        // Save locally (existing logic)
        val upd = userAccountDao.update(accountWithTimestamp)
        val result = if (upd > 0) upd.toLong() else userAccountDao.insert(accountWithTimestamp)

        // Upload to Firestore if authenticated
        if (authRepository.isAuthenticated()) {
            viewModelScope.launch(ioDispatcher) {
                settingsSyncRepository.uploadAccount(accountWithTimestamp)
            }
        }

        return result
    }

    override suspend fun deleteByGuid(guid: String): Int {
        // Delete locally (existing logic)
        val result = /* existing delete logic */

        // Delete from Firestore if authenticated
        if (authRepository.isAuthenticated()) {
            viewModelScope.launch(ioDispatcher) {
                settingsSyncRepository.deleteAccount(guid)
            }
        }

        return result
    }
}
```

**`/data/local/entity/UserAccount.kt`**
- Add `updatedAt: Long = 0L` field for conflict resolution
- Update database version and migration

---

### 5. UI Changes

#### New Screens

**LoginFragment** (`/presentation/features/auth/LoginFragment.kt`)
- **Layout**: Material Design with CardView
- **Components**:
  - App logo and welcome text
  - Google Sign-In button (white button with Google logo)
  - Email input field with email validation
  - Password input field with show/hide toggle
  - "Sign In" / "Sign Up" toggle button
  - "Forgot Password?" link
  - "Skip" button (bottom) - for anonymous mode
  - Privacy policy link (footer)
- **States**:
  - Idle: Show all options
  - Loading: Disable inputs, show progress
  - Error: Show error message with retry
  - Success: Navigate to OrderListFragment

#### Modified Screens

**`MainActivity.kt`** (lines 63-100)
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Check authentication state
    lifecycleScope.launch {
        authRepository.authState.collect { state ->
            when (state) {
                is AuthState.Unauthenticated -> {
                    // Navigate to LoginFragment
                    navController.navigate(R.id.loginFragment)
                }
                is AuthState.Authenticated -> {
                    // Trigger initial sync
                    settingsSyncRepository.syncOnLogin()
                }
                is AuthState.Anonymous -> {
                    // Continue with local-only mode
                }
            }
        }
    }

    // Existing initialization code...
}
```

**`UserAccountListFragment.kt`**
- Add sync status indicator (cloud icon with states):
  - Green: Synced
  - Orange: Syncing...
  - Red: Sync failed
  - Gray: Not synced (anonymous mode)
- Add "Sync Now" button in toolbar menu

**`SettingsFragment.kt`**
- Add "Account" section at top with:
  - Current user email (if authenticated)
  - "Sign Out" button
  - Last sync time
- Add "Sync Settings" option to trigger manual sync

#### Navigation Changes

**`/res/navigation/navigation.xml`**
```xml
<!-- Add LoginFragment -->
<fragment
    android:id="@+id/loginFragment"
    android:name="ua.com.programmer.agentventa.presentation.features.auth.LoginFragment"
    android:label="@string/login_title">
    <action
        android:id="@+id/action_login_to_orderList"
        app:destination="@id/orderListFragment"
        app:popUpTo="@id/loginFragment"
        app:popUpToInclusive="true" />
</fragment>

<!-- Make start destination conditional based on auth state -->
<!-- This will be handled in MainActivity, not in XML -->
```

---

### 6. Database Schema Changes

#### UserAccount Entity Update

**Add field to `UserAccount.kt`:**
```kotlin
@ColumnInfo(name = "updated_at") val updatedAt: Long = 0L
```

#### New Entity: SyncQueue

**`/data/local/entity/SyncQueue.kt`**
```kotlin
@Entity(tableName = "sync_queue", primaryKeys = ["id"])
data class SyncQueue(
    val id: String = UUID.randomUUID().toString(),
    val operation: String, // "upload", "delete"
    val accountGuid: String,
    val accountJson: String?, // Serialized UserAccount for upload
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String? = null
)
```

#### Migration

**Add to `AppDatabase.kt`:**
```kotlin
@Database(
    entities = [/* existing entities */, SyncQueue::class],
    version = 21, // Increment from 20
    exportSchema = true
)

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add updated_at column to user_accounts
        database.execSQL("ALTER TABLE user_accounts ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")

        // Create sync_queue table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS sync_queue (
                id TEXT PRIMARY KEY NOT NULL,
                operation TEXT NOT NULL,
                account_guid TEXT NOT NULL,
                account_json TEXT,
                timestamp INTEGER NOT NULL,
                retry_count INTEGER NOT NULL,
                last_error TEXT
            )
        """)
    }
}
```

---

### 7. Firestore Security Rules

**Deploy to Firebase Console:**
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // User accounts: only owner can read/write
    match /users/{userId}/accounts/{accountId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }

    // User metadata: only owner can read/write
    match /users/{userId}/metadata {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }

    // Deny all other access
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

---

## Implementation Steps

### Phase 1: Authentication Infrastructure (Days 1-3)

**Tasks:**
1. Create `AuthRepository` interface and `AuthRepositoryImpl`
2. Add `AuthModule` to DI (inject `FirebaseAuth`)
3. Create `LoginFragment` layout XML
4. Implement `LoginFragment` with Google Sign-In integration
   - Setup Google Sign-In client (Web Client ID from Firebase Console)
   - Handle One Tap sign-in flow
   - Handle email/password forms
5. Implement `LoginViewModel` with auth state management
6. Add auth state check in `MainActivity.onCreate()`
7. Test Google Sign-In flow
8. Test email/password authentication
9. Test anonymous mode (skip login)

**Deliverables:**
- Working Google Sign-In
- Working email/password authentication
- Anonymous mode functional
- Auth state persists across app restarts

---

### Phase 2: Encryption Layer (Days 4-5)

**Tasks:**
1. Create `CryptoHelper.kt` utility class
2. Implement Android Keystore integration
3. Implement AES-256-GCM encryption
4. Implement decryption with error handling
5. Write unit tests for encryption/decryption
6. Test key persistence across app restarts
7. Handle key loss scenarios (device lock screen changes)

**Deliverables:**
- Secure encryption/decryption working
- Unit tests passing
- Key stored in hardware-backed Keystore

---

### Phase 3: Settings Sync Infrastructure (Days 6-9)

**Tasks:**
1. Create `UserAccountCloud.kt` data model
2. Create `SettingsSyncRepository` interface
3. Implement `SettingsSyncRepositoryImpl` with Firestore
4. Implement `uploadAccount()` with encryption
5. Implement `downloadAccounts()` with decryption
6. Implement conflict resolution logic (timestamp-based)
7. Create `SyncQueue` entity and DAO
8. Implement offline queue logic
9. Add database migration (v20 → v21)
10. Deploy Firestore security rules
11. Write integration tests for sync operations

**Deliverables:**
- Firestore read/write working
- Passwords encrypted in Firestore
- Conflict resolution working
- Offline queue functional

---

### Phase 4: Bidirectional Sync Integration (Days 10-12)

**Tasks:**
1. Modify `UserAccountRepositoryImpl.saveAccount()` to trigger upload
2. Modify `UserAccountRepositoryImpl.deleteByGuid()` to trigger delete
3. Implement `syncOnLogin()` in `SettingsSyncRepository`
4. Add sync trigger in `MainActivity` on authentication
5. Implement sync status tracking (LiveData/Flow)
6. Add background sync worker (WorkManager) for offline queue
7. Handle network connectivity changes
8. Test sync across multiple devices
9. Test offline scenarios
10. Test conflict resolution with concurrent edits

**Deliverables:**
- Accounts sync on save
- Accounts sync on login
- Offline queue processes on reconnect
- Multi-device sync working

---

### Phase 5: UI Integration (Days 13-15)

**Tasks:**
1. Update `MainActivity` navigation logic for auth state
2. Add sync status indicator to `UserAccountListFragment`
3. Add "Sync Now" button to toolbar
4. Add "Sign Out" option to `SettingsFragment`
5. Add account info section to `SettingsFragment`
6. Show last sync time in UI
7. Add sync progress indicator
8. Add error messages for sync failures
9. Test all UI flows
10. Polish animations and transitions

**Deliverables:**
- Login screen integrated
- Sync status visible in UI
- Sign out working
- Manual sync button working
- Error states handled gracefully

---

### Phase 6: Testing & Polish (Days 16-18)

**Tasks:**
1. Write unit tests for `AuthRepositoryImpl`
2. Write unit tests for `SettingsSyncRepositoryImpl`
3. Write UI tests for `LoginFragment`
4. Test migration from existing accounts (no data loss)
5. Test sync with 10+ accounts
6. Test sync with slow network
7. Test sync with no network (offline queue)
8. Test concurrent edits on multiple devices
9. Test password change on 1C server (re-encryption)
10. Update ProGuard rules for Firebase classes
11. Test release build with minification
12. Performance testing (sync speed, memory usage)
13. Update CLAUDE.md documentation
14. Create user-facing documentation

**Deliverables:**
- All tests passing
- No data loss in migration
- Performance acceptable
- ProGuard configured
- Documentation updated

---

## Migration Strategy for Existing Users

### First Launch After Update

**Flow:**
1. App launches normally (no forced login)
2. Banner at top: "Sync your settings across devices - Sign in"
3. User can dismiss and continue using app locally
4. User can tap banner to go to login screen

**On First Login:**
1. Show progress dialog: "Syncing your accounts..."
2. Upload all local accounts to Firestore
3. Show success message: "X accounts synced"
4. User can now use app on other devices

**Key Points:**
- **No data loss**: Local data always accessible
- **Optional**: Login is optional, not forced
- **One-way initial**: First sync is upload-only (local → Firestore)
- **Non-disruptive**: Existing workflow unchanged if user skips login

---

## Security Considerations

### Password Encryption
- **Algorithm**: AES-256-GCM (authenticated encryption)
- **Key storage**: Android Keystore (hardware-backed on supported devices)
- **Key rotation**: Not implemented in v1 (add in future)
- **Threat model**: Protects against Firestore data breach, not device compromise

### Authentication
- **Token storage**: FirebaseAuth SDK handles tokens securely
- **Session management**: Automatic token refresh by Firebase
- **Sign-out**: Clears auth tokens and local session

### Network Security
- **Transport**: HTTPS only (Firestore SDK)
- **Certificate pinning**: Not implemented (rely on Firestore SDK)

### Firestore Access Control
- **Rules**: User can only access their own data (`request.auth.uid == userId`)
- **No public data**: All collections require authentication

---

## Error Handling

### Network Errors
- **Offline mode**: Queue operations, retry on reconnect
- **Timeout**: 30-second timeout for Firestore operations
- **Retry logic**: Exponential backoff (1s, 2s, 4s, 8s, max 60s)
- **User feedback**: Toast messages for errors

### Conflict Resolution
- **Strategy**: Last-write-wins based on `updatedAt` timestamp
- **Edge case**: If timestamps equal (< 1ms difference), prefer local data
- **Manual resolution**: Not implemented in v1 (add if requested)

### Encryption Errors
- **Key loss**: If Keystore key lost (rare), prompt user to re-enter passwords
- **Decryption failure**: Log error, skip that account, continue sync
- **Fallback**: If encryption fails, don't upload (keep local only)

---

## Performance Considerations

### Sync Performance
- **Batch uploads**: Upload all accounts in single Firestore batch write
- **Incremental download**: Only download changed accounts (future optimization)
- **Background sync**: Use WorkManager for non-blocking sync

### Memory
- **Pagination**: Firestore queries paginated if user has 100+ accounts
- **Cleanup**: Clear sync queue after successful operations

### Battery
- **No polling**: Only sync on login, save, or manual trigger
- **WorkManager**: Battery-aware background processing

---

## Testing Scenarios

### Manual Test Cases

**Authentication:**
1. Sign in with Google (new user)
2. Sign in with Google (existing user)
3. Sign in with email/password (new user)
4. Sign in with email/password (wrong password)
5. Sign up with email/password
6. Sign out
7. Skip login (anonymous mode)

**Sync:**
1. Login → accounts sync down
2. Save account → uploads immediately
3. Delete account → deletes from Firestore
4. Edit account on Device A → syncs to Device B
5. Edit same account on Device A and B → conflict resolution
6. Offline save → queued → syncs when online
7. 10 accounts sync in < 5 seconds

**Migration:**
1. Existing user with 5 accounts → update app → login → accounts preserved
2. Existing user → skip login → accounts still accessible locally

**Edge Cases:**
1. Network error during sync
2. Firestore permission denied
3. Encryption key lost
4. Invalid data in Firestore
5. App killed during sync

---

## Rollback Plan

**If issues arise post-deployment:**

1. **Disable sync**: Add remote config flag to disable sync feature
2. **Local fallback**: App works normally with local data
3. **Fix & redeploy**: Fix issues in patch release
4. **Data integrity**: Firestore data preserved, no deletion

**Code:**
```kotlin
// In SettingsSyncRepositoryImpl
private val syncEnabled = remoteConfig.getBoolean("sync_enabled")

override suspend fun uploadAccount(account: UserAccount): Result<Unit> {
    if (!syncEnabled) return Result.success(Unit) // Skip sync
    // ... existing code
}
```

---

## Future Enhancements (Post-v1)

1. **Incremental sync**: Only sync changed accounts (use Firestore snapshots)
2. **Manual conflict resolution**: Show UI when conflicts detected
3. **Selective sync**: Choose which accounts to sync
4. **Key rotation**: Periodic re-encryption with new keys
5. **Multi-factor authentication**: Add 2FA support
6. **Biometric auth**: Use fingerprint/face for re-authentication
7. **Sync analytics**: Track sync success/failure rates
8. **Account sharing**: Share specific accounts with team members

---

## Open Questions

1. **Should we support password reset?** (Forgot password flow)
   - **Recommendation**: Yes, implement Firebase password reset emails

2. **What happens if user signs in on Device B while offline on Device A?**
   - **Current plan**: Device A syncs when online, conflicts resolved by timestamp
   - **Alternative**: Show warning if accounts modified offline

3. **Should demo account sync?**
   - **Recommendation**: No, demo accounts are local-only (skip sync for `isDemo()`)

4. **Should we encrypt other sensitive fields?** (e.g., `dbUser`, `token`)
   - **Recommendation**: v1: password only. v2: encrypt all sensitive fields

5. **Rate limiting?** (Prevent abuse of sync API)
   - **Firestore**: Has built-in rate limiting (1 write/sec per document)
   - **Additional**: Could add app-level throttling if needed

---

## Dependencies

### Existing (Already in build.gradle)
- `firebase-auth` (line 108)
- `firebase-firestore` (line 107)
- `com.google.android.gms:play-services-auth` (for Google Sign-In)

### New Dependencies (Add to build.gradle)
```gradle
// Google Sign-In (One Tap)
implementation 'com.google.android.gms:play-services-auth:21.3.0'

// Coroutines (already present, but ensure version supports StateFlow)
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2'

// WorkManager (for background sync)
implementation 'androidx.work:work-runtime-ktx:2.11.0'

// Timber (for logging, optional)
implementation 'com.jakewharton.timber:timber:5.0.1'
```

### Firebase Configuration
- Download `google-services.json` from Firebase Console
- Enable Authentication methods: Google, Email/Password
- Create Firestore database in production mode
- Deploy security rules

---

## Timeline Estimate

**Total: 18 days (3.5 weeks)**

- Phase 1: Authentication (3 days)
- Phase 2: Encryption (2 days)
- Phase 3: Sync Infrastructure (4 days)
- Phase 4: Integration (3 days)
- Phase 5: UI (3 days)
- Phase 6: Testing & Polish (3 days)

**Assumes:**
- 1 full-time developer
- No major blockers
- Firebase project already configured

---

## Success Criteria

**v1 Release:**
- ✅ Users can sign in with Google
- ✅ Users can sign in with email/password
- ✅ Users can skip login (anonymous mode)
- ✅ Accounts sync across devices within 5 seconds
- ✅ Passwords encrypted in Firestore
- ✅ No data loss during migration
- ✅ Offline mode works (queue uploads)
- ✅ Conflict resolution works correctly
- ✅ No crashes in production (< 0.1% crash rate)
- ✅ Sync success rate > 99%

---

## Questions for Review

1. **Auth flow**: Should login be optional or required?
   - Current plan: Optional (can skip)

2. **Encryption scope**: Encrypt password only, or all fields?
   - Current plan: Password only in v1

3. **Conflict resolution**: Last-write-wins, or manual resolution UI?
   - Current plan: Last-write-wins (timestamp)

4. **Migration**: Force sync on first login, or keep it optional?
   - Current plan: Optional banner, user-initiated

5. **Demo accounts**: Should they sync?
   - Current plan: No, local-only

Please review and provide feedback!

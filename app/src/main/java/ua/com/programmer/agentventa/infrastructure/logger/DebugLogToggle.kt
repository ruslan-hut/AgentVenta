package ua.com.programmer.agentventa.infrastructure.logger

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import ua.com.programmer.agentventa.presentation.features.settings.UserOptionsBuilder
import ua.com.programmer.agentventa.utility.Constants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether the device should upload debug logs.
 *
 * Effective state = (local force-on flag) OR (server-pushed UserOptions.debugLogsEnabled).
 * The local flag exists so QA/dev can switch logging on without round-tripping
 * through the backend; the server flag exists so an operator can enable it for a
 * single misbehaving device per [UserAccount.guid].
 */
@Singleton
class DebugLogToggle @Inject constructor(
    private val prefs: SharedPreferences,
    private val userAccountRepository: UserAccountRepository,
) {

    private val _enabled = MutableStateFlow(forceFlag())
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Constants.PREF_DEBUG_LOGS_FORCE_ENABLED) {
            recomputeFromAccount(currentRemoteFlag)
        }
    }

    @Volatile private var currentRemoteFlag: Boolean = false

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        scope.launch {
            userAccountRepository.currentAccount.collectLatest { account ->
                val remote = UserOptionsBuilder.build(account).debugLogsEnabled
                currentRemoteFlag = remote
                recomputeFromAccount(remote)
            }
        }
    }

    fun isEnabled(): Boolean = _enabled.value

    private fun forceFlag(): Boolean =
        prefs.getBoolean(Constants.PREF_DEBUG_LOGS_FORCE_ENABLED, false)

    private fun recomputeFromAccount(remote: Boolean) {
        val effective = forceFlag() || remote
        if (_enabled.value != effective) _enabled.value = effective
    }
}

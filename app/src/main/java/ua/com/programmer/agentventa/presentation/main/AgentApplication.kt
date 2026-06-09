package ua.com.programmer.agentventa.presentation.main

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import ua.com.programmer.agentventa.infrastructure.logger.RemoteLogFlushWorker
import ua.com.programmer.agentventa.infrastructure.logger.RemoteLogUploader
import ua.com.programmer.agentventa.infrastructure.relay.RelayRestSyncWorker
import ua.com.programmer.agentventa.infrastructure.websocket.WebSocketConnectionManager
import ua.com.programmer.agentventa.infrastructure.websocket.WebSocketSyncWorker
import javax.inject.Inject

@HiltAndroidApp
class AgentApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var connectionManager: WebSocketConnectionManager

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var remoteLogUploader: RemoteLogUploader

    override fun onCreate() {
        super.onCreate()

        // Initialize automatic WebSocket connection manager
        connectionManager.initialize()

        // Schedule periodic background sync. Both workers run on the same
        // cadence and each no-ops for the other's transport: the WS worker's
        // checkAndConnect is a no-op for REST accounts, and the REST worker
        // no-ops unless the current account is REST-relay.
        WebSocketSyncWorker.scheduleWithDefaultInterval(this)
        RelayRestSyncWorker.scheduleWithDefaultInterval(this)

        // Remote debug log pipeline. Idempotent — start() is a no-op if the
        // drain loop is already running. Worker safety-net for process kill.
        remoteLogUploader.start()
        RemoteLogFlushWorker.schedule(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

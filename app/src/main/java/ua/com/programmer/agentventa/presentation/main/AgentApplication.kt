package ua.com.programmer.agentventa.presentation.main

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import ua.com.programmer.agentventa.infrastructure.websocket.WebSocketConnectionManager
import ua.com.programmer.agentventa.infrastructure.websocket.WebSocketSyncWorker
import javax.inject.Inject

@HiltAndroidApp
class AgentApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var connectionManager: WebSocketConnectionManager

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        // Initialize automatic WebSocket connection manager
        connectionManager.initialize()

        // Schedule periodic background sync
        WebSocketSyncWorker.scheduleWithDefaultInterval(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

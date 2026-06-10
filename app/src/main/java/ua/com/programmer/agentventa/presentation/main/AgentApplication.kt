package ua.com.programmer.agentventa.presentation.main

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import ua.com.programmer.agentventa.infrastructure.logger.RemoteLogFlushWorker
import ua.com.programmer.agentventa.infrastructure.logger.RemoteLogUploader
import ua.com.programmer.agentventa.infrastructure.relay.RelayRestSyncWorker
import javax.inject.Inject

@HiltAndroidApp
class AgentApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var remoteLogUploader: RemoteLogUploader

    override fun onCreate() {
        super.onCreate()

        // Schedule periodic background sync for REST-relay accounts. No-ops for
        // direct-1C / demo accounts (handled by manual HTTP sync).
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

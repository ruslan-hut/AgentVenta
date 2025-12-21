package ua.com.programmer.agentventa.di

import javax.inject.Qualifier

/**
 * Qualifier for OkHttpClient used by WebSocket connections.
 * This client does NOT include HttpAuthInterceptor to avoid
 * overwriting the Bearer token with Basic Auth credentials.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WebSocketClient

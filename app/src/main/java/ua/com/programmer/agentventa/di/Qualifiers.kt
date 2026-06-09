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

/**
 * Qualifier for the OkHttpClient/Retrofit used to POST debug logs to the relay
 * server. Must NOT include HttpAuthInterceptor (which would clobber the Bearer
 * apiKey:deviceUuid header) and must NOT include TokenRefresh (which is wired
 * for 1C basic auth, not the relay).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DebugLogClient

/**
 * Qualifier for the OkHttpClient/Retrofit used by the relay device-REST sync
 * (status/pull/ack/upload). Like [DebugLogClient] it carries no
 * HttpAuthInterceptor or TokenRefresh (auth is the Bearer apiKey:deviceUuid
 * header passed per call), but uses longer timeouts since catalog pulls and
 * base64 image uploads can be large.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RelayClient

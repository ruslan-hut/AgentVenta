package ua.com.programmer.agentventa.presentation.common.viewmodel

import android.widget.ImageView
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.resource.bitmap.Rotate
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.data.local.entity.ClientImage
import ua.com.programmer.agentventa.data.local.entity.LProduct
import ua.com.programmer.agentventa.data.local.entity.UserAccount
import ua.com.programmer.agentventa.data.local.entity.fileName
import ua.com.programmer.agentventa.data.local.entity.getBaseUrl
import ua.com.programmer.agentventa.data.local.entity.hasImageData
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ImageLoadingManager using Glide library.
 *
 * Handles:
 * - Authenticated image loading from remote server
 * - Local cached image loading
 * - Image transformations (rotation)
 * - Cache management
 * - Base64 encoding for uploads
 */
@Singleton
class GlideImageLoadingManager @Inject constructor(
    private val glide: RequestManager,
    private val logger: Logger
) : ImageLoadingManager {

    private val logTag = "ImageLoading"

    private var baseUrl: String = ""
    private var headers: LazyHeaders? = null
    private var cacheDir: File? = null

    override fun configure(account: UserAccount) {
        baseUrl = account.getBaseUrl()
        headers = createHeaders(account)
    }

    override fun setCacheDir(dir: File) {
        cacheDir = dir
    }

    override fun loadProductImage(
        product: LProduct,
        view: ImageView,
        rotation: Int,
        loadImages: Boolean
    ) {
        if (!loadImages) return
        if (!product.hasImageData()) return

        val url = getImageUrl(product.imageGuid ?: "", product.imageUrl ?: "")
        val glideUrl = GlideUrl(url, headers ?: LazyHeaders.DEFAULT)

        glide.load(glideUrl)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)  // Only cache transformed
            .placeholder(R.drawable.baseline_downloading_24)
            .error(R.drawable.baseline_error_outline_24)
            .apply {
                if (rotation != 0) {
                    transform(Rotate(rotation))
                }
            }
            .into(view)
    }

    override fun loadClientImage(
        image: ClientImage,
        view: ImageView,
        rotation: Int
    ) {
        if (image.isLocal == 0) {
            // Remote image - load from server
            loadRemoteClientImage(image, view, rotation)
        } else {
            // Local image - load from cache
            loadLocalClientImage(image, view, rotation)
        }
    }

    private fun loadRemoteClientImage(
        image: ClientImage,
        view: ImageView,
        rotation: Int
    ) {
        // Clean up local cache file if it exists
        val imageFile = fileInCache(image.fileName())
        try {
            if (imageFile.exists()) {
                imageFile.delete()
            }
        } catch (e: Exception) {
            logger.e(logTag, "Failed to delete cached file: ${e.message}")
        }

        val url = getImageUrl(image.guid, image.url)
        val glideUrl = GlideUrl(url, headers ?: LazyHeaders.DEFAULT)

        glide.load(glideUrl)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .placeholder(R.drawable.baseline_downloading_24)
            .error(R.drawable.baseline_error_outline_24)
            .apply {
                if (rotation != 0) {
                    transform(Rotate(rotation))
                }
            }
            .into(view)
    }

    private fun loadLocalClientImage(
        image: ClientImage,
        view: ImageView,
        rotation: Int
    ) {
        val imageFile = fileInCache(image.fileName())

        glide.load(imageFile)
            .diskCacheStrategy(DiskCacheStrategy.NONE)  // Don't cache local files
            .placeholder(R.drawable.baseline_downloading_24)
            .error(R.drawable.baseline_error_outline_24)
            .apply {
                if (rotation != 0) {
                    transform(Rotate(rotation))
                }
            }
            .into(view)
    }

    override fun fileInCache(fileName: String): File {
        val cache = cacheDir ?: throw IllegalStateException(
            "Cache directory not set. Call setCacheDir() first."
        )
        return File(cache, fileName)
    }

    override fun deleteFileInCache(fileName: String) {
        try {
            val file = fileInCache(fileName)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            logger.e(logTag, "Failed to delete file: ${e.message}")
        }
    }

    override fun encodeBase64(file: File): String {
        return try {
            val bytes = file.readBytes()
            android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            logger.e(logTag, "Failed to encode Base64: ${e.message}")
            ""
        }
    }

    override fun clearHeaders() {
        headers = null
    }

    /**
     * Returns the full image URL based on the provided parameters.
     *
     * @param guid The unique identifier for the image
     * @param url The image URL. If not blank, returns as-is
     * @return The full image URL
     */
    private fun getImageUrl(guid: String, url: String): String {
        return url.ifBlank { "$baseUrl/image/$guid" }
    }

    /**
     * Creates authentication headers for image requests.
     *
     * @param account User account with credentials
     * @return LazyHeaders with Basic Auth
     */
    private fun createHeaders(account: UserAccount): LazyHeaders {
        val credentials = "${account.dbUser ?: ""}:${account.dbPassword ?: ""}"
        val encodedAuth = android.util.Base64.encodeToString(
            credentials.toByteArray(),
            android.util.Base64.NO_WRAP
        )

        return LazyHeaders.Builder()
            .addHeader("Authorization", "Basic $encodedAuth")
            .build()
    }
}

package ua.com.programmer.agentventa.shared

import android.widget.ImageView
import ua.com.programmer.agentventa.dao.entity.ClientImage
import ua.com.programmer.agentventa.dao.entity.LProduct
import ua.com.programmer.agentventa.dao.entity.UserAccount
import java.io.File

/**
 * Interface for managing image loading operations.
 * Extracted from SharedViewModel to follow Single Responsibility Principle.
 *
 * Responsibilities:
 * - Load product images with authentication
 * - Load client images (local and remote)
 * - Manage image cache
 * - Handle image encoding for upload
 */
interface ImageLoadingManager {

    /**
     * Configure the manager with current user account credentials.
     * Must be called before loading images that require authentication.
     */
    fun configure(account: UserAccount)

    /**
     * Set the cache directory for local image storage.
     */
    fun setCacheDir(dir: File)

    /**
     * Load product image into ImageView.
     *
     * @param product Product with image data
     * @param view Target ImageView
     * @param rotation Rotation angle (0, 90, 180, 270)
     * @param loadImages Whether to actually load images (from user options)
     */
    fun loadProductImage(
        product: LProduct,
        view: ImageView,
        rotation: Int = 0,
        loadImages: Boolean = true
    )

    /**
     * Load client image into ImageView.
     * Handles both local (cached) and remote images.
     *
     * @param image Client image metadata
     * @param view Target ImageView
     * @param rotation Rotation angle (0, 90, 180, 270)
     */
    fun loadClientImage(
        image: ClientImage,
        view: ImageView,
        rotation: Int = 0
    )

    /**
     * Get file reference in cache directory.
     *
     * @param fileName Name of the file
     * @return File object in cache directory
     */
    fun fileInCache(fileName: String): File

    /**
     * Delete file from cache directory.
     *
     * @param fileName Name of the file to delete
     */
    fun deleteFileInCache(fileName: String)

    /**
     * Encode image file to Base64 string for upload.
     *
     * @param file Image file
     * @return Base64 encoded string, or empty string on error
     */
    fun encodeBase64(file: File): String

    /**
     * Clear all cached authentication headers.
     * Should be called when user account changes.
     */
    fun clearHeaders()
}

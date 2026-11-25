package com.gmm.domain.image.storage

/** Stores uploaded images and returns their public URL. */
interface ImageStorage {
    fun store(bytes: ByteArray, extension: String): String

    /** True only for a URL that [store] could have returned, so clients cannot point images at other hosts. */
    fun isStoredUrl(url: String): Boolean
}

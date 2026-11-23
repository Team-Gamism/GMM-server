package com.gmm.domain.image.storage

/** Stores uploaded images and returns their public URL. */
interface ImageStorage {
    fun store(bytes: ByteArray, extension: String): String
}

package com.gmm.domain.build.storage

import java.nio.file.Path

/**
 * Stores extracted builds. MVP keeps them on local disk behind nginx; an S3-compatible
 * implementation can replace [LocalDiskBuildStorage] without touching callers.
 */
interface BuildStorage {
    /** Creates an empty, private work directory for one upload. Delete it with [deleteWorkspace]. */
    fun createWorkspace(uploadId: String): Path

    fun deleteWorkspace(workspace: Path)

    /** Moves an extracted build to its public location and returns its build path (`{gameId}/{uploadId}`). */
    fun publish(extractedDir: Path, gameId: String, uploadId: String): String

    fun delete(buildPath: String)

    fun deleteGame(gameId: String)

    /** Public folder URL without a trailing slash. */
    fun publicUrl(buildPath: String): String
}

package com.apphud.sdk.storage

import androidx.core.util.AtomicFile
import com.apphud.sdk.ApphudIdentityVersion
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * The identity version this install ran last. Install-scoped: survives logout.
 */
internal interface IdentityVersionStorage {
    /** Null when no version has been saved yet, or it can't be read. */
    fun read(): ApphudIdentityVersion?

    /** Throws when the version wasn't saved. */
    fun write(version: ApphudIdentityVersion)
}

/**
 * Keeps the version in `noBackupFilesDir`, next to the install record: Auto Backup never restores it,
 * so data restored from another install never reads as this install's own.
 */
internal class FileIdentityVersionStorage(
    private val directory: () -> File?,
) : IdentityVersionStorage {

    override fun read(): ApphudIdentityVersion? {
        val file = atomicFile() ?: return null
        val text = try {
            file.readFully().toString(Charsets.UTF_8).trim()
        } catch (e: FileNotFoundException) {
            return null
        } catch (e: IOException) {
            return null
        }
        return ApphudIdentityVersion.values().firstOrNull { it.name == text }
    }

    override fun write(version: ApphudIdentityVersion) {
        val file = atomicFile() ?: throw IOException("No directory for the identity version")
        val out = file.startWrite()
        try {
            out.write(version.name.toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            throw e
        }
    }

    private fun atomicFile(): AtomicFile? = directory()?.let { AtomicFile(File(it, FILE_NAME)) }

    companion object {
        const val FILE_NAME = "apphud_identity_version"
    }
}

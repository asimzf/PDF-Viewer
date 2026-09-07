package com.asimzf.asimpdf.pdf

import android.content.Context
import com.asimzf.asimpdf.util.Workspace
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File

/** Raised when a document needs a password we do not have yet. */
class PdfPasswordRequiredException(message: String = "This PDF is password protected.") :
    Exception(message)

/** Any failure we want to surface to the user with a readable message. */
class PdfOperationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Loading and saving helpers shared by every editing operation.
 *
 * Documents are opened with a mixed memory setting so large files spill to a
 * scratch file in the app cache instead of blowing up the heap.
 */
object PdfIo {

    private const val MAIN_MEMORY_BYTES = 24L * 1024 * 1024

    private var tempDir: File? = null

    fun attach(context: Context) {
        tempDir = File(context.cacheDir, "pdfbox").apply { mkdirs() }
    }

    private fun memorySetting(): MemoryUsageSetting {
        val setting = MemoryUsageSetting.setupMixed(MAIN_MEMORY_BYTES)
        return tempDir?.let { setting.setTempDir(it) } ?: setting
    }

    @Throws(PdfPasswordRequiredException::class, PdfOperationException::class)
    fun load(file: File, password: String? = null): PDDocument = try {
        PDDocument.load(file, password ?: "", memorySetting())
    } catch (e: InvalidPasswordException) {
        throw PdfPasswordRequiredException(
            if (password.isNullOrEmpty()) "This PDF is password protected."
            else "That password did not unlock the PDF."
        )
    } catch (e: Exception) {
        throw PdfOperationException("This file could not be read as a PDF.", e)
    }

    fun blank(): PDDocument = PDDocument(memorySetting())

    /**
     * Runs [block] against [file] and writes the result back atomically, so a
     * failure half way through never leaves a corrupt document behind.
     */
    fun <T> edit(
        context: Context,
        file: File,
        password: String? = null,
        stripSecurity: Boolean = true,
        block: (PDDocument) -> T
    ): T {
        val temp = Workspace.newWorkFile(context, ".tmp")
        val document = try {
            load(file, password)
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
        val result: T
        try {
            if (stripSecurity && document.isEncrypted) {
                // We opened it with the right password; saving keeps it readable.
                document.isAllSecurityToBeRemoved = true
            }
            result = block(document)
            document.save(temp)
        } catch (e: Exception) {
            temp.delete()
            throw e as? PdfOperationException
                ?: PdfOperationException(e.message ?: "The document could not be updated.", e)
        } finally {
            runCatching { document.close() }
        }
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
        return result
    }

    /** Reads information out of a document without modifying it. */
    fun <T> read(file: File, password: String? = null, block: (PDDocument) -> T): T =
        load(file, password).use(block)

    /** Saves [document] to a fresh working file and returns it. */
    fun saveNew(context: Context, document: PDDocument, suffix: String = ".pdf"): File {
        val target = Workspace.newWorkFile(context, suffix)
        document.save(target)
        return target
    }
}

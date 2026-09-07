package com.asimzf.asimpdf.pdf

import android.content.Context
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The permission switches a PDF can carry alongside its passwords. */
data class PdfPermissions(
    val canPrint: Boolean = true,
    val canPrintHighQuality: Boolean = true,
    val canModify: Boolean = true,
    val canCopyContent: Boolean = true,
    val canAnnotate: Boolean = true,
    val canFillForms: Boolean = true,
    val canAssemble: Boolean = true,
    val canExtractForAccessibility: Boolean = true
) {
    fun toAccessPermission(): AccessPermission = AccessPermission().apply {
        setCanPrint(canPrint)
        setCanPrintDegraded(canPrintHighQuality)
        setCanModify(canModify)
        setCanExtractContent(canCopyContent)
        setCanModifyAnnotations(canAnnotate)
        setCanFillInForm(canFillForms)
        setCanAssembleDocument(canAssemble)
        setCanExtractForAccessibility(canExtractForAccessibility)
    }

    companion object {
        fun from(permission: AccessPermission) = PdfPermissions(
            canPrint = permission.canPrint(),
            canPrintHighQuality = permission.canPrintDegraded(),
            canModify = permission.canModify(),
            canCopyContent = permission.canExtractContent(),
            canAnnotate = permission.canModifyAnnotations(),
            canFillForms = permission.canFillInForm(),
            canAssemble = permission.canAssembleDocument(),
            canExtractForAccessibility = permission.canExtractForAccessibility()
        )
    }
}

/** Encryption strength offered when protecting a document. */
enum class EncryptionStrength(val label: String, val keyLength: Int) {
    AES_128("AES 128-bit", 128),
    AES_256("AES 256-bit", 256)
}

/**
 * Password protection. Documents are decrypted into the working copy when they
 * are opened, so protecting one is always a matter of writing new encryption.
 */
object PdfSecurity {

    suspend fun protect(
        context: Context,
        file: File,
        userPassword: String,
        ownerPassword: String,
        permissions: PdfPermissions,
        strength: EncryptionStrength
    ) = withContext(Dispatchers.IO) {
        if (userPassword.isEmpty() && ownerPassword.isEmpty()) {
            throw PdfOperationException("Enter a password first.")
        }
        PdfIo.edit(context, file, stripSecurity = false) { document ->
            document.isAllSecurityToBeRemoved = false
            val policy = StandardProtectionPolicy(
                ownerPassword.ifEmpty { userPassword },
                userPassword,
                permissions.toAccessPermission()
            )
            policy.encryptionKeyLength = strength.keyLength
            policy.preferAES = true
            document.protect(policy)
        }
    }

    /** Writes the document out with no encryption at all. */
    suspend fun removeProtection(context: Context, file: File) = withContext(Dispatchers.IO) {
        PdfIo.edit(context, file) { document ->
            document.isAllSecurityToBeRemoved = true
        }
    }

    /** Reports whether the file on disk is encrypted and what it allows. */
    suspend fun inspect(file: File, password: String? = null): Pair<Boolean, PdfPermissions> =
        withContext(Dispatchers.IO) {
            PdfIo.read(file, password) { document ->
                document.isEncrypted to PdfPermissions.from(document.currentAccessPermission)
            }
        }

    /** True when the file needs a password before it can be opened. */
    suspend fun isPasswordProtected(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            PdfIo.read(file) { it.isEncrypted }
            false
        } catch (e: PdfPasswordRequiredException) {
            true
        } catch (e: Exception) {
            false
        }
    }
}

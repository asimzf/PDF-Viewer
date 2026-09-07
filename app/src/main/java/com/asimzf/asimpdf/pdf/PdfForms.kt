package com.asimzf.asimpdf.pdf

import android.content.Context
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDPushButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class FormFieldType { TEXT, CHECKBOX, RADIO, CHOICE, SIGNATURE, BUTTON, OTHER }

data class FormField(
    val name: String,
    val label: String,
    val type: FormFieldType,
    val value: String,
    val options: List<String> = emptyList(),
    val readOnly: Boolean = false,
    val multiline: Boolean = false
)

/** Reading, filling and flattening interactive PDF forms. */
object PdfForms {

    suspend fun fields(file: File): List<FormField> = withContext(Dispatchers.IO) {
        PdfIo.read(file) { document ->
            val form = document.documentCatalog?.acroForm ?: return@read emptyList<FormField>()
            val collected = mutableListOf<FormField>()
            val iterator = form.fieldIterator
            while (iterator.hasNext()) {
                val field = iterator.next() ?: continue
                if (field is PDPushButton) continue
                collected.add(describe(field))
                if (collected.size >= MAX_FIELDS) break
            }
            collected.toList()
        }
    }

    suspend fun setValues(context: Context, file: File, values: Map<String, String>) =
        withContext(Dispatchers.IO) {
            if (values.isEmpty()) return@withContext
            PdfIo.edit(context, file) { document ->
                val form = document.documentCatalog?.acroForm
                    ?: throw PdfOperationException("This PDF has no form fields.")
                values.forEach { (name, value) ->
                    val field = form.getField(name) ?: return@forEach
                    if (field.isReadOnly) return@forEach
                    runCatching {
                        when (field) {
                            is PDCheckBox -> if (value.toBoolean()) field.check() else field.unCheck()
                            is PDRadioButton -> field.setValue(value)
                            is PDChoice -> field.setValue(value)
                            is PDTextField -> field.setValue(value)
                            else -> Unit
                        }
                    }
                }
                runCatching { form.refreshAppearances() }
                form.needAppearances = false
            }
        }

    /** Bakes the current answers into the page so they cannot be changed. */
    suspend fun flatten(context: Context, file: File) = withContext(Dispatchers.IO) {
        PdfIo.edit(context, file) { document ->
            val form = document.documentCatalog?.acroForm
                ?: throw PdfOperationException("This PDF has no form fields.")
            runCatching { form.refreshAppearances() }
            form.flatten()
        }
    }

    private fun describe(field: PDField): FormField {
        val type = when (field) {
            is PDTextField -> FormFieldType.TEXT
            is PDCheckBox -> FormFieldType.CHECKBOX
            is PDRadioButton -> FormFieldType.RADIO
            is PDChoice -> FormFieldType.CHOICE
            is PDSignatureField -> FormFieldType.SIGNATURE
            is PDPushButton -> FormFieldType.BUTTON
            else -> FormFieldType.OTHER
        }
        val options = when (field) {
            is PDChoice -> runCatching { field.optionsDisplayValues.ifEmpty { field.options } }
                .getOrDefault(emptyList())

            is PDRadioButton -> runCatching { field.onValues.toList() }.getOrDefault(emptyList())
            is PDCheckBox -> listOf("true", "false")
            else -> emptyList()
        }
        val value = when (field) {
            is PDCheckBox -> field.isChecked.toString()
            else -> runCatching { field.valueAsString }.getOrDefault("")
        }
        return FormField(
            name = field.fullyQualifiedName.orEmpty(),
            label = field.alternateFieldName?.takeIf { it.isNotBlank() }
                ?: field.partialName?.takeIf { it.isNotBlank() }
                ?: field.fullyQualifiedName.orEmpty(),
            type = type,
            value = value.orEmpty(),
            options = options,
            readOnly = field.isReadOnly,
            multiline = field is PDTextField && runCatching { field.isMultiline }.getOrDefault(false)
        )
    }

    private const val MAX_FIELDS = 300
}

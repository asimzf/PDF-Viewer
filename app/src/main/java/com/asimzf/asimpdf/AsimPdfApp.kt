package com.asimzf.asimpdf

import android.app.Application
import com.asimzf.asimpdf.util.Workspace
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * Application entry point.
 *
 * asimPDF runs entirely on the device: the manifest declares no INTERNET
 * permission and every operation below is backed by either the platform
 * [android.graphics.pdf.PdfRenderer] or the bundled PDFBox engine.
 */
class AsimPdfApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Loads the PDFBox font/CMap resources from the APK instead of the network.
        PDFBoxResourceLoader.init(applicationContext)
        Workspace.clearStaleTemp(this)
    }
}

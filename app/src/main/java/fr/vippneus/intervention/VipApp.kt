package fr.vippneus.intervention

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class VipApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}

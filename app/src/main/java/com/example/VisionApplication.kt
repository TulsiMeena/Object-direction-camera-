package com.example

import android.app.Application
import android.util.Log
import java.io.File

class VisionApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initStorageDirectories()
    }

    private fun initStorageDirectories() {
        try {
            // Ensure ML Kit acceleration storage directory exists to prevent native proto_data_store errors
            val mlkitDir = File(filesDir, "com.google.mlkit.acceleration")
            if (!mlkitDir.exists()) {
                val created = mlkitDir.mkdirs()
                Log.i("VisionApplication", "ML Kit acceleration directory initialized: $created")
            }
        } catch (e: Exception) {
            Log.w("VisionApplication", "Failed to pre-create storage directories", e)
        }
    }
}

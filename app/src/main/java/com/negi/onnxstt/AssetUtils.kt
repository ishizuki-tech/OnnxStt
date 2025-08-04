package com.negi.onnxstt

import android.app.Application
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object AssetUtils {

    fun copyDataDir(application: Application, dirInAssets: String): String {
        Log.i("AssetUtils", "Copying asset dir: $dirInAssets")
        copyAssetsRecursive(application, dirInAssets)
        val newRoot = application.getExternalFilesDir(null)!!.absolutePath
        Log.i("AssetUtils", "External root: $newRoot")
        return newRoot
    }

    private fun copyAssetsRecursive(application: Application, path: String) {
        try {
            val entries = application.assets.list(path) ?: emptyArray()
            if (entries.isEmpty()) {
                copyAssetFile(application, path)
            } else {
                val fullDir = File(application.getExternalFilesDir(null), path)
                if (!fullDir.exists()) fullDir.mkdirs()
                entries.forEach { name ->
                    val child = if (path.isEmpty()) name else "$path/$name"
                    copyAssetsRecursive(application, child)
                }
            }
        } catch (ex: IOException) {
            Log.e("AssetUtils", "Failed to copy assets at $path", ex)
        }
    }

    private fun copyAssetFile(application: Application, assetPath: String) {
        val outFile = File(application.getExternalFilesDir(null), assetPath)
        if (outFile.exists()) return
        outFile.parentFile?.mkdirs()
        try {
            application.assets.open(assetPath).use { input ->
                FileOutputStream(outFile).use { output ->
                    val buf = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(buf)
                        if (read == -1) break
                        output.write(buf, 0, read)
                    }
                }
            }
        } catch (ex: IOException) {
            Log.e("AssetUtils", "Failed to copy asset file: $assetPath", ex)
        }
    }
}

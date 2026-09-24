package br.ufg.akcit.smartglasses.voice

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Manages the asynchronous loading and direct unpacking of the Vosk acoustic model from assets.
 */
class VoskModelManager(private val context: Context) {

    private val tag = "VoskModelManager"
    private var loadedModel: Model? = null

    /**
     * Initializes the Vosk model in a background coroutine.
     * Extracts model files from assets into internal filesDir if not already extracted.
     */
    suspend fun getOrInitModel(assetModelName: String = "model-pt"): Model = withContext(Dispatchers.IO) {
        loadedModel?.let { return@withContext it }

        val targetDir = File(context.filesDir, assetModelName)
        val markerFile = File(targetDir, "final.mdl")

        if (!targetDir.exists() || !markerFile.exists()) {
            Log.d(tag, "Extracting Vosk model from assets/$assetModelName to ${targetDir.absolutePath}")
            targetDir.mkdirs()
            copyAssetFolder(context.assets, assetModelName, targetDir)
        }

        Log.d(tag, "Loading Vosk Model from ${targetDir.absolutePath}")
        val model = Model(targetDir.absolutePath)
        loadedModel = model
        model
    }

    private fun copyAssetFolder(assetManager: AssetManager, fromAssetPath: String, toDir: File) {
        val files = assetManager.list(fromAssetPath) ?: return
        if (!toDir.exists()) {
            toDir.mkdirs()
        }

        for (file in files) {
            val subAssetPath = if (fromAssetPath.isEmpty()) file else "$fromAssetPath/$file"
            val subDest = File(toDir, file)
            val subList = assetManager.list(subAssetPath)
            if (subList != null && subList.isNotEmpty()) {
                // Subdirectory (e.g. ivector)
                copyAssetFolder(assetManager, subAssetPath, subDest)
            } else {
                // File
                copyAssetFile(assetManager, subAssetPath, subDest)
            }
        }
    }

    private fun copyAssetFile(assetManager: AssetManager, fromAssetPath: String, toFile: File) {
        var input: InputStream? = null
        var output: OutputStream? = null
        try {
            input = assetManager.open(fromAssetPath)
            toFile.parentFile?.mkdirs()
            output = FileOutputStream(toFile)
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
            }
            output.flush()
        } catch (e: Exception) {
            Log.e(tag, "Failed to copy asset file: $fromAssetPath to ${toFile.absolutePath}", e)
            throw e
        } finally {
            input?.close()
            output?.close()
        }
    }

    fun isModelLoaded(): Boolean = loadedModel != null

    fun release() {
        loadedModel = null
    }
}

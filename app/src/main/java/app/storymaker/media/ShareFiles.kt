package app.storymaker.media

import android.content.Context
import app.storymaker.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** The files handed to Instagram for one share. */
data class SharedImages(val background: File, val sticker: File?)

/** Unique share file name, e.g. `story_1728650000000_3f2a9c1e.jpg`, so Instagram never reuses a cached file. */
fun shareFileName(prefix: String, extension: String, timeMillis: Long, random: String): String =
    "${prefix}_${timeMillis}_$random.$extension"

/**
 * Writes share files into `cache/share/` (exposed through the FileProvider). Files are never
 * deleted at share time, because Instagram may read them late; cleanup at app start removes old ones.
 */
class ShareFiles internal constructor(
    private val directory: File,
    private val io: CoroutineDispatcher,
) {
    @Inject
    constructor(@ApplicationContext context: Context, @IoDispatcher io: CoroutineDispatcher) :
        this(File(context.cacheDir, SHARE_DIRECTORY), io)

    /** Writes the background (JPEG) and, if present, the caption sticker (PNG). Throws IOException on failure. */
    suspend fun write(
        writeBackground: (OutputStream) -> Unit,
        writeSticker: ((OutputStream) -> Unit)?,
        timeMillis: Long = System.currentTimeMillis(),
    ): SharedImages = withContext(io) {
        directory.mkdirs()
        val background = File(directory, shareFileName("story", "jpg", timeMillis, randomSuffix()))
        background.outputStream().use(writeBackground)
        val sticker = writeSticker?.let { write ->
            File(directory, shareFileName("caption", "png", timeMillis, randomSuffix())).also { file ->
                file.outputStream().use(write)
            }
        }
        SharedImages(background, sticker)
    }

    private fun randomSuffix() = UUID.randomUUID().toString().take(8)

    companion object {
        /** Must match `res/xml/share_paths.xml`. */
        const val SHARE_DIRECTORY = "share"
    }
}

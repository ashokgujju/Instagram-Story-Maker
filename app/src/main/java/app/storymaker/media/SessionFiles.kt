package app.storymaker.media

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * File layout of editing sessions under [root] (`noBackupFilesDir` in the app):
 *
 * ```
 * root/active_session            ID of the current session
 * root/sessions/<id>/capture.jpg normalized photo
 * root/sessions/<id>/session.json snapshot
 * ```
 */
class SessionFiles(private val root: File) {
    val sessionsDir: File get() = File(root, "sessions")
    val activeSessionFile: File get() = File(root, "active_session")

    fun sessionDir(id: String) = File(sessionsDir, id)
    fun captureFile(id: String) = File(sessionDir(id), "capture.jpg")
    fun snapshotFile(id: String) = File(sessionDir(id), "session.json")
}

/**
 * Writes to a temporary sibling file, syncs it, then atomically renames it over this file.
 * A crash mid-write leaves the previous contents intact.
 */
fun File.writeAtomically(write: (OutputStream) -> Unit) {
    val temp = File(parentFile, "$name.tmp")
    FileOutputStream(temp).use { out ->
        write(out)
        out.flush()
        out.fd.sync()
    }
    Files.move(temp.toPath(), toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}

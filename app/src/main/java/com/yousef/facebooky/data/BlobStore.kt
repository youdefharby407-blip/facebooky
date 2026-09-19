package com.yousef.facebooky.data

import android.content.Context
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

/**
 * Media storage on Firestore only (no Cloud Storage bucket, no paid plan).
 *
 *   blobs/{id}                 ownerUid, kind, mime, size, chunks, createdAt
 *   blobs/{id}/chunks/{i}      i, data (bytes, <= 900 KB each)
 *
 * Messages reference a file as "blob:{id}". Every file is cached on the phone after the
 * first download (and the sender keeps its own copy), so it is only fetched once per device.
 */
class BlobStore private constructor(context: Context) {

    private val db = FirebaseFirestore.getInstance()
    private val dir = File(context.filesDir, "blobs").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val inFlight = HashMap<String, Deferred<File>>()

    /** Uploads [bytes] and returns its reference ("blob:{id}"). */
    suspend fun upload(
        ownerUid: String,
        kind: String,
        mime: String,
        bytes: ByteArray,
        onProgress: (Float) -> Unit = {},
    ): String =
        withContext(Dispatchers.IO) {
            require(bytes.isNotEmpty()) { "empty file" }
            val ref = db.collection(FirebasePaths.BLOBS).document()
            val chunks = (bytes.size + CHUNK_BYTES - 1) / CHUNK_BYTES
            ref.set(
                mapOf(
                    "ownerUid" to ownerUid,
                    "kind" to kind,
                    "mime" to mime,
                    "size" to bytes.size.toLong(),
                    "chunks" to chunks.toLong(),
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            for (i in 0 until chunks) {
                val part = bytes.copyOfRange(i * CHUNK_BYTES, min(bytes.size, (i + 1) * CHUNK_BYTES))
                ref.collection(FirebasePaths.CHUNKS).document(i.toString())
                    .set(mapOf("i" to i.toLong(), "data" to Blob.fromBytes(part)))
                    .await()
                onProgress((i + 1f) / chunks)
            }
            File(dir, ref.id).writeBytes(bytes) // sender never needs to download its own file
            "$SCHEME:${ref.id}"
        }

    /** Local file for a "blob:{id}" reference, downloading it once if needed. */
    suspend fun file(reference: String): File {
        val id = reference.removePrefix("$SCHEME:")
        require(ID_REGEX.matches(id)) { "bad reference" }
        File(dir, id).takeIf { it.exists() && it.length() > 0 }?.let { return it }
        val job = lock.withLock { inFlight.getOrPut(id) { scope.async { download(id) } } }
        try {
            return job.await()
        } finally {
            lock.withLock { if (inFlight[id] === job && job.isCompleted) inFlight.remove(id) }
        }
    }

    /** Path usable by MediaPlayer.setDataSource: a local file for blobs, the URL otherwise. */
    suspend fun playablePath(reference: String): String =
        if (isRef(reference)) file(reference).absolutePath else reference

    private suspend fun download(id: String): File {
        val meta = db.collection(FirebasePaths.BLOBS).document(id).get().await()
        val chunks = meta.getLong("chunks")?.toInt() ?: error("file not found")
        val tmp = File(dir, "$id.part")
        tmp.outputStream().use { out ->
            for (i in 0 until chunks) {
                val chunk = meta.reference.collection(FirebasePaths.CHUNKS).document(i.toString()).get().await()
                val bytes = chunk.getBlob("data")?.toBytes() ?: error("file is still uploading")
                out.write(bytes)
            }
        }
        val target = File(dir, id)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        return target
    }

    companion object {
        const val SCHEME = "blob"
        const val CHUNK_BYTES = 900_000
        private val ID_REGEX = Regex("[A-Za-z0-9]{10,40}")

        fun isRef(value: String?) = value != null && value.startsWith("$SCHEME:")

        @Volatile private var instance: BlobStore? = null
        fun get(context: Context): BlobStore =
            instance ?: synchronized(this) { instance ?: BlobStore(context.applicationContext).also { instance = it } }
    }
}

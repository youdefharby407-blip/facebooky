package com.yousef.facebooky.data

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await

class StorageRepository(private val storage: FirebaseStorage = FirebaseStorage.getInstance()) {

    suspend fun uploadBytes(path: String, bytes: ByteArray, contentType: String): String {
        val ref = storage.reference.child(path)
        ref.putBytes(bytes, metadata(contentType)).await()
        return ref.downloadUrl.await().toString()
    }

    suspend fun uploadFile(path: String, uri: Uri, contentType: String): String {
        val ref = storage.reference.child(path)
        ref.putFile(uri, metadata(contentType)).await()
        return ref.downloadUrl.await().toString()
    }

    private fun metadata(contentType: String) = StorageMetadata.Builder().setContentType(contentType).build()
}

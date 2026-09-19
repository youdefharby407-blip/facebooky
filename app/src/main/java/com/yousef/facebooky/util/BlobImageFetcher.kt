package com.yousef.facebooky.util

import android.content.Context
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.yousef.facebooky.data.BlobStore
import okio.buffer
import okio.source

/** Lets Coil (AsyncImage) load "blob:{id}" images stored in Firestore. */
class BlobImageFetcher(
    private val context: Context,
    private val reference: String,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val file = BlobStore.get(context).file(reference)
        return SourceResult(
            source = ImageSource(file.source().buffer(), context),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.scheme == BlobStore.SCHEME) BlobImageFetcher(context, data.toString()) else null
    }
}

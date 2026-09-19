package com.yousef.facebooky

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.yousef.facebooky.util.BlobImageFetcher

class FaceBookyApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(BlobImageFetcher.Factory(this@FaceBookyApp)) }
            .crossfade(true)
            .build()
}

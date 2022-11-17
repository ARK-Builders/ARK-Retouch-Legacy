package space.taran.arkretouch.utils

import android.graphics.Bitmap
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.DiskCacheStrategy

fun RequestBuilder<Bitmap>.noCache() =
    this
        .diskCacheStrategy(DiskCacheStrategy.NONE)
        .skipMemoryCache(true)

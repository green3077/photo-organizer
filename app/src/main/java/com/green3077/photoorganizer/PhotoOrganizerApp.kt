package com.green3077.photoorganizer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

/** 동영상 썸네일(첫 프레임)도 사진처럼 Coil로 바로 로드할 수 있도록 기본 ImageLoader에 디코더를 등록. */
class PhotoOrganizerApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
}

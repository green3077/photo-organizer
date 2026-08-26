package com.green3077.photoorganizer.model

data class PhotoDetails(
    val displayName: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long?,
    val locationLabel: String?
)

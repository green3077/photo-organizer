package com.green3077.photoorganizer.data

import android.content.Context

/**
 * 사진을 휴지통으로 보낸 시각을 기기에 직접 기록해 둔다. MediaStore가 자체적으로
 * 관리하는 트래시 만료 기간(기기·제조사마다 달라 신뢰할 수 없음)과 무관하게,
 * 이 앱은 항상 [RETENTION_DAYS]일이 지나면 완전삭제하도록 스스로 추적한다.
 */
object TrashTracker {
    const val RETENTION_DAYS = 14
    private const val PREFS_NAME = "trash_prefs"
    private const val KEY_PREFIX = "trashed_at_"

    fun recordTrashed(context: Context, ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val editor = prefs.edit()
        ids.forEach { editor.putLong(KEY_PREFIX + it, now) }
        editor.apply()
    }

    fun clear(context: Context, ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        ids.forEach { editor.remove(KEY_PREFIX + it) }
        editor.apply()
    }

    /** 기록이 없으면(휴지통 기능 도입 전에 트래시된 경우 등) 지금 막 버려진 것으로 간주해 새로 기록한다. */
    fun trashedAtOrNow(context: Context, id: Long): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getLong(KEY_PREFIX + id, -1L)
        if (existing != -1L) return existing
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_PREFIX + id, now).apply()
        return now
    }
}

package com.green3077.photoorganizer.data

import android.content.Context

/**
 * 사진 썸네일을 더블탭하면 즐겨찾기로 표시해 기기에 기록해 둔다. 홈 화면의
 * "즐겨찾기" 메뉴는 이 기록을 기준으로 사진을 따로 모아 보여준다.
 */
object FavoriteTracker {
    private const val PREFS_NAME = "favorite_prefs"
    private const val KEY_PREFIX = "favorite_"

    fun isFavorite(context: Context, id: Long): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_PREFIX + id, false)

    /** 즐겨찾기 상태를 뒤집고, 뒤집은 뒤의 새 상태를 돌려준다. */
    fun toggle(context: Context, id: Long): Boolean {
        val nowFavorite = !isFavorite(context, id)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (nowFavorite) prefs.edit().putBoolean(KEY_PREFIX + id, true).apply()
        else prefs.edit().remove(KEY_PREFIX + id).apply()
        return nowFavorite
    }
}

package com.green3077.photoorganizer

import com.green3077.photoorganizer.data.FavoriteTracker
import com.green3077.photoorganizer.model.Photo
import com.green3077.photoorganizer.util.DateFormat
import java.time.LocalDate

/** 사진 썸네일을 더블탭해 즐겨찾기로 표시한 사진만 모아서 보여주는 화면(홈 화면 "즐겨찾기" 메뉴). */
class FavoritesActivity : BasePhotoDetailActivity() {

    override fun parseExtras(): Boolean = true

    override fun screenTitle(): String = getString(R.string.title_favorites)

    override suspend fun loadPhotosByDate(): Map<LocalDate, List<Photo>> =
        repository.loadAllPhotos()
            .filter { FavoriteTracker.isFavorite(this, it.id) }
            .groupBy { it.dateTaken }
            .toSortedMap(compareByDescending { it })

    override fun sectionLabel(date: LocalDate): String = DateFormat.fullDateLabel(date)
}

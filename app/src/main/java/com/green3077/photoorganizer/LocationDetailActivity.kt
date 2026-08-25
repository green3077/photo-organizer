package com.green3077.photoorganizer

import com.green3077.photoorganizer.model.Photo
import com.green3077.photoorganizer.util.DateFormat
import java.time.LocalDate

/**
 * 한 장소 그룹에 속한 사진들을 날짜별로 묶어 보여준다. 장소 그룹은 (클러스터링 결과가 아니라)
 * 사진 ID 목록으로 전달받아 매번 Repository에서 다시 걸러낸다 — static 홀더에 의존하지 않아
 * 프로세스가 재생성돼도 안전하다.
 */
class LocationDetailActivity : BasePhotoDetailActivity() {

    private lateinit var photoIds: LongArray
    private var placeName: String = ""

    override fun parseExtras(): Boolean {
        val ids = intent.getLongArrayExtra(EXTRA_PHOTO_IDS)
        if (ids == null || ids.isEmpty()) return false
        photoIds = ids
        placeName = intent.getStringExtra(EXTRA_PLACE_NAME) ?: getString(R.string.title_location_detail)
        return true
    }

    override fun screenTitle(): String = placeName

    override suspend fun loadPhotosByDate(): Map<LocalDate, List<Photo>> {
        val idSet = photoIds.toSet()
        return repository.loadAllPhotos()
            .filter { it.id in idSet }
            .groupBy { it.dateTaken }
            .toSortedMap(compareByDescending { it })
    }

    override fun sectionLabel(date: LocalDate): String = DateFormat.fullDateLabel(date)

    companion object {
        const val EXTRA_PHOTO_IDS = "extra_photo_ids"
        const val EXTRA_PLACE_NAME = "extra_place_name"
    }
}

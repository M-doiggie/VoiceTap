package com.voicetap.data

import android.graphics.Rect
import com.voicetap.data.db.RegionDao
import com.voicetap.data.db.RegionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class VoiceRegion(
    val id: Long,
    val phrase: String,
    val appPackage: String,
    val appLabel: String,
    val rect: Rect,
    val orientation: Int,
)

@Singleton
class RegionRepository @Inject constructor(private val dao: RegionDao) {

    fun observeRegions(): Flow<List<VoiceRegion>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getRegions(): List<VoiceRegion> = dao.getAll().map { it.toDomain() }

    suspend fun saveRegion(
        phrase: String,
        appPackage: String,
        appLabel: String,
        rect: Rect,
        orientation: Int,
    ): Long = dao.insert(
        RegionEntity(
            phrase = phrase.trim().lowercase(),
            appPackage = appPackage,
            appLabel = appLabel,
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom,
            orientation = orientation,
        )
    )

    suspend fun deleteRegion(id: Long) = dao.deleteById(id)

    private fun RegionEntity.toDomain() = VoiceRegion(
        id = id,
        phrase = phrase,
        appPackage = appPackage,
        appLabel = appLabel,
        rect = Rect(left, top, right, bottom),
        orientation = orientation,
    )
}

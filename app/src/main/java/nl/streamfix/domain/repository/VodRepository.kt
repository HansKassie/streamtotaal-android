package nl.streamfix.domain.repository

import nl.streamfix.domain.model.LiveCategory
import nl.streamfix.domain.model.VodDetail
import nl.streamfix.domain.model.VodItem
import nl.streamfix.domain.util.AppResult

interface VodRepository {
    suspend fun getCategories(): AppResult<List<LiveCategory>>

    suspend fun getManageableCategories(): AppResult<List<LiveCategory>> = getCategories()
    suspend fun getItems(categoryId: String): AppResult<List<VodItem>>
    suspend fun getAllItems(): AppResult<List<VodItem>>
    suspend fun getDetail(vodId: String): AppResult<VodDetail>
    fun streamUrl(vodId: String, extension: String): String?
}

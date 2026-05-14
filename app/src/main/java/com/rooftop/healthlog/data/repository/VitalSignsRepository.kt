package com.rooftop.healthlog.data.repository

import com.rooftop.healthlog.data.local.dao.VitalSignsDao
import com.rooftop.healthlog.data.local.entity.VitalSignsRecord
import kotlinx.coroutines.flow.Flow

class VitalSignsRepository(private val dao: VitalSignsDao) {
    suspend fun insert(r: VitalSignsRecord): Long = dao.insert(r)
    fun latest(): Flow<VitalSignsRecord?> = dao.getLatest()
    fun between(start: Long, end: Long): Flow<List<VitalSignsRecord>> = dao.getBetween(start, end)
    suspend fun getWeightInRange(start: Long, end: Long): VitalSignsRecord? =
        dao.getWeightInRange(start, end)
    fun all(): Flow<List<VitalSignsRecord>> = dao.getAll()
}

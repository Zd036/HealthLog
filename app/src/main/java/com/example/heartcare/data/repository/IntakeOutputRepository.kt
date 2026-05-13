package com.example.heartcare.data.repository

import com.example.heartcare.data.local.dao.IntakeOutputDao
import com.example.heartcare.data.local.entity.CustomCategory
import com.example.heartcare.data.local.entity.IntakeOutputRecord
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class IntakeOutputRepository(private val dao: IntakeOutputDao) {

    /** 含水量百分比（内置） */
    val builtinWaterContent: Map<String, Float> = mapOf(
        "米饭" to 70f,
        "馒头" to 40f,
        "蔬菜" to 90f,
        "苹果" to 85f,
        "香蕉" to 75f,
        "橙子" to 87f,
        "牛奶" to 100f,
        "汤" to 95f,
        "粥" to 85f
    )

    /** 摄入类型（液体直接 ml） */
    val intakeCategories: List<String> = listOf(
        "饮用水", "汤", "粥", "牛奶", "水果", "米饭", "馒头", "蔬菜"
    )

    /** 排出类型 */
    val outputCategories: List<String> = listOf("尿液", "大便")

    /** 液体类（输入毫升） */
    val liquidCategories: Set<String> = setOf("饮用水", "汤", "粥", "牛奶", "尿液")

    /** 固体类（输入克数，按含水量换算） */
    val solidCategories: Set<String> = setOf("米饭", "馒头", "蔬菜", "水果", "苹果", "香蕉", "橙子")

    suspend fun insert(record: IntakeOutputRecord) = dao.insert(record)
    suspend fun delete(record: IntakeOutputRecord) = dao.delete(record)
    suspend fun update(record: IntakeOutputRecord) = dao.update(record)

    /** 今日（7:00 - 次日 7:00） */
    fun todayRecords(): Flow<List<IntakeOutputRecord>> {
        val (s, e) = todayRange()
        return dao.getBetween(s, e)
    }

    fun records(start: Long, end: Long): Flow<List<IntakeOutputRecord>> =
        dao.getBetween(start, end)

    fun all(): Flow<List<IntakeOutputRecord>> = dao.getAll()

    suspend fun addCategory(c: CustomCategory) = dao.insertCategory(c)
    fun getCategories(type: String): Flow<List<CustomCategory>> = dao.getCategories(type)

    /** 计算 7:00 为分界的"当天"开始结束时间 */
    fun todayRange(): Pair<Long, Long> {
        val now = Calendar.getInstance()
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis > now.timeInMillis) add(Calendar.DAY_OF_YEAR, -1)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        return start.timeInMillis to end.timeInMillis
    }
}

package com.rooftop.healthlog.data.repository

import com.rooftop.healthlog.data.local.dao.IntakeOutputDao
import com.rooftop.healthlog.data.local.entity.CustomCategory
import com.rooftop.healthlog.data.local.entity.IntakeOutputRecord
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class IntakeOutputRepository(private val dao: IntakeOutputDao) {

    /** 含水量百分比（内置） */
    val builtinWaterContent: Map<String, Float> = mapOf(
        "米饭" to 70f,
        "馒头/饼" to 40f,
        "粥/稀饭" to 85f,
        "蔬菜" to 90f,
        "鸡蛋" to 70f,
        "肉类" to 50f,
        "鱼/虾" to 70f,
        "水果" to 85f,
        "牛奶" to 100f,
        "坚果" to 10f
    )

    /** 摄入类型（液体直接 ml） */
    val intakeCategories: List<String> = listOf(
        "饮用水", "米饭", "馒头/饼", "粥/稀饭", "蔬菜", "鸡蛋", "肉类", "鱼/虾", "水果", "牛奶", "坚果"
    )

    /** 排出类型 */
    val outputCategories: List<String> = listOf("尿液", "大便")

    /** 液体类（输入毫升） */
    val liquidCategories: Set<String> = setOf("饮用水", "牛奶", "尿液")

    /** 固体类（输入克数，按含水量换算） */
    val solidCategories: Set<String> = setOf("米饭", "馒头/饼", "粥/稀饭", "蔬菜", "鸡蛋", "肉类", "鱼/虾", "水果", "坚果")

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

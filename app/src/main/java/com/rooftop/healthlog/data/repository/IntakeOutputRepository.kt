package com.rooftop.healthlog.data.repository

import com.rooftop.healthlog.data.local.dao.IntakeOutputDao
import com.rooftop.healthlog.data.local.entity.CustomCategory
import com.rooftop.healthlog.data.local.entity.IntakeOutputRecord
import com.rooftop.healthlog.utils.DateUtils
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

    /** 今日（自然日 00:00 - 次日 00:00） */
    fun todayRecords(): Flow<List<IntakeOutputRecord>> {
        val (s, e) = todayRange()
        return dao.getBetween(s, e)
    }

    fun records(start: Long, end: Long): Flow<List<IntakeOutputRecord>> =
        dao.getBetween(start, end)

    /** 历史记录页仅取最近 500 条，避免全量数据拖慢列表。 */
    fun getRecentRecordsForDisplay(): Flow<List<IntakeOutputRecord>> = dao.getRecentRecords()

    /** 导出功能仍需读取全量数据。 */
    fun getAllRecordsForExport(): Flow<List<IntakeOutputRecord>> = dao.getAllRecords()

    /** 历史页根据总数决定是否显示“最近 500 条”提示。 */
    fun countAllRecords(): Flow<Int> = dao.countAllRecords()

    suspend fun addCategory(c: CustomCategory) = dao.insertCategory(c)
    fun getCategories(type: String): Flow<List<CustomCategory>> = dao.getCategories(type)
    suspend fun deleteCategory(c: CustomCategory) = dao.deleteCategory(c)
    fun getAllCategories(): Flow<List<CustomCategory>> = dao.getAllCategories()

    /** 导入时按业务字段判断重复。 */
    suspend fun countDuplicate(record: IntakeOutputRecord): Int =
        dao.countDuplicate(
            time = record.time,
            type = record.type,
            category = record.category,
            amount = record.amount
        )

    /** 计算自然日 00:00 - 次日 00:00 的时间范围 */
    fun todayRange(): Pair<Long, Long> {
        val start = DateUtils.todayStart()
        val end = Calendar.getInstance().apply {
            timeInMillis = start
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        return start to end
    }
}

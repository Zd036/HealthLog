package com.rooftop.healthlog.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {

    private val ymd = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val ymdhm = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val hm = SimpleDateFormat("HH:mm", Locale.CHINA)
    private val mdhm = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)
    private val fullZh = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
    private val md = SimpleDateFormat("M/d", Locale.CHINA)

    fun formatYmd(time: Long): String = ymd.format(Date(time))
    fun formatYmdHm(time: Long): String = ymdhm.format(Date(time))
    fun formatHm(time: Long): String = hm.format(Date(time))
    fun formatMdHm(time: Long): String = mdhm.format(Date(time))
    fun formatFullZh(time: Long): String = fullZh.format(Date(time))
    fun formatMd(time: Long): String = md.format(Date(time))

    /** 今天 00:00 的时间戳 */
    fun todayStart(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** N 天前的 00:00 时间戳 */
    fun daysAgoStart(days: Int): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_YEAR, -days)
        return c.timeInMillis
    }

    /** 该时间戳所属日期 00:00 */
    fun dayStartOf(time: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = time }
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** 下次目标时刻（hour:minute）的时间戳（若今日已过则为明日） */
    fun nextTriggerAt(hour: Int, minute: Int): Long {
        val now = System.currentTimeMillis()
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, hour); c.set(Calendar.MINUTE, minute)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }
}

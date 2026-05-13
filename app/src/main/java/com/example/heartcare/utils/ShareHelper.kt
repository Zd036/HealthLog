package com.example.heartcare.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.heartcare.data.local.entity.IntakeOutputRecord
import com.example.heartcare.data.local.entity.MedicationRecord
import com.example.heartcare.data.local.entity.VitalSignsRecord
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 渲染一张"今日健康记录"图片并通过系统分享 */
object ShareHelper {

    private const val WIDTH = 1080
    private const val PADDING = 48
    private const val BG = 0xFFFFFFFF.toInt()
    private const val PRIMARY = 0xFF2196F3.toInt()
    private const val TEXT = 0xFF333333.toInt()
    private const val HINT = 0xFF9E9E9E.toInt()
    private const val DANGER = 0xFFF44336.toInt()
    private const val SUCCESS = 0xFF4CAF50.toInt()
    private const val CARD_BG = 0xFFF5F9FF.toInt()

    /** 渲染并保存图片；返回文件 */
    fun renderDailyReport(
        context: Context,
        dayLabel: String,
        intakeTotal: Int,
        outputTotal: Int,
        stoolCount: Int,
        hasIntakeOutput: Boolean,
        showIntakeOutput: Boolean,
        latestVital: VitalSignsRecord?,
        medications: List<MedicationRecord>
    ): File {
        val sections = buildSections(
            dayLabel, intakeTotal, outputTotal, stoolCount,
            hasIntakeOutput, showIntakeOutput, latestVital, medications
        )
        val height = estimateHeight(sections)
        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BG)
        drawAll(canvas, dayLabel, sections)

        val dir = File(context.cacheDir, "shared")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "heartcare_report_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    /** 保存并弹起系统分享 */
    fun shareDailyReport(
        context: Context,
        dayLabel: String,
        intakeTotal: Int,
        outputTotal: Int,
        stoolCount: Int,
        hasIntakeOutput: Boolean,
        showIntakeOutput: Boolean,
        latestVital: VitalSignsRecord?,
        medications: List<MedicationRecord>
    ) {
        val file = renderDailyReport(
            context, dayLabel, intakeTotal, outputTotal, stoolCount,
            hasIntakeOutput, showIntakeOutput, latestVital, medications
        )
        val uri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "HeartCare · $dayLabel 健康记录")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "分享当天记录")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    // ------- 渲染辅助 -------
    private data class Section(val title: String, val lines: List<Pair<String, Int>>)

    private fun buildSections(
        dayLabel: String,
        intakeTotal: Int,
        outputTotal: Int,
        stoolCount: Int,
        hasIntakeOutput: Boolean,
        showIntakeOutput: Boolean,
        latestVital: VitalSignsRecord?,
        medications: List<MedicationRecord>
    ): List<Section> {
        val list = mutableListOf<Section>()
        if (showIntakeOutput) {
            val io = mutableListOf<Pair<String, Int>>()
            if (!hasIntakeOutput) {
                io += "今日暂无出入量记录" to HINT
            } else {
                io += "总摄入：$intakeTotal ml" to TEXT
                val outputText = if (stoolCount > 0) "（大便 $stoolCount 次）" else ""
                io += "总排出：$outputTotal ml$outputText" to TEXT
                val diff = intakeTotal - outputTotal
                val color = when {
                    diff > 1000 || diff < -1000 -> DANGER
                    diff in -500..500 -> SUCCESS
                    else -> 0xFFFFC107.toInt()
                }
                val sign = if (diff >= 0) "+" else ""
                io += "差值：$sign$diff ml" to color
            }
            list += Section("今日出入量", io)
        }
        val vitalLines = mutableListOf<Pair<String, Int>>()
        if (latestVital == null) {
            vitalLines += "今日暂无体征记录" to HINT
        } else {
            if (latestVital.systolic != null && latestVital.diastolic != null)
                vitalLines += "血压 ${latestVital.systolic}/${latestVital.diastolic} mmHg" to TEXT
            latestVital.heartRate?.let { vitalLines += "心率 $it 次/分" to TEXT }
            latestVital.weight?.let { vitalLines += "体重 ${"%.1f".format(it)} 斤" to TEXT }
            latestVital.bloodSugar?.let {
                val abnormal = it > 11.1f || it < 3.9f
                vitalLines += "血糖 ${"%.1f".format(it)} mmol/L" to if (abnormal) DANGER else TEXT
            }
        }
        list += Section("最近体征", vitalLines)

        val medLines = mutableListOf<Pair<String, Int>>()
        if (medications.isEmpty()) {
            medLines += "今日暂无服药记录" to HINT
        } else {
            val sdf = SimpleDateFormat("HH:mm", Locale.CHINA)
            for (m in medications.take(8)) {
                val status = if (m.status == "taken") "已服" else "未服"
                val color = if (m.status == "taken") SUCCESS else DANGER
                val t = sdf.format(Date(m.scheduledTime))
                medLines += "$t  ${m.medicationName}  ${"%.1f".format(m.dosage)}${m.unit}  [$status]" to color
            }
            if (medications.size > 8) medLines += "... 共 ${medications.size} 条" to HINT
        }
        list += Section("今日用药", medLines)
        return list
    }

    private fun estimateHeight(sections: List<Section>): Int {
        var h = PADDING // top
        h += 90 // title
        h += 30 // subtitle gap
        for (s in sections) {
            h += 40 // top gap
            h += 72 // section title
            h += s.lines.size * 56
            h += 24 // bottom padding
        }
        h += 80 // footer
        h += PADDING
        return h.coerceAtLeast(1200)
    }

    private fun drawAll(canvas: Canvas, dayLabel: String, sections: List<Section>) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PRIMARY
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = HINT
            textSize = 34f
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PRIMARY
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT
            textSize = 38f
        }
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD_BG }

        var y = PADDING + 64f
        canvas.drawText("HeartCare · 今日健康记录", PADDING.toFloat(), y, titlePaint)
        y += 50f
        canvas.drawText(dayLabel, PADDING.toFloat(), y, subPaint)
        y += 30f

        for (s in sections) {
            y += 40f
            val cardTop = y - 6f
            val linesCount = s.lines.size
            val cardBottom = y + 72f + linesCount * 56f + 16f
            val rect = android.graphics.RectF(
                PADDING.toFloat(), cardTop,
                (WIDTH - PADDING).toFloat(), cardBottom
            )
            canvas.drawRoundRect(rect, 24f, 24f, cardPaint)
            y += 60f
            canvas.drawText(s.title, (PADDING + 24).toFloat(), y, sectionPaint)
            y += 24f
            for ((text, color) in s.lines) {
                y += 44f
                linePaint.color = color
                canvas.drawText(text, (PADDING + 24).toFloat(), y, linePaint)
                y += 12f
            }
            y += 16f
        }

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = HINT
            textSize = 28f
        }
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
        val footer = "导出时间：$stamp  ·  HeartCare"
        val bounds = Rect()
        footerPaint.getTextBounds(footer, 0, footer.length, bounds)
        canvas.drawText(
            footer,
            (WIDTH - PADDING - bounds.width()).toFloat(),
            (canvas.height - PADDING / 2).toFloat(),
            footerPaint
        )
        // avoid unused warning
        Color.alpha(BG)
    }
}

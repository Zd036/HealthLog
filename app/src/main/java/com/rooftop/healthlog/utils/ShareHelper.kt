package com.rooftop.healthlog.utils

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.rooftop.healthlog.data.local.entity.MedicationRecord
import com.rooftop.healthlog.ui.history.HistoryViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 渲染一张"今日健康记录"图片并直接调起微信发送 */
object ShareHelper {

    private const val WECHAT_PACKAGE_NAME = "com.tencent.mm"
    private const val WIDTH = 1080
    private const val PADDING = 48
    private const val BG = 0xFFFFFFFF.toInt()
    private const val PRIMARY = 0xFF2196F3.toInt()
    private const val TEXT = 0xFF333333.toInt()
    private const val HINT = 0xFF9E9E9E.toInt()
    private const val DANGER = 0xFFF44336.toInt()
    private const val SUCCESS = 0xFF4CAF50.toInt()
    private const val WARNING = 0xFFFFC107.toInt()
    private const val CARD_BG = 0xFFFFFFFF.toInt()
    private const val PANEL_BG = 0xFFF3F6FA.toInt()
    private const val SECTION_TITLE_HEIGHT = 108
    private const val SECTION_TITLE_ONLY_HEIGHT = 72
    private const val LINE_TEXT_SIZE = 38f
    private const val CARD_BOTTOM_PADDING = 24
    private const val CARD_INNER_HORIZONTAL = 24
    private const val SECTION_TOP_GAP = 40
    private const val ROW_GAP = 10
    private const val MED_COLUMN_GAP = 20
    private const val MED_TIME_SAMPLE = "00:00"
    private const val MED_STATUS_SAMPLE = "[已服用]"
    private const val VITAL_NAME_SAMPLE = "脉率"

    /** 渲染并保存图片；返回文件 */
    fun renderDailyReport(
        context: Context,
        dayLabel: String,
        intakeTotal: Int,
        outputTotal: Int,
        stoolCount: Int,
        hasIntakeOutput: Boolean,
        showIntakeOutput: Boolean,
        recentVitals: List<RecentVitalItem>,
        alerts: List<String>,
        medications: List<MedicationRecord>
    ): File {
        val sections = buildSections(
            dayLabel, intakeTotal, outputTotal, stoolCount,
            hasIntakeOutput, showIntakeOutput, recentVitals, alerts, medications
        )
        val height = estimateHeight(sections)
        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BG)
        drawAll(canvas, dayLabel, sections)

        val dir = File(context.cacheDir, "shared")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "healthlog_report_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    /** 保存并直接调起微信分享 */
    fun shareDailyReport(
        context: Context,
        dayLabel: String,
        intakeTotal: Int,
        outputTotal: Int,
        stoolCount: Int,
        hasIntakeOutput: Boolean,
        showIntakeOutput: Boolean,
        recentVitals: List<RecentVitalItem>,
        alerts: List<String>,
        medications: List<MedicationRecord>
    ) {
        val file = renderDailyReport(
            context, dayLabel, intakeTotal, outputTotal, stoolCount,
            hasIntakeOutput, showIntakeOutput, recentVitals, alerts, medications
        )
        val uri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            setPackage(WECHAT_PACKAGE_NAME)
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "HealthLog · $dayLabel 健康记录")
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            throw IllegalStateException("未安装微信或当前微信版本不支持图片分享")
        }
    }

    // ------- 渲染辅助 -------
    private data class Section(
        val title: String,
        val subtitle: String?,
        val accentColor: Int,
        val rows: List<ShareRow>
    )

    private sealed interface ShareRow {
        data class Paragraph(
            val text: String,
            val color: Int,
            val emphasized: Boolean = false
        ) : ShareRow
        data class Medication(val time: String, val status: String, val names: String, val color: Int) : ShareRow
        data class Vital(
            val name: String,
            val value: String,
            val time: String,
            val delta: String?,
            val color: Int
        ) : ShareRow
    }

    private fun buildSections(
        dayLabel: String,
        intakeTotal: Int,
        outputTotal: Int,
        stoolCount: Int,
        hasIntakeOutput: Boolean,
        showIntakeOutput: Boolean,
        recentVitals: List<RecentVitalItem>,
        alerts: List<String>,
        medications: List<MedicationRecord>
    ): List<Section> {
        val list = mutableListOf<Section>()
        if (showIntakeOutput) {
            val io = mutableListOf<ShareRow>()
            if (!hasIntakeOutput) {
                io += ShareRow.Paragraph("今日未记录", HINT)
            } else {
                io += ShareRow.Paragraph("总摄入：$intakeTotal ml", DANGER, emphasized = true)
                io += ShareRow.Paragraph("总排出：$outputTotal ml", SUCCESS, emphasized = true)
                io += ShareRow.Paragraph("大便次数：$stoolCount 次", TEXT)
                val diff = intakeTotal - outputTotal
                val color = when {
                    diff < 0 -> SUCCESS
                    diff < 200 -> TEXT
                    diff < 500 -> WARNING
                    else -> DANGER
                }
                val sign = if (diff >= 0) "+" else ""
                io += ShareRow.Paragraph("差值：$sign$diff ml", color, emphasized = true)
            }
            list += Section("今日出入量", null, PRIMARY, io)
        }
        val vitalLines = if (recentVitals.isEmpty()) {
            defaultShareVitalRows()
        } else {
            buildShareVitalRows(recentVitals)
        }
        list += Section("今日体征", null, PRIMARY, vitalLines)

        val medLines = mutableListOf<ShareRow>()
        if (medications.isEmpty()) {
            medLines += ShareRow.Paragraph("今日未记录", HINT)
        } else {
            val groupedItems = HistoryViewModel.groupMedicationRecords(medications)
            for (item in groupedItems.take(8)) {
                val status = medicationStatusLabel(item.status)
                val color = if (item.status == MEDICATION_STATUS_TAKEN) SUCCESS else DANGER
                val t = DateUtils.formatHm(item.scheduledTime)
                medLines += ShareRow.Medication(
                    time = t,
                    status = "[$status]",
                    names = item.medicationNames.joinToString("、"),
                    color = color
                )
            }
            if (groupedItems.size > 8) {
                medLines += ShareRow.Paragraph("... 共 ${groupedItems.size} 个时间点", HINT)
            }
        }
        list += Section("今日用药", null, PRIMARY, medLines)

        if (alerts.isNotEmpty()) {
            list += Section("异常提示", null, DANGER, alerts.map { ShareRow.Paragraph(it, DANGER) })
        }
        return list
    }

    private fun estimateHeight(sections: List<Section>): Int {
        val linePaint = bodyTextPaint()
        val monoPaint = medicationTimePaint()
        var h = PADDING // top
        h += 90 // title
        h += 30 // subtitle gap
        for (s in sections) {
            h += SECTION_TOP_GAP
            h += sectionHeaderHeight(s)
            h += measureRowsHeight(s.rows, linePaint, monoPaint)
            h += 28 // 内层信息面板额外留白
            h += CARD_BOTTOM_PADDING
        }
        h += 80 // footer
        h += PADDING
        return h.coerceAtLeast(1200)
    }

    private fun drawAll(canvas: Canvas, dayLabel: String, sections: List<Section>) {
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PRIMARY
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = HINT
            textSize = 34f
        }
        val sectionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sectionSubPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = HINT
            textSize = 30f
        }
        val linePaint = bodyTextPaint()
        val monoPaint = medicationTimePaint()
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD_BG }
        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PANEL_BG }
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        var y = PADDING + 64f
        canvas.drawText("HealthLog · 今日健康记录", PADDING.toFloat(), y, titlePaint)
        y += 50f
        canvas.drawText(dayLabel, PADDING.toFloat(), y, subPaint)
        y += 30f

        for (s in sections) {
            y += SECTION_TOP_GAP.toFloat()
            val cardTop = y - 6f
            val rowsHeight = measureRowsHeight(s.rows, linePaint, monoPaint)
            val headerHeight = sectionHeaderHeight(s).toFloat()
            val panelTop = y + headerHeight
            val panelBottom = panelTop + rowsHeight + 28f
            val cardBottom = panelBottom + CARD_BOTTOM_PADDING - 8f
            val rect = RectF(
                PADDING.toFloat(), cardTop,
                (WIDTH - PADDING).toFloat(), cardBottom
            )
            canvas.drawRoundRect(rect, 24f, 24f, cardPaint)
            drawSectionIcon(
                canvas = canvas,
                left = (PADDING + 24).toFloat(),
                top = y + 20f,
                color = s.accentColor,
                paint = iconPaint
            )
            canvas.drawText(s.title, (PADDING + 82).toFloat(), y + 54f, sectionPaint)
            s.subtitle?.takeIf { it.isNotBlank() }?.let {
                canvas.drawText(it, (PADDING + 82).toFloat(), y + 90f, sectionSubPaint)
            }
            canvas.drawRoundRect(
                RectF(
                    (PADDING + 18).toFloat(),
                    panelTop,
                    (WIDTH - PADDING - 18).toFloat(),
                    panelBottom
                ),
                20f,
                20f,
                panelPaint
            )
            val rowsTop = panelTop + 14f
            y = drawRows(canvas, s.rows, rowsTop, linePaint, monoPaint)
            y += CARD_BOTTOM_PADDING.toFloat()
        }

        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = HINT
            textSize = 28f
        }
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
        val footer = "导出时间：$stamp  ·  HealthLog"
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

    private fun sectionHeaderHeight(section: Section): Int {
        return if (section.subtitle.isNullOrBlank()) SECTION_TITLE_ONLY_HEIGHT else SECTION_TITLE_HEIGHT
    }

    private fun bodyTextPaint(): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT
        textSize = LINE_TEXT_SIZE
    }

    private fun medicationTimePaint(): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT
        textSize = LINE_TEXT_SIZE
        typeface = Typeface.MONOSPACE
    }

    private fun contentLeft(): Float = (PADDING + CARD_INNER_HORIZONTAL).toFloat()

    private fun contentWidth(): Int = WIDTH - PADDING * 2 - CARD_INNER_HORIZONTAL * 2

    private fun measureRowsHeight(
        rows: List<ShareRow>,
        linePaint: TextPaint,
        monoPaint: TextPaint
    ): Int {
        if (rows.isEmpty()) return 0
        var total = 0
        rows.forEachIndexed { index, row ->
            total += measureRowHeight(row, linePaint, monoPaint)
            if (index != rows.lastIndex) total += ROW_GAP
        }
        return total
    }

    private fun measureRowHeight(
        row: ShareRow,
        linePaint: TextPaint,
        monoPaint: TextPaint
    ): Int = when (row) {
        is ShareRow.Paragraph -> paragraphLayout(row.text, linePaint).height
        is ShareRow.Medication -> {
            val namesPaint = linePaint.copyWithColor(row.color)
            val layout = paragraphLayout(row.names, namesPaint, medicationNameWidth(linePaint, monoPaint))
            maxOf(layout.height, singleLineHeight(linePaint))
        }
        is ShareRow.Vital -> {
            val valueLayout = vitalValueLayout(row, linePaint)
            maxOf(valueLayout.height, singleLineHeight(linePaint))
        }
    }

    private fun vitalValueLayout(row: ShareRow.Vital, linePaint: TextPaint): StaticLayout {
        val text = buildVitalValueText(row)
        val paint = bodyTextPaint()
        return paragraphLayout(text, paint, vitalValueWidth(linePaint, medicationTimePaint()))
    }

    private fun buildVitalValueText(row: ShareRow.Vital): CharSequence {
        val text = android.text.SpannableStringBuilder(row.value)
        row.delta?.let {
            text.append(" ")
            val start = text.length
            text.append("(").append(it).append(")")
            text.setSpan(
                android.text.style.ForegroundColorSpan(weightDeltaColor(it)),
                start,
                text.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return text
    }

    private fun defaultShareVitalRows(): List<ShareRow.Vital> = listOf(
        ShareRow.Vital(name = "体重", value = "今日未记录", time = "--:--", delta = null, color = HINT),
        ShareRow.Vital(name = "血压", value = "今日未记录", time = "--:--", delta = null, color = HINT),
        ShareRow.Vital(name = "脉率", value = "今日未记录", time = "--:--", delta = null, color = HINT),
        ShareRow.Vital(name = "血糖", value = "今日未记录", time = "--:--", delta = null, color = HINT)
    )

    private fun buildShareVitalRows(recentVitals: List<RecentVitalItem>): List<ShareRow.Vital> {
        val map = recentVitals.associateBy { it.kind }
        val defaults = defaultShareVitalRows().associateBy { it.name }
        return listOf(
            map[RecentVitalKind.WEIGHT]?.toShareVitalRow() ?: defaults.getValue("体重"),
            map[RecentVitalKind.BLOOD_PRESSURE]?.toShareVitalRow() ?: defaults.getValue("血压"),
            map[RecentVitalKind.HEART_RATE]?.copy(nameText = "脉率")?.toShareVitalRow() ?: defaults.getValue("脉率"),
            map[RecentVitalKind.BLOOD_SUGAR]?.toShareVitalRow() ?: defaults.getValue("血糖")
        )
    }

    private fun RecentVitalItem.toShareVitalRow(): ShareRow.Vital = ShareRow.Vital(
        name = nameText,
        value = valueText,
        time = timeText,
        delta = deltaText,
        color = if (abnormal) DANGER else TEXT
    )

    private fun drawRows(
        canvas: Canvas,
        rows: List<ShareRow>,
        startTop: Float,
        linePaint: TextPaint,
        monoPaint: TextPaint
    ): Float {
        var top = startTop
        rows.forEachIndexed { index, row ->
            top += when (row) {
                is ShareRow.Paragraph -> drawParagraphRow(canvas, row, top, linePaint)
                is ShareRow.Medication -> drawMedicationRow(canvas, row, top, linePaint, monoPaint)
                is ShareRow.Vital -> drawVitalRow(canvas, row, top, linePaint, monoPaint)
            }
            if (index != rows.lastIndex) top += ROW_GAP.toFloat()
        }
        return top
    }

    private fun drawParagraphRow(
        canvas: Canvas,
        row: ShareRow.Paragraph,
        top: Float,
        linePaint: TextPaint
    ): Float {
        val paint = linePaint.copyWithColor(row.color)
        if (row.emphasized) {
            paint.textSize = 40f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val layout = paragraphLayout(row.text, paint)
        canvas.save()
        canvas.translate(contentLeft(), top)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    private fun drawMedicationRow(
        canvas: Canvas,
        row: ShareRow.Medication,
        top: Float,
        linePaint: TextPaint,
        monoPaint: TextPaint
    ): Float {
        val timePaint = monoPaint.copyWithColor(row.color)
        val statusPaint = linePaint.copyWithColor(row.color)
        val namesPaint = linePaint.copyWithColor(row.color)
        val timeWidth = timeColumnWidth(timePaint)
        val statusWidth = statusColumnWidth(statusPaint)
        val left = contentLeft()
        val statusLeft = left + timeWidth + MED_COLUMN_GAP
        val namesLeft = statusLeft + statusWidth + MED_COLUMN_GAP
        val nameLayout = paragraphLayout(row.names, namesPaint, medicationNameWidth(linePaint, monoPaint))
        val firstLineBaseline = top + nameLayout.getLineBaseline(0)

        canvas.drawText(row.time, left, firstLineBaseline, timePaint)
        val centeredStatusLeft = statusLeft + (statusWidth - statusPaint.measureText(row.status)) / 2f
        canvas.drawText(row.status, centeredStatusLeft, firstLineBaseline, statusPaint)

        canvas.save()
        canvas.translate(namesLeft, top)
        nameLayout.draw(canvas)
        canvas.restore()
        return maxOf(nameLayout.height.toFloat(), singleLineHeight(linePaint).toFloat())
    }

    private fun drawVitalRow(
        canvas: Canvas,
        row: ShareRow.Vital,
        top: Float,
        linePaint: TextPaint,
        monoPaint: TextPaint
    ): Float {
        val timePaint = monoPaint.copyWithColor(HINT)
        val namePaint = linePaint.copyWithColor(if (row.color == DANGER) DANGER else HINT)
        val valuePaint = linePaint.copyWithColor(row.color)
        val nameWidth = vitalNameColumnWidth(namePaint)
        val left = contentLeft()
        val valueLeft = left + nameWidth + MED_COLUMN_GAP
        val timeLeft = valueLeft + vitalValueWidth(linePaint, monoPaint) + MED_COLUMN_GAP
        val valueLayout = paragraphLayout(buildVitalValueText(row), valuePaint, vitalValueWidth(linePaint, monoPaint))
        val firstLineBaseline = top + valueLayout.getLineBaseline(0)

        canvas.drawText(row.name, left, firstLineBaseline, namePaint)

        canvas.save()
        canvas.translate(valueLeft, top)
        valueLayout.draw(canvas)
        canvas.restore()
        canvas.drawText(row.time, timeLeft, firstLineBaseline, timePaint)
        return maxOf(valueLayout.height.toFloat(), singleLineHeight(linePaint).toFloat())
    }

    private fun paragraphLayout(
        text: CharSequence,
        paint: TextPaint,
        width: Int = contentWidth()
    ): StaticLayout = StaticLayout.Builder
        .obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .setLineSpacing(10f, 1f)
        .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
        .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
        .build()

    private fun singleLineHeight(paint: TextPaint): Int {
        val metrics = paint.fontMetrics
        return kotlin.math.ceil((metrics.bottom - metrics.top).toDouble()).toInt()
    }

    private fun timeColumnWidth(paint: TextPaint): Float = paint.measureText(MED_TIME_SAMPLE)

    private fun statusColumnWidth(paint: TextPaint): Float = paint.measureText(MED_STATUS_SAMPLE)

    private fun vitalNameColumnWidth(paint: TextPaint): Float = paint.measureText(VITAL_NAME_SAMPLE)

    private fun medicationNameWidth(linePaint: TextPaint, monoPaint: TextPaint): Int {
        val width = contentWidth() -
            timeColumnWidth(monoPaint).toInt() -
            statusColumnWidth(linePaint).toInt() -
            MED_COLUMN_GAP * 2
        return width.coerceAtLeast(120)
    }

    private fun vitalValueWidth(linePaint: TextPaint, monoPaint: TextPaint): Int {
        val width = contentWidth() -
            timeColumnWidth(monoPaint).toInt() -
            vitalNameColumnWidth(linePaint).toInt() -
            MED_COLUMN_GAP * 2
        return width.coerceAtLeast(120)
    }

    private fun weightDeltaColor(deltaText: String): Int = when {
        deltaText.startsWith("+") -> DANGER
        deltaText.startsWith("-") -> SUCCESS
        else -> HINT
    }

    private fun drawSectionIcon(
        canvas: Canvas,
        left: Float,
        top: Float,
        color: Int,
        paint: Paint
    ) {
        paint.color = (color and 0x00FFFFFF) or 0x1F000000
        canvas.drawCircle(left + 18f, top + 18f, 18f, paint)
        paint.color = color
        canvas.drawCircle(left + 18f, top + 18f, 9f, paint)
    }

    private fun TextPaint.copyWithColor(color: Int): TextPaint = TextPaint(this).apply {
        this.color = color
    }
}

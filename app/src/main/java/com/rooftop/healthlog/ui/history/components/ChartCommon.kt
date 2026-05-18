package com.rooftop.healthlog.ui.history.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import com.rooftop.healthlog.ui.theme.BorderGray
import com.rooftop.healthlog.ui.theme.HintGray
import com.rooftop.healthlog.utils.DateUtils

/** 图表区域的几何布局参数 */
data class ChartLayout(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class ChartPoint(
    val dayStart: Long,
    val value: Float?,    // null 表示缺失（不连线）
    val highlight: Color? = null // 异常值高亮
)

/** 根据 canvas 大小计算图表绘制区域 */
fun DrawScope.computeLayout(
    leftPadding: Float = 40f.dpToPx(this),
    rightPadding: Float = 16f.dpToPx(this),
    topPadding: Float = 20f.dpToPx(this),
    bottomPadding: Float = 30f.dpToPx(this)
): ChartLayout = ChartLayout(
    left = leftPadding,
    top = topPadding,
    right = size.width - rightPadding,
    bottom = size.height - bottomPadding
)

fun Float.dpToPx(density: Density): Float = this * density.density

/** 画坐标轴、网格线、Y 轴刻度和 X 轴日期 */
fun DrawScope.drawAxes(
    layout: ChartLayout,
    yTicks: List<Float>,
    yLabelFormatter: (Float) -> String,
    xDayStarts: List<Long>,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    gridColor: Color = BorderGray,
    axisColor: Color = HintGray
) {
    // 绘制 Y 轴网格线及刻度
    if (yTicks.isNotEmpty()) {
        val minY = yTicks.first()
        val maxY = yTicks.last()
        val rangeY = (maxY - minY).takeIf { it > 0f } ?: 1f
        for (v in yTicks) {
            val y = layout.bottom - (v - minY) / rangeY * layout.height
            drawLine(
                color = gridColor,
                start = Offset(layout.left, y),
                end = Offset(layout.right, y),
                strokeWidth = 1f
            )
            val txt = yLabelFormatter(v)
            val tl = textMeasurer.measure(txt, labelStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = txt,
                style = labelStyle,
                topLeft = Offset(layout.left - tl.size.width - 4f, y - tl.size.height / 2f)
            )
        }
    }

    // X 轴底线
    drawLine(
        color = axisColor,
        start = Offset(layout.left, layout.bottom),
        end = Offset(layout.right, layout.bottom),
        strokeWidth = 2f
    )

    // X 轴日期标签
    if (xDayStarts.isNotEmpty()) {
        val step = layout.width / xDayStarts.size.coerceAtLeast(1)
        // 最多显示 7 个标签
        val stride = (xDayStarts.size / 7).coerceAtLeast(1)
        for ((i, t) in xDayStarts.withIndex()) {
            if (i % stride != 0 && i != xDayStarts.size - 1) continue
            val cx = layout.left + step * i + step / 2f
            val txt = DateUtils.formatMd(t)
            val tl = textMeasurer.measure(txt, labelStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = txt,
                style = labelStyle,
                topLeft = Offset(cx - tl.size.width / 2f, layout.bottom + 6f)
            )
        }
    }
}

/** 画带数据点的折线；缺失值（null）处断开 */
fun DrawScope.drawLineChart(
    layout: ChartLayout,
    xCount: Int,
    yMin: Float,
    yMax: Float,
    values: List<Float?>,
    highlights: List<Color?> = emptyList(),
    lineColor: Color,
    normalDotColor: Color = lineColor,
    dotRadiusDp: Float = 4f
) {
    if (xCount <= 0 || values.isEmpty()) return
    val rangeY = (yMax - yMin).takeIf { it > 0f } ?: 1f
    val step = layout.width / xCount
    val dotR = dotRadiusDp.dpToPx(this)

    fun pointAt(i: Int, v: Float): Offset {
        val cx = layout.left + step * i + step / 2f
        val cy = layout.bottom - (v - yMin) / rangeY * layout.height
        return Offset(cx, cy)
    }

    // 连线：相邻非空点
    for (i in 0 until values.size - 1) {
        val a = values[i]; val b = values[i + 1]
        if (a != null && b != null) {
            drawLine(
                color = lineColor,
                start = pointAt(i, a),
                end = pointAt(i + 1, b),
                strokeWidth = 2f.dpToPx(this)
            )
        }
    }
    // 数据点
    for (i in values.indices) {
        val v = values[i] ?: continue
        val hl = highlights.getOrNull(i)
        val center = pointAt(i, v)
        // 正常点（蓝 / 线色）
        drawCircle(color = normalDotColor, radius = dotR, center = center)
        if (hl != null) {
            // 异常值：红色外环
            drawCircle(
                color = hl,
                radius = dotR + 2f.dpToPx(this),
                center = center,
                style = Stroke(width = 2.5f.dpToPx(this))
            )
        }
    }
}

/** 零线（虚线） */
fun DrawScope.drawZeroLine(
    layout: ChartLayout,
    yMin: Float,
    yMax: Float,
    color: Color = HintGray
) {
    val range = (yMax - yMin).takeIf { it > 0f } ?: return
    if (0f < yMin || 0f > yMax) return
    val y = layout.bottom - (0f - yMin) / range * layout.height
    drawLine(
        color = color,
        start = Offset(layout.left, y),
        end = Offset(layout.right, y),
        strokeWidth = 1.5f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
    )
}

/** 柱状图 */
fun DrawScope.drawBars(
    layout: ChartLayout,
    xCount: Int,
    values: List<Float?>,      // 0~1 百分比；null 缺失（空白）
    colorFor: (Float) -> Color,
    yMax: Float = 1f
) {
    if (xCount <= 0 || values.isEmpty()) return
    val step = layout.width / xCount
    val barW = step * 0.6f
    for (i in values.indices) {
        val v = values[i] ?: continue
        val clamped = v.coerceIn(0f, yMax)
        val h = clamped / yMax * layout.height
        val cx = layout.left + step * i + step / 2f
        drawRect(
            color = colorFor(clamped),
            topLeft = Offset(cx - barW / 2f, layout.bottom - h),
            size = Size(barW, h)
        )
    }
}

/** 生成覆盖 [days] 天的日期起点列表（升序，含今天） */
fun dayStartsForRange(days: Int): List<Long> {
    val today = DateUtils.todayStart()
    return (0 until days).map { idx ->
        DateUtils.dayStartOf(today - (days - 1 - idx) * 24L * 3600_000L)
    }
}

/** 根据日期起点对列表分桶：每天取平均（或 null） */
fun <T> bucketDaily(
    items: List<T>,
    timeOf: (T) -> Long,
    valueOf: (T) -> Float?,
    dayStarts: List<Long>
): List<Float?> {
    val oneDay = 24L * 3600_000L
    return dayStarts.map { ds ->
        val end = ds + oneDay
        val vals = items.mapNotNull { it.takeIf { timeOf(it) in ds until end }?.let(valueOf) }
        if (vals.isEmpty()) null else vals.average().toFloat()
    }
}

/** 聚合每日出入量差值（摄入 - 排出，非大便） */
fun dailyIntakeOutputDiff(
    records: List<com.rooftop.healthlog.data.local.entity.IntakeOutputRecord>,
    dayStarts: List<Long>
): List<Float?> {
    val oneDay = 24L * 3600_000L
    return dayStarts.map { ds ->
        val end = ds + oneDay
        val inToday = records.filter { it.time in ds until end }
        if (inToday.isEmpty()) return@map null
        var intake = 0f; var output = 0f
        for (r in inToday) {
            when {
                r.type == "intake" -> intake += r.amount
                r.type == "output" && r.category != "大便" -> output += r.amount
            }
        }
        intake - output
    }
}

/** 根据差值计算颜色（200 以下绿、500 以下黄、更多红） */
fun intakeDiffColor(
    v: Float,
    green: Color,
    yellow: Color,
    red: Color
): Color = when {
    v < 200f -> green
    v < 500f -> yellow
    else -> red
}

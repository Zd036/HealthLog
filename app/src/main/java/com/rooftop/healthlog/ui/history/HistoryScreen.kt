package com.rooftop.healthlog.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rooftop.healthlog.data.local.entity.IntakeOutputRecord
import com.rooftop.healthlog.data.local.entity.VitalSignsRecord
import com.rooftop.healthlog.ui.appViewModel
import com.rooftop.healthlog.ui.history.components.*
import com.rooftop.healthlog.ui.settings.SettingsViewModel
import com.rooftop.healthlog.ui.theme.HintGray
import com.rooftop.healthlog.ui.theme.PrimaryBlue
import com.rooftop.healthlog.utils.DateUtils

@Composable
fun HistoryScreen(onOpenCompliance: () -> Unit = {}) { // 保留参数避免破坏调用方
    val vm: HistoryViewModel = appViewModel()
    val settingsVm: SettingsViewModel = appViewModel()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val io by vm.intakeOutput.collectAsStateWithLifecycle()
    val ioChartRecords by vm.intakeChartRecords.collectAsStateWithLifecycle()
    val vitals by vm.vitals.collectAsStateWithLifecycle()
    val vitalChartRecords by vm.vitalChartRecords.collectAsStateWithLifecycle()
    val groupedMeds by vm.groupedMeds.collectAsStateWithLifecycle()
    val medChartRecords by vm.medChartRecords.collectAsStateWithLifecycle()
    val intakeRange by vm.intakeRange.collectAsStateWithLifecycle()
    val vitalRange by vm.vitalRange.collectAsStateWithLifecycle()
    val medRange by vm.medRange.collectAsStateWithLifecycle()
    val chartPeriod by vm.chartPeriod.collectAsStateWithLifecycle()
    val intakeTotalCount by vm.intakeTotalCount.collectAsStateWithLifecycle()
    val vitalTotalCount by vm.vitalsTotalCount.collectAsStateWithLifecycle()
    val medTotalCount by vm.medsTotalCount.collectAsStateWithLifecycle()

    val enableIO = settings.enableIntakeOutput
    val tabs = remember(enableIO) {
        if (enableIO) listOf("出入量", "体征", "服药")
        else listOf("体征", "服药")
    }
    var tab by remember(tabs) { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp)) {
        Text("历史记录", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, name ->
                Tab(selected = tab == i, onClick = { tab = i },
                    text = { Text(name, style = MaterialTheme.typography.titleMedium) })
            }
        }
        Spacer(Modifier.height(8.dp))

        val actualTabName = tabs[tab]
        when (actualTabName) {
            "出入量" -> RangeFilter(intakeRange) { vm.setIntakeRange(it) }
            "体征"   -> RangeFilter(vitalRange) { vm.setVitalRange(it) }
            "服药"   -> RangeFilter(medRange) { vm.setMedRange(it) }
        }

        Spacer(Modifier.height(8.dp))
        PeriodSelector(chartPeriod) { vm.setChartPeriod(it) }

        Spacer(Modifier.height(8.dp))

        when (actualTabName) {
            "出入量" -> IntakeOutputTab(
                records = io,
                chartRecords = ioChartRecords,
                range = intakeRange,
                period = chartPeriod,
                totalCount = intakeTotalCount,
                onDelete = vm::deleteIntakeOutput
            )
            "体征"   -> VitalsTab(
                records = vitals,
                chartRecords = vitalChartRecords,
                range = vitalRange,
                period = chartPeriod,
                totalCount = vitalTotalCount
            )
            "服药"   -> MedicationTab(
                records = groupedMeds,
                chartRecords = medChartRecords,
                range = medRange,
                period = chartPeriod,
                totalCount = medTotalCount
            )
        }
    }
}

@Composable
private fun RangeFilter(current: DateRange, onPick: (DateRange) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (r in DateRange.values()) {
            FilterChip(
                selected = current == r,
                onClick = { onPick(r) },
                label = { Text(r.label, style = MaterialTheme.typography.bodyLarge) }
            )
        }
    }
}

@Composable
private fun PeriodSelector(period: ChartPeriod, onPick: (ChartPeriod) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("图表维度：", style = MaterialTheme.typography.bodyMedium)
        SegmentedButtons(
            items = ChartPeriod.values().toList(),
            selected = period,
            labelOf = { it.label },
            onSelect = onPick
        )
    }
}

@Composable
private fun <T> SegmentedButtons(
    items: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit
) {
    Row {
        for ((i, it) in items.withIndex()) {
            val isFirst = i == 0; val isLast = i == items.size - 1
            val shape = when {
                items.size == 1 -> RoundedCornerShape(8.dp)
                isFirst -> RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                isLast -> RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                else -> RoundedCornerShape(0.dp)
            }
            val isSelected = it == selected
            Surface(
                shape = shape,
                color = if (isSelected) PrimaryBlue else PrimaryBlue.copy(alpha = 0.08f),
                onClick = { onSelect(it) },
                modifier = Modifier.height(40.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxHeight()
                ) {
                    Text(
                        labelOf(it),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else PrimaryBlue
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun IntakeOutputTab(
    records: List<IntakeOutputRecord>,
    chartRecords: List<IntakeOutputRecord>,
    range: DateRange,
    period: ChartPeriod,
    totalCount: Int,
    onDelete: (IntakeOutputRecord) -> Unit
) {
    val filtered = remember(records, range) { filterByRange(records, { it.time }, range) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            // 图表单独使用按时间范围查询的数据，避免受 500 条列表限制影响。
            IntakeOutputChart(records = chartRecords, days = period.days)
            Spacer(Modifier.height(12.dp))
        }
        intakeOutputList(filtered, onDelete)
        if (totalCount > HistoryViewModel.HISTORY_DISPLAY_LIMIT) {
            item { HistoryLimitHint() }
        }
    }
}

@Composable
private fun VitalsTab(
    records: List<VitalSignsRecord>,
    chartRecords: List<VitalSignsRecord>,
    range: DateRange,
    period: ChartPeriod,
    totalCount: Int
) {
    val filtered = remember(records, range) { filterByRange(records, { it.time }, range) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            // 图表单独使用按时间范围查询的数据，避免受 500 条列表限制影响。
            WeightChart(chartRecords, period.days); Spacer(Modifier.height(12.dp))
            BloodPressureChart(chartRecords, period.days); Spacer(Modifier.height(12.dp))
            HeartRateChart(chartRecords, period.days); Spacer(Modifier.height(12.dp))
            BloodSugarChart(chartRecords, period.days); Spacer(Modifier.height(12.dp))
        }
        vitalSignsList(filtered)
        if (totalCount > HistoryViewModel.HISTORY_DISPLAY_LIMIT) {
            item { HistoryLimitHint() }
        }
    }
}

@Composable
private fun MedicationTab(
    records: List<MedicationHistoryItem>,
    chartRecords: List<MedicationHistoryItem>,
    range: DateRange,
    period: ChartPeriod,
    totalCount: Int
) {
    val filtered = remember(records, range) { filterByRange(records, { it.scheduledTime }, range) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            // 图表单独使用按时间范围查询的数据，避免受 500 条列表限制影响。
            MedicationComplianceChart(chartRecords, period.days)
            Spacer(Modifier.height(12.dp))
        }
        medicationList(filtered)
        if (totalCount > HistoryViewModel.HISTORY_DISPLAY_LIMIT) {
            item { HistoryLimitHint() }
        }
    }
}

private fun <T> filterByRange(list: List<T>, timeOf: (T) -> Long, range: DateRange): List<T> {
    if (range == DateRange.ALL) return list
    val from = DateUtils.daysAgoStart(range.days - 1)
    return list.filter { timeOf(it) >= from }
}

@Composable
private fun HistoryLimitHint() {
    Text(
        text = "已显示最近500条记录，更多数据请使用导出功能查看",
        color = HintGray,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    )
}

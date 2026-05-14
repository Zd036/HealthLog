package com.rooftop.healthlog.ui.compliance

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rooftop.healthlog.ui.appViewModel
import com.rooftop.healthlog.ui.components.BigCard
import com.rooftop.healthlog.ui.components.LoadingIndicator
import com.rooftop.healthlog.ui.components.PrimaryBigButton
import com.rooftop.healthlog.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplianceReportScreen(onClose: () -> Unit) {
    val vm: ComplianceReportViewModel = appViewModel()
    val data by vm.data.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val exporting by vm.exporting.collectAsStateWithLifecycle()
    val exportResult by vm.exportResult.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    LaunchedEffect(exportResult) {
        exportResult?.let { r ->
            val msg = if (r.error == null) "报告已导出：${r.path}" else "导出失败：${r.error}"
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            vm.consumeExportResult()
        }
    }

    BackHandler { onClose() }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("依从性报告") },
                    navigationIcon = {
                        IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, "返回") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = PrimaryBlue, titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )

                AnimatedVisibility(visible = loading) { LoadingIndicator() }

                AnimatedVisibility(visible = !loading && data != null) {
                    data?.let { d ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SummaryCard(d)
                            PerMedCard("最近 7 天 · 各药品明细", d.sevenDay)
                            PerMedCard("最近 30 天 · 各药品明细", d.thirtyDay)

                            PrimaryBigButton(
                                text = if (exporting) "导出中 ..." else "导出报告",
                                onClick = { vm.exportCsv(ctx) },
                                enabled = !exporting
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(d: ComplianceReportData) {
    BigCard {
        Text("总体依从率", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RateBlock(label = "最近 7 天", summary = d.sevenDay)
            RateBlock(label = "最近 30 天", summary = d.thirtyDay)
        }
    }
}

@Composable
private fun RateBlock(label: String, summary: ComplianceSummary) {
    val rate = summary.overallRate
    val color = rateColor(rate, summary.totalExpected)
    val text = if (summary.totalExpected == 0) "--" else "${(rate * 100).toInt()}%"
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = HintGray)
        Spacer(Modifier.height(6.dp))
        Text(
            text = text,
            color = color,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${summary.totalActual} / ${summary.totalExpected}",
            style = MaterialTheme.typography.bodyMedium,
            color = HintGray
        )
    }
}

@Composable
private fun PerMedCard(title: String, summary: ComplianceSummary) {
    BigCard {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        if (summary.perMedication.isEmpty()) {
            Text("暂无数据", style = MaterialTheme.typography.bodyLarge, color = HintGray)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (m in summary.perMedication) {
                    MedRow(m)
                }
            }
        }
    }
}

@Composable
private fun MedRow(c: MedicationCompliance) {
    val color = rateColor(c.rate, c.expected)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                c.medicationName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            val pct = if (c.expected == 0) "--" else "${(c.rate * 100).toInt()}%"
            Text(
                pct,
                style = MaterialTheme.typography.titleMedium.copy(color = color, fontWeight = FontWeight.Bold)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "已服 ${c.actual} / 应服 ${c.expected}",
            style = MaterialTheme.typography.bodyMedium,
            color = HintGray
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { if (c.expected == 0) 0f else c.rate.coerceIn(0f, 1f) },
            color = SuccessGreen,
            trackColor = BorderGray,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}

private fun rateColor(rate: Float, expected: Int): Color {
    if (expected == 0) return HintGray
    val pct = rate * 100f
    return when {
        pct >= 90f -> SuccessGreen
        pct >= 70f -> WarningYellow
        else -> DangerRed
    }
}

package com.rooftop.healthlog.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rooftop.healthlog.ui.appViewModel
import com.rooftop.healthlog.ui.components.BigCard
import com.rooftop.healthlog.ui.components.PrimaryBigButton
import com.rooftop.healthlog.ui.components.UiFeedbackBus
import com.rooftop.healthlog.ui.theme.*

@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = appViewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val exporting by vm.exporting.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val exportResult by vm.exportResult.collectAsStateWithLifecycle()
    val importResult by vm.importResult.collectAsStateWithLifecycle()

    var showExportConfirm by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val ctx = LocalContext.current

    // SAF：仅选 CSV 文件
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingImportUri = uri
    }

    LaunchedEffect(exportResult) {
        exportResult?.let { r ->
            if (r.error == null) {
                UiFeedbackBus.show("导出成功：${r.displayPath}", long = true)
            } else {
                UiFeedbackBus.show("导出失败，请检查存储权限", long = true)
                Toast.makeText(ctx, "导出失败：${r.error}", Toast.LENGTH_LONG).show()
            }
            vm.consumeExportResult()
        }
    }
    LaunchedEffect(importResult) {
        importResult?.let { r ->
            val msg = when {
                r.error != null -> "导入失败：${r.error}"
                r.isEmpty && r.skippedDuplicates == 0 && r.skippedInvalid == 0 ->
                    "未导入任何数据"
                else -> buildString {
                    append("成功导入 ")
                    append("${r.intakeAdded} 条出入量、")
                    append("${r.vitalsAdded} 条体征、")
                    append("${r.medRecordsAdded} 条服药记录、")
                    append("${r.medSettingsAdded} 条用药设置")
                    if (r.skippedDuplicates > 0) append("\n跳过 ${r.skippedDuplicates} 条重复记录")
                    if (r.skippedInvalid > 0) append("\n${r.skippedInvalid} 条记录格式错误已跳过")
                }
            }
            UiFeedbackBus.show(msg, long = true)
            vm.consumeImportResult()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)

        FontSizeSection(
            isLarge = settings.fontSize == "large",
            onChange = { vm.setFontSize(it) }
        )

        IntakeOutputToggleSection(
            enabled = settings.enableIntakeOutput,
            onToggle = { vm.setEnableIntakeOutput(it) }
        )

        DataIoSection(
            exporting = exporting,
            importing = importing,
            onExport = { showExportConfirm = true },
            onImport = {
                // 只显示 CSV
                importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "*/*"))
            }
        )

        Spacer(Modifier.height(80.dp))
    }

    if (showExportConfirm) {
        AlertDialog(
            onDismissRequest = { showExportConfirm = false },
            title = { Text("导出数据", style = MaterialTheme.typography.headlineMedium) },
            text = {
                Text(
                    "将导出所有历史记录到 Download/healthlog/ 目录，是否继续？",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExportConfirm = false
                    vm.exportAll()
                }) { Text("确认导出", style = MaterialTheme.typography.titleLarge) }
            },
            dismissButton = {
                TextButton(onClick = { showExportConfirm = false }) {
                    Text("取消", style = MaterialTheme.typography.titleLarge)
                }
            }
        )
    }

    pendingImportUri?.let { uri ->
        val displayName = uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("即将导入数据", style = MaterialTheme.typography.headlineMedium) },
            text = {
                Column {
                    Text("文件：$displayName", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("导入将合并到现有数据中，是否继续？",
                        style = MaterialTheme.typography.bodyLarge)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val u = pendingImportUri
                    pendingImportUri = null
                    if (u != null) vm.importFrom(u)
                }) { Text("确认导入", style = MaterialTheme.typography.titleLarge) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) {
                    Text("取消", style = MaterialTheme.typography.titleLarge)
                }
            }
        )
    }

    if (exporting) ProgressDialog("正在导出 ...")
    if (importing) ProgressDialog("正在导入 ...")
}

@Composable
private fun ProgressDialog(text: String) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text(text, style = MaterialTheme.typography.titleLarge) },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(color = PrimaryBlue)
                Text("请稍候", style = MaterialTheme.typography.bodyLarge)
            }
        }
    )
}

@Composable
private fun FontSizeSection(isLarge: Boolean, onChange: (Boolean) -> Unit) {
    BigCard {
        Text("字体大小", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "当前：${if (isLarge) "超大字体" else "标准字体"}",
            style = MaterialTheme.typography.bodyLarge, color = HintGray
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FontOptionRow("标准字体", !isLarge) { onChange(false) }
            FontOptionRow("超大字体（推荐）", isLarge) { onChange(true) }
        }
    }
}

@Composable
private fun FontOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) PrimaryBlue.copy(alpha = 0.08f) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, if (selected) PrimaryBlue else BorderGray
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun IntakeOutputToggleSection(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    BigCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("开启出入量记录", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "关闭后，首页和历史将隐藏出入量相关内容，已有数据不会被删除。",
                    style = MaterialTheme.typography.bodyLarge, color = HintGray
                )
            }
            Switch(
                checked = enabled, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BgWhite, checkedTrackColor = PrimaryBlue,
                    uncheckedThumbColor = BgWhite, uncheckedTrackColor = HintGray
                )
            )
        }
    }
}

/** 修改点1：数据导入 / 导出按钮 */
@Composable
private fun DataIoSection(
    exporting: Boolean,
    importing: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    BigCard {
        Text("数据导入 / 导出", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "导出 CSV 到 /Download/healthlog/，支持从 CSV 文件导入合并历史数据。",
            style = MaterialTheme.typography.bodyLarge, color = HintGray
        )
        Spacer(Modifier.height(12.dp))
        // 导出按钮：蓝色
        PrimaryBigButton(
            text = if (exporting) "导出中 ..." else "导出数据",
            onClick = onExport,
            enabled = !exporting,
            color = PrimaryBlue
        )
        Spacer(Modifier.height(8.dp))
        // 导入按钮：绿色
        PrimaryBigButton(
            text = if (importing) "导入中 ..." else "导入数据",
            onClick = onImport,
            enabled = !importing,
            color = SuccessGreen
        )
    }
}

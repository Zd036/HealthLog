package com.example.heartcare.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
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
import com.example.heartcare.ui.appViewModel
import com.example.heartcare.ui.components.BigCard
import com.example.heartcare.ui.components.PrimaryBigButton
import com.example.heartcare.ui.components.SecondaryBigButton
import com.example.heartcare.ui.components.UiFeedbackBus
import com.example.heartcare.ui.theme.*

@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = appViewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val exporting by vm.exporting.collectAsStateWithLifecycle()
    val backingUp by vm.backingUp.collectAsStateWithLifecycle()
    val exportResult by vm.exportResult.collectAsStateWithLifecycle()
    val backupResult by vm.backupResult.collectAsStateWithLifecycle()
    val backups by vm.backups.collectAsStateWithLifecycle()

    var showExportConfirm by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    LaunchedEffect(exportResult) {
        exportResult?.let { r ->
            if (r.error == null) {
                UiFeedbackBus.show("导出完成：${r.fileCount} 个文件\n位置：${r.dirPath}", long = true)
            } else {
                UiFeedbackBus.show("导出失败：${r.error}", long = true)
                Toast.makeText(ctx, "导出失败：${r.error}", Toast.LENGTH_LONG).show()
            }
            vm.consumeExportResult()
        }
    }
    LaunchedEffect(backupResult) {
        backupResult?.let { r ->
            if (r.success) UiFeedbackBus.show("备份完成：${r.filePath}", long = true)
            else UiFeedbackBus.show("备份失败：${r.error}", long = true)
            vm.consumeBackupResult()
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

        DataExportSection(
            exporting = exporting,
            onExport = { showExportConfirm = true }
        )

        BackupSection(
            backingUp = backingUp,
            backups = backups,
            onManualBackup = { vm.backupNow() }
        )

        Spacer(Modifier.height(80.dp))
    }

    if (showExportConfirm) {
        AlertDialog(
            onDismissRequest = { showExportConfirm = false },
            title = { Text("导出数据", style = MaterialTheme.typography.headlineMedium) },
            text = {
                Text(
                    "将把所有记录（出入量 / 体征 / 服药）导出为 CSV 文件，保存到应用私有目录。是否继续？",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExportConfirm = false
                    vm.exportAll()
                }) { Text("导出", style = MaterialTheme.typography.titleLarge) }
            },
            dismissButton = {
                TextButton(onClick = { showExportConfirm = false }) {
                    Text("取消", style = MaterialTheme.typography.titleLarge)
                }
            }
        )
    }

    if (exporting) ProgressDialog("正在导出 ...")
    if (backingUp) ProgressDialog("正在备份 ...")
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
            FontOptionRow(
                label = "标准字体",
                selected = !isLarge,
                onClick = { onChange(false) }
            )
            FontOptionRow(
                label = "超大字体（推荐）",
                selected = isLarge,
                onClick = { onChange(true) }
            )
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
            1.dp,
            if (selected) PrimaryBlue else BorderGray
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
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("开启出入量记录", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "关闭后，首页和历史将隐藏出入量相关内容，已有数据不会被删除。",
                    style = MaterialTheme.typography.bodyLarge, color = HintGray
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BgWhite,
                    checkedTrackColor = PrimaryBlue,
                    uncheckedThumbColor = BgWhite,
                    uncheckedTrackColor = HintGray
                )
            )
        }
    }
}

@Composable
private fun DataExportSection(exporting: Boolean, onExport: () -> Unit) {
    BigCard {
        Text("数据导出", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "将全部记录导出为 CSV 文件，方便备份或交给医生查看。",
            style = MaterialTheme.typography.bodyLarge, color = HintGray
        )
        Spacer(Modifier.height(12.dp))
        PrimaryBigButton(
            text = if (exporting) "导出中 ..." else "导出数据",
            onClick = onExport,
            enabled = !exporting
        )
    }
}

@Composable
private fun BackupSection(
    backingUp: Boolean,
    backups: List<BackupFileInfo>,
    onManualBackup: () -> Unit
) {
    BigCard {
        Text("数据备份", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "每天凌晨 2:00 会自动备份数据库，最多保留最近 7 个。",
            style = MaterialTheme.typography.bodyLarge, color = HintGray
        )
        Spacer(Modifier.height(12.dp))
        SecondaryBigButton(
            text = if (backingUp) "备份中 ..." else "立即备份",
            onClick = onManualBackup
        )
        if (backups.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("备份历史（${backups.size}）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                backups.forEach { BackupRow(it) }
            }
        }
    }
}

@Composable
private fun BackupRow(info: BackupFileInfo) {
    val sizeText = formatSize(info.sizeBytes)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = PrimaryBlue.copy(alpha = 0.06f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                info.dayLabel,
                style = MaterialTheme.typography.titleMedium.copy(color = PrimaryBlue)
            )
            Spacer(Modifier.weight(1f))
            Text(sizeText, style = MaterialTheme.typography.bodyMedium, color = HintGray)
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    return "%.1f MB".format(mb)
}

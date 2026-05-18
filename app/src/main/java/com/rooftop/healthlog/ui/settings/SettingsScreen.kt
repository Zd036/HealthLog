package com.rooftop.healthlog.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rooftop.healthlog.data.local.entity.CustomCategory
import com.rooftop.healthlog.ui.appViewModel
import com.rooftop.healthlog.ui.components.BigCard
import com.rooftop.healthlog.ui.components.PrimaryBigButton
import com.rooftop.healthlog.ui.components.UiFeedbackBus
import com.rooftop.healthlog.ui.theme.*
import com.rooftop.healthlog.utils.ReminderPermissionHelper
import com.rooftop.healthlog.utils.ReminderPermissionStatus

@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = appViewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val customCategories by vm.customCategories.collectAsStateWithLifecycle()
    val exporting by vm.exporting.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val exportResult by vm.exportResult.collectAsStateWithLifecycle()
    val importResult by vm.importResult.collectAsStateWithLifecycle()
    val reminderPermissionStatus by vm.reminderPermissionStatus.collectAsStateWithLifecycle()

    var showExportConfirm by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        vm.refreshReminderPermissionStatus()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refreshReminderPermissionStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                    append("导入完成")
                    append("\n成功导入：${r.importedCount} 条")
                    append("\n跳过重复：${r.skippedDuplicates} 条")
                    append("\n格式错误：${r.skippedInvalid} 条")
                    append(
                        "\n明细：出入量 ${r.intakeAdded}，体征 ${r.vitalsAdded}，" +
                            "服药记录 ${r.medRecordsAdded}，用药设置 ${r.medSettingsAdded}，" +
                            "自定义项目 ${r.customCategoriesAdded}"
                    )
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

        CustomCategorySection(
            categories = customCategories,
            onAdd = { name, waterPercent, intake, onResult ->
                vm.addCustomCategory(name, waterPercent, intake, onResult)
            },
            onDelete = vm::deleteCustomCategory
        )

        ReminderPermissionSection(
            status = reminderPermissionStatus,
            onRefresh = { vm.refreshReminderPermissionStatus() },
            onOpenNotifications = {
                ctx.startActivity(ReminderPermissionHelper.notificationSettingsIntent(ctx).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            },
            onOpenExactAlarm = {
                ctx.startActivity(ReminderPermissionHelper.exactAlarmSettingsIntent(ctx).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
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
private fun ReminderPermissionSection(
    status: ReminderPermissionStatus,
    onRefresh: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenExactAlarm: () -> Unit,
) {
    BigCard {
        Text("后台提醒权限", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            if (status.allReady) {
                "后台提醒（服药提醒）所需权限已开启。"
            } else {
                "如果 APP 退到后台后不提醒，请检查以下系统权限。"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = if (status.allReady) SuccessGreen else HintGray
        )
        Spacer(Modifier.height(12.dp))
        ReminderStatusRow(
            label = "通知权限",
            ready = status.notificationsReady,
            buttonLabel = "去开启",
            onClick = onOpenNotifications
        )
        Spacer(Modifier.height(8.dp))
        ReminderStatusRow(
            label = "精确闹钟",
            ready = status.exactAlarmReady,
            buttonLabel = "去开启",
            onClick = onOpenExactAlarm
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("重新检查", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ReminderStatusRow(
    label: String,
    ready: Boolean,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                if (ready) "已开启" else "未开启",
                style = MaterialTheme.typography.bodyMedium,
                color = if (ready) SuccessGreen else DangerRed
            )
        }
        if (!ready) {
            TextButton(onClick = onClick) {
                Text(buttonLabel, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
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
            FontOptionRow("超大字体", isLarge) { onChange(true) }
        }
    }
}

@Composable
private fun FontOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) PrimaryBlue.copy(alpha = 0.08f) else Color.Transparent,
        border = BorderStroke(
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
private fun CustomCategorySection(
    categories: List<CustomCategory>,
    onAdd: (String, Float, Boolean, (String?) -> Unit) -> Unit,
    onDelete: (CustomCategory) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val intakeItems = remember(categories) { categories.filter { it.type == "intake" } }
    val outputItems = remember(categories) { categories.filter { it.type == "output" } }

    BigCard {
        Text("自定义出入量项目", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        PrimaryBigButton(
            text = "新增自定义项目",
            onClick = { showAddDialog = true },
            color = PrimaryBlue
        )
        Spacer(Modifier.height(12.dp))
        CategoryGroup(
            title = "摄入",
            categories = intakeItems,
            onDelete = onDelete
        )
        Spacer(Modifier.height(12.dp))
        CategoryGroup(
            title = "排出",
            categories = outputItems,
            onDelete = onDelete
        )
    }

    if (showAddDialog) {
        AddCustomCategoryDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, waterPercent, intake ->
                onAdd(name, waterPercent, intake) { error ->
                    if (error == null) {
                        showAddDialog = false
                        UiFeedbackBus.show("已新增自定义项目")
                    } else {
                        UiFeedbackBus.show(error)
                    }
                }
            }
        )
    }
}

@Composable
private fun CategoryGroup(
    title: String,
    categories: List<CustomCategory>,
    onDelete: (CustomCategory) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (categories.isEmpty()) {
            Text("暂无自定义项目", style = MaterialTheme.typography.bodyLarge, color = HintGray)
        } else {
            categories.forEach { item ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderGray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (item.type == "intake") "含水量 ${item.waterPercent.toInt()}%" else "排出项目",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HintGray
                            )
                        }
                        IconButton(onClick = { onDelete(item) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "删除${item.name}",
                                tint = DangerRed
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddCustomCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Float, Boolean) -> Unit,
) {
    var isIntake by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var waterPercent by remember { mutableStateOf("80") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增自定义项目", style = MaterialTheme.typography.headlineMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isIntake,
                        onClick = { isIntake = true },
                        label = { Text("摄入", style = MaterialTheme.typography.bodyLarge) }
                    )
                    FilterChip(
                        selected = !isIntake,
                        onClick = { isIntake = false },
                        label = { Text("排出", style = MaterialTheme.typography.bodyLarge) }
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (isIntake) {
                    OutlinedTextField(
                        value = waterPercent,
                        onValueChange = { waterPercent = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("含水量 % (0-100)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name, waterPercent.toFloatOrNull() ?: 80f, isIntake)
                }
            ) {
                Text("确定", style = MaterialTheme.typography.titleLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", style = MaterialTheme.typography.titleLarge)
            }
        }
    )
}

@Composable
private fun IntakeOutputToggleSection(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    BigCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("开启出入量记录", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "关闭后将隐藏出入量相关内容，不会删除已有数据。",
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

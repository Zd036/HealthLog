package com.rooftop.healthlog.ui.medication

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rooftop.healthlog.data.local.entity.Medication
import com.rooftop.healthlog.data.local.entity.MedicationSchedule
import com.rooftop.healthlog.ui.appViewModel
import com.rooftop.healthlog.ui.components.BigCard
import com.rooftop.healthlog.ui.components.PrimaryBigButton
import com.rooftop.healthlog.ui.components.SecondaryBigButton
import com.rooftop.healthlog.ui.theme.HintGray
import com.rooftop.healthlog.ui.theme.PrimaryBlue
import com.rooftop.healthlog.utils.medicationDoseLabel
import com.rooftop.healthlog.utils.medicationSpecificationLabel

/** 用药管理列表页 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationScreen() {
    val vm: MedicationViewModel = appViewModel()
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<MedicationSchedule?>(null) }
    var showCreateTime by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateTime = true },
                containerColor = PrimaryBlue
            ) { Icon(Icons.Filled.Add, "添加时间点") }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("用药管理", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            if (schedules.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "点击右下角 + 添加用药时间点",
                        style = MaterialTheme.typography.titleMedium, color = HintGray
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(schedules) { sm ->
                        ScheduleItem(
                            schedule = sm.schedule,
                            medications = sm.medications,
                            onToggle = { vm.toggleEnabled(sm.schedule, it) },
                            onEdit = { editing = sm.schedule }
                        )
                    }
                }
            }
        }
    }

    // 新建时间点选择器
    if (showCreateTime) {
        TimePickerDialogSimple(
            initialHour = 8, initialMinute = 0,
            onDismiss = { showCreateTime = false },
            onConfirm = { h, m ->
                showCreateTime = false
                val time = "%02d:%02d".format(h, m)
                vm.upsertSchedule(time, null) { newId ->
                    // 立即打开编辑页
                    editing = MedicationSchedule(id = newId, time = time)
                }
            }
        )
    }
    // 编辑时间点
    editing?.let { sch ->
        ScheduleEditScreen(
            schedule = sch,
            onClose = { editing = null }
        )
    }
}

/** 单个时间点卡片 */
@Composable
private fun ScheduleItem(
    schedule: MedicationSchedule,
    medications: List<Medication>,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    BigCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(schedule.time, style = MaterialTheme.typography.headlineMedium)
                Text(
                    timeLabel(schedule.time),
                    style = MaterialTheme.typography.bodyMedium, color = HintGray
                )
            }
            Switch(checked = schedule.enabled, onCheckedChange = onToggle)
        }
        Spacer(Modifier.height(8.dp))
        if (medications.isEmpty()) {
            Text("暂无药品", style = MaterialTheme.typography.bodyLarge, color = HintGray)
        } else {
            for (m in medications) {
                Text(formatMed(m), style = MaterialTheme.typography.bodyLarge)
                if (m.method.isNotBlank()) {
                    Text(
                        "   — ${m.method}",
                        style = MaterialTheme.typography.bodyMedium, color = HintGray
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        SecondaryBigButton(text = "编辑", onClick = onEdit)
    }
}

private fun formatMed(m: Medication): String {
    val spec = medicationSpecificationLabel(m.specification, m.unit)?.let { "（$it）" }.orEmpty()
    return "${m.name}：${medicationDoseLabel(m.dosage, m.unit)}$spec"
}

private fun timeLabel(time: String): String {
    val hour = time.substringBefore(':').toIntOrNull() ?: return ""
    return when (hour) {
        in 0..4 -> "凌晨"
        in 5..10 -> "早上"
        in 11..13 -> "中午"
        in 14..17 -> "下午"
        in 18..23 -> "晚上"
        else -> ""
    }
}

/** 简易时间选择器（小时 + 分钟） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialogSimple(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val tps = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择时间", style = MaterialTheme.typography.headlineMedium) },
        text = { TimePicker(state = tps) },
        confirmButton = {
            TextButton(onClick = { onConfirm(tps.hour, tps.minute) }) {
                Text("确定", style = MaterialTheme.typography.titleLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "取消",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    )
}

/** 编辑时间点 + 药品列表 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleEditScreen(schedule: MedicationSchedule, onClose: () -> Unit) {
    val vm: MedicationViewModel = appViewModel()
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    val currentSchedule = schedules.firstOrNull { it.schedule.id == schedule.id }
    val meds = currentSchedule?.medications.orEmpty()
    var editingMed by remember { mutableStateOf<Medication?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    var time by remember(schedule.id) { mutableStateOf(schedule.time) }

    LaunchedEffect(currentSchedule?.schedule?.time) {
        currentSchedule?.schedule?.time?.let { time = it }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("编辑用药时间点") },
                    navigationIcon = {
                        IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, "返回") }
                    },
                    actions = {
                        IconButton(onClick = {
                            vm.deleteSchedule(schedule); onClose()
                        }) { Icon(Icons.Filled.Delete, "删除") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = PrimaryBlue,
                        titleContentColor = androidx.compose.ui.graphics.Color.White,
                        navigationIconContentColor = androidx.compose.ui.graphics.Color.White,
                        actionIconContentColor = androidx.compose.ui.graphics.Color.White
                    )
                )
                Column(
                    Modifier
                        .padding(16.dp)
                        .weight(1f)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("时间", style = MaterialTheme.typography.titleMedium)
                                Text(time, style = MaterialTheme.typography.headlineMedium)
                            }
                            OutlinedButton(onClick = { showTimePicker = true }) {
                                Text("修改")
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "药品列表",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            // 修改点1：移除剩余药量与购药提醒字段后，新药品仅保留基础信息。
                            editingMed = Medication(
                                scheduleId = schedule.id,
                                name = "", dosage = 1f, unit = "片",
                                specification = 0f, method = ""
                            )
                        }) { Icon(Icons.Filled.Add, "添加药品") }
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(meds) { m ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(m.name, style = MaterialTheme.typography.titleLarge)
                                        Text(
                                            medicationDoseLabel(m.dosage, m.unit),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        if (m.method.isNotBlank()) Text(
                                            m.method,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = HintGray
                                        )
                                    }
                                    IconButton(onClick = { editingMed = m }) {
                                        Icon(Icons.Filled.Edit, "编辑")
                                    }
                                    IconButton(onClick = {
                                        vm.deleteMedication(m)
                                    }) { Icon(Icons.Filled.Delete, "删除") }
                                }
                            }
                        }
                    }
                }
                Surface(shadowElevation = 8.dp) {
                    Box(Modifier.padding(16.dp)) {
                        PrimaryBigButton("完成", onClick = onClose)
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        val h = time.substringBefore(':').toIntOrNull() ?: 8
        val m = time.substringAfter(':').toIntOrNull() ?: 0
        TimePickerDialogSimple(
            initialHour = h, initialMinute = m,
            onDismiss = { showTimePicker = false },
            onConfirm = { newH, newM ->
                showTimePicker = false
                time = "%02d:%02d".format(newH, newM)
                vm.upsertSchedule(time, schedule.id) {}
            }
        )
    }

    editingMed?.let { med ->
        MedicationEditDialog(
            medication = med,
            onDismiss = { editingMed = null },
            onSave = {
                vm.upsertMedication(it)
                editingMed = null
            }
        )
    }
}

/** 药品编辑对话框 */
@Composable
private fun MedicationEditDialog(
    medication: Medication,
    onDismiss: () -> Unit,
    onSave: (Medication) -> Unit
) {
    var name by remember { mutableStateOf(medication.name) }
    var dosage by remember {
        mutableStateOf(
            medication.dosage.toString().trimEnd('0').trimEnd('.')
        )
    }
    var unit by remember { mutableStateOf(medication.unit) }
    var spec by remember { mutableStateOf(if (medication.specification > 0) medication.specification.toString() else "") }
    var method by remember { mutableStateOf(medication.method) }
    val units = listOf("片", "粒", "mg", "g", "ml")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (medication.id == 0L) "添加药品" else "编辑药品",
                style = MaterialTheme.typography.headlineMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("药物名称") }, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dosage,
                        onValueChange = { dosage = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("单次剂量") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    UnitDropdown(unit, units) { unit = it }
                }
                OutlinedTextField(
                    value = spec,
                    onValueChange = { spec = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("规格（每片含药量 mg，可空）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = method, onValueChange = { method = it },
                    label = { Text("服用方式（如饭后服用）") }, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    onSave(
                        medication.copy(
                            name = name.trim(),
                            dosage = dosage.toFloatOrNull() ?: 1f,
                            unit = unit,
                            specification = spec.toFloatOrNull() ?: 0f,
                            method = method
                        )
                    )
                }
            }) { Text("保存", style = MaterialTheme.typography.titleLarge) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "取消",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDropdown(value: String, options: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded, onExpandedChange = { expanded = it },
        modifier = Modifier.width(120.dp)
    ) {
        OutlinedTextField(
            value = value, onValueChange = onChange, label = { Text("单位") },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (o in options) {
                DropdownMenuItem(text = { Text(o) },
                    onClick = { onChange(o); expanded = false })
            }
        }
    }
}

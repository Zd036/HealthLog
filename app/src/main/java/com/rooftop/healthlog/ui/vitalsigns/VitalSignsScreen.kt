package com.rooftop.healthlog.ui.vitalsigns

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rooftop.healthlog.ui.appViewModel
import com.rooftop.healthlog.ui.components.PrimaryBigButton
import com.rooftop.healthlog.ui.components.UiFeedbackBus
import com.rooftop.healthlog.ui.theme.DangerRed
import com.rooftop.healthlog.ui.theme.PrimaryBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VitalSignsScreen(onClose: () -> Unit) {
    val vm: VitalSignsViewModel = appViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var validationMessage by remember { mutableStateOf<String?>(null) }
    var abnormalAlertMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        vm.clearInputFields()
    }

    BackHandler { onClose() }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("体征记录") },
                    navigationIcon = {
                        IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, "返回") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = PrimaryBlue, titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BigField("体重 ⚖\uFE0F (斤)", state.weight, vm::setWeight, "请输入体重", KeyboardType.Decimal)
                    BigField("高压 \uD83E\uDE78 (mmHg)", state.systolic, vm::setSys, "请输入高压", KeyboardType.Number)
                    BigField("低压 \uD83E\uDE78 (mmHg)", state.diastolic, vm::setDia, "请输入低压", KeyboardType.Number)
                    BigField("脉率 \uD83E\uDE7A (次/分)", state.heartRate, vm::setHr, "请输入脉率", KeyboardType.Number)
                    BigField("血糖 \uD83D\uDC89 (mmol/L)", state.bloodSugar, vm::setSugar, "请输入血糖", KeyboardType.Decimal)
                    OutlinedTextField(
                        value = state.note,
                        onValueChange = vm::setNote,
                        label = { Text("备注（可选）") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge
                    )
                }
                Surface(shadowElevation = 8.dp) {
                    Box(Modifier.padding(16.dp)) {
                        PrimaryBigButton("保存", onClick = {
                            if (vm.isAllEmpty()) {
                                validationMessage = "请至少填写一项体征数据。"
                            } else if (vm.bloodPressurePairMessage() != null) {
                                validationMessage = vm.bloodPressurePairMessage()
                            } else {
                                saveAndHandle(vm) { alerts ->
                                    if (alerts.isNotEmpty()) abnormalAlertMessage = alerts.joinToString("\n")
                                    else { UiFeedbackBus.show("已保存体征记录"); onClose() }
                                }
                            }
                        })
                    }
                }
            }
        }
    }

    validationMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { validationMessage = null },
            title = {
                Text(
                    "提示",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    validationMessage = null
                }) {
                    Text("知道了", style = MaterialTheme.typography.titleLarge)
                }
            }
        )
    }

    abnormalAlertMessage?.let { message ->
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    "⚠️ 体征异常提醒",
                    style = MaterialTheme.typography.titleLarge.copy(color = DangerRed)
                )
            },
            text = {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    abnormalAlertMessage = null
                    UiFeedbackBus.show("已保存体征记录")
                    onClose()
                }) {
                    Text("知道了", style = MaterialTheme.typography.titleLarge)
                }
            }
        )
    }
}

/** 触发 VM 保存并把结果回传 */
private fun saveAndHandle(vm: VitalSignsViewModel, onDone: (List<String>) -> Unit) {
    vm.save { alerts -> onDone(alerts) }
}

@Composable
private fun BigField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    keyboard: KeyboardType
) {
    Column {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyLarge) },
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            textStyle = MaterialTheme.typography.titleLarge
        )
    }
}

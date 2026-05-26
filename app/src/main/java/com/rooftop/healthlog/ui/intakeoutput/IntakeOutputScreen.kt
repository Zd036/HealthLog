package com.rooftop.healthlog.ui.intakeoutput

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rooftop.healthlog.ui.appViewModel
import com.rooftop.healthlog.ui.components.PrimaryBigButton
import com.rooftop.healthlog.ui.components.UiFeedbackBus
import com.rooftop.healthlog.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/** 出入量记录页面（全屏） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntakeOutputScreen(startWithIntake: Boolean, onClose: () -> Unit) {
    val vm: IntakeOutputViewModel = appViewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    // 修改点2：每次进入页面时重置输入状态，确保时间戳不被缓存
    LaunchedEffect(startWithIntake) {
        vm.resetInputs(startWithIntake)
    }
    BackHandler { onClose() }

    Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false, dismissOnBackPress = true
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("出入量记录") },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.ArrowBack, "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = PrimaryBlue,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
                // 顶部 Tab 切换
                TabRow(selectedTabIndex = if (state.isIntake) 0 else 1) {
                    Tab(
                        selected = state.isIntake,
                        onClick = { vm.setIntake(true) },
                        text = { Text("摄入", style = MaterialTheme.typography.titleLarge) }
                    )
                    Tab(
                        selected = !state.isIntake,
                        onClick = { vm.setIntake(false) },
                        text = { Text("排出", style = MaterialTheme.typography.titleLarge) }
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    Text("选择类型", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    val categories = vm.categoryList(state.isIntake)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 280.dp)
                    ) {
                        items(categories) { c ->
                            val selected = state.category == c
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .background(
                                        if (selected) PrimaryBlue else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        if (selected) 0.dp else 1.dp,
                                        if (selected) PrimaryBlue else BorderGray,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { vm.setCategory(c) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    c,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = if (selected) Color.White else TextDark
                                    )
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    InputArea(vm, state)
                }
                Surface(shadowElevation = 8.dp) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        PrimaryBigButton("保存", onClick = {
                            vm.save {
                                UiFeedbackBus.show(
                                    if (state.isIntake) "已保存摄入记录" else "已保存排出记录"
                                )
                                onClose()
                            }
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun InputArea(vm: IntakeOutputViewModel, state: IOInputState) {
    val isStool = vm.isStool(state.category)
    val isLiquid = vm.isLiquid(state.category)
    val isSolid = vm.isSolid(state.category)
    val isCustomIntake = state.isIntake && vm.isCustom(state.category, intake = true)

    val unitText = when {
        isStool -> "次"
        isLiquid -> "毫升 (ml)"
        isSolid || isCustomIntake -> "克 (g)"
        else -> "毫升 (ml)"
    }
    val tip = when {
        isStool -> "请输入次数（整数）"
        isSolid || isCustomIntake -> "输入克数，将按含水量自动换算"
        else -> "请输入毫升数"
    }
    Text(tip, style = MaterialTheme.typography.bodyMedium, color = HintGray)
    Spacer(Modifier.height(4.dp))
    OutlinedTextField(
        value = state.amountText,
        onValueChange = vm::setAmount,
        label = { Text(unitText) },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isStool) KeyboardType.Number else KeyboardType.Decimal
        ),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        textStyle = MaterialTheme.typography.titleLarge
    )
    if (isSolid || isCustomIntake) {
        val pct = vm.waterPercent(state.category)
        if (pct != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                "含水量 ${pct.toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = HintGray
            )
        }
        val computed = vm.computeAmount()
        if (computed != null) {
            Text(
                "≈ ${computed.toInt()} ml",
                style = MaterialTheme.typography.titleMedium.copy(color = PrimaryBlue)
            )
        }
    }

    if (state.isIntake && state.category == "水/牛奶") {
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickPickButton("吃药 20ml") {
                vm.setAmount("20")
                vm.setNote("吃药")
            }
            QuickPickButton("蛋白粉 100ml") {
                vm.setAmount("100")
                vm.setNote("蛋白粉")
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    val timeStr = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(state.time))
    Text("记录时间：$timeStr", style = MaterialTheme.typography.bodyLarge)
    OutlinedTextField(
        value = state.note,
        onValueChange = vm::setNote,
        label = { Text("备注（可选）") },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        textStyle = MaterialTheme.typography.bodyLarge
    )
}

@Composable
private fun QuickPickButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.heightIn(min = 48.dp)
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

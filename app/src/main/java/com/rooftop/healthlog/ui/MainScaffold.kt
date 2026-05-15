package com.rooftop.healthlog.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rooftop.healthlog.ui.theme.PrimaryBlue
import com.rooftop.healthlog.ui.theme.HintGray
import com.rooftop.healthlog.ui.home.HomeScreen
import com.rooftop.healthlog.ui.history.HistoryScreen
import com.rooftop.healthlog.ui.medication.MedicationScreen
import com.rooftop.healthlog.ui.settings.SettingsScreen
import com.rooftop.healthlog.ui.settings.SettingsViewModel
import com.rooftop.healthlog.ui.intakeoutput.IntakeOutputScreen
import com.rooftop.healthlog.ui.vitalsigns.VitalSignsScreen
import com.rooftop.healthlog.ui.components.UiFeedbackBus

/** 主脚手架：4 个 Tab 页 + 中间大加号 + 顶层全屏子页面 */
@Composable
fun MainScaffold(modifier: Modifier = Modifier) {
    val pages = listOf(MainTab.Home, MainTab.History, MainTab.Medication, MainTab.Settings)
    var currentPageIndex by rememberSaveable { mutableIntStateOf(0) }

    val settingsVm: SettingsViewModel = appViewModel()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val ioEnabled = settings.enableIntakeOutput

    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var sub by remember { mutableStateOf<SubScreen>(SubScreen.None) }

    // 应用级 SnackbarHost
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        UiFeedbackBus.messages.collect { msg ->
            val result = snackbarHostState.showSnackbar(
                message = msg.text,
                actionLabel = msg.actionLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) msg.onAction?.invoke()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            // 修改点4：修正 Snackbar 配置，避免黑色方块
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .padding(bottom = 80.dp)  // 避开底部导航栏（72dp + 8dp 余量）
                    .padding(horizontal = 16.dp),
                snackbar = { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        actionColor = MaterialTheme.colorScheme.inversePrimary,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            )
        },
        bottomBar = {
            HealthLogBottomBar(
                currentPageIndex = currentPageIndex,
                onTabClick = { idx -> currentPageIndex = idx },
                onPlusClick = { showAddSheet = true }
            )
        }
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            AnimatedContent(
                targetState = pages[currentPageIndex],
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "tab"
            ) { tab ->
                when (tab) {
                    MainTab.Home -> HomeScreen(
                        onRecordIntakeOutput = { intake -> sub = SubScreen.IntakeOutput(intake) },
                        onRecordVitalSigns = { sub = SubScreen.VitalSigns }
                    )
                    MainTab.History -> HistoryScreen()
                    MainTab.Medication -> MedicationScreen()
                    MainTab.Settings -> SettingsScreen()
                }
            }
        }
    }

    if (showAddSheet) {
        AddBottomSheet(
            showIntakeOutput = ioEnabled,
            onDismiss = { showAddSheet = false },
            onIntakeOutput = {
                showAddSheet = false
                sub = SubScreen.IntakeOutput(intake = true)
            },
            onVitalSigns = {
                showAddSheet = false
                sub = SubScreen.VitalSigns
            }
        )
    }

    // 全屏子页面 —— 300ms 从右滑入，从右滑出
    AnimatedContent(
        targetState = sub,
        transitionSpec = {
            if (targetState is SubScreen.None) {
                fadeIn(tween(200)) togetherWith
                    (slideOutHorizontally(tween(300)) { it } + fadeOut(tween(200)))
            } else {
                (slideInHorizontally(tween(300)) { it } + fadeIn(tween(200))) togetherWith
                    fadeOut(tween(200))
            }
        },
        label = "sub"
    ) { s ->
        when (s) {
            is SubScreen.IntakeOutput -> IntakeOutputScreen(
                startWithIntake = s.intake,
                onClose = { sub = SubScreen.None }
            )
            SubScreen.VitalSigns -> VitalSignsScreen(onClose = { sub = SubScreen.None })
            SubScreen.None -> {}
        }
    }
}

/** 4 个真实 Tab */
enum class MainTab(val title: String, val icon: ImageVector) {
    Home("首页", Icons.Filled.Home),
    History("历史记录", Icons.Filled.BarChart),
    Medication("用药管理", Icons.Filled.Medication),
    Settings("设置", Icons.Filled.Settings)
}

/** 顶层子页面 */
sealed interface SubScreen {
    data object None : SubScreen
    data class IntakeOutput(val intake: Boolean) : SubScreen
    data object VitalSigns : SubScreen
}

/** 5 段底部导航：首页 / 历史 / [+大加号] / 用药 / 设置 */
@Composable
private fun HealthLogBottomBar(
    currentPageIndex: Int,
    onTabClick: (Int) -> Unit,
    onPlusClick: () -> Unit
) {
    Surface(tonalElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomTabItem("首页", Icons.Filled.Home, currentPageIndex == 0, Modifier.weight(1f)) {
                onTabClick(0)
            }
            BottomTabItem("历史记录", Icons.Filled.BarChart, currentPageIndex == 1, Modifier.weight(1f)) {
                onTabClick(1)
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(PrimaryBlue, CircleShape)
                        .clickable { onPlusClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "新增",
                        tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }
            BottomTabItem("用药管理", Icons.Filled.Medication, currentPageIndex == 2, Modifier.weight(1f)) {
                onTabClick(2)
            }
            BottomTabItem("设置", Icons.Filled.Settings, currentPageIndex == 3, Modifier.weight(1f)) {
                onTabClick(3)
            }
        }
    }
}

@Composable
private fun BottomTabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val color = if (selected) PrimaryBlue else HintGray
    Column(
        modifier = modifier.heightIn(min = 56.dp).clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 14.sp, color = color, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBottomSheet(
    showIntakeOutput: Boolean,
    onDismiss: () -> Unit,
    onIntakeOutput: () -> Unit,
    onVitalSigns: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("快速记录", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            if (showIntakeOutput) {
                ListItem(
                    headlineContent = { Text("记录出入量", style = MaterialTheme.typography.titleLarge) },
                    supportingContent = { Text("摄入 / 排出", style = MaterialTheme.typography.bodyLarge) },
                    leadingContent = { Icon(Icons.Filled.WaterDrop, null, tint = PrimaryBlue) },
                    modifier = Modifier.heightIn(min = 64.dp).clickable { onIntakeOutput() }
                )
                HorizontalDivider()
            }
            ListItem(
                headlineContent = { Text("记录体征", style = MaterialTheme.typography.titleLarge) },
                supportingContent = { Text("体重 / 血压 / 脉率 / 血糖", style = MaterialTheme.typography.bodyLarge) },
                leadingContent = { Icon(Icons.Filled.Favorite, null, tint = PrimaryBlue) },
                modifier = Modifier.heightIn(min = 64.dp).clickable { onVitalSigns() }
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

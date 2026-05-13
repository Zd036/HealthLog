package com.example.heartcare.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable
import com.example.heartcare.HeartCareApp
import com.example.heartcare.ui.home.HomeViewModel
import com.example.heartcare.ui.intakeoutput.IntakeOutputViewModel
import com.example.heartcare.ui.medication.MedicationViewModel
import com.example.heartcare.ui.vitalsigns.VitalSignsViewModel
import com.example.heartcare.ui.history.HistoryViewModel
import com.example.heartcare.ui.settings.SettingsViewModel
import com.example.heartcare.ui.compliance.ComplianceReportViewModel

/** 简单的 VM Factory，所有 VM 都需要 Application */
class AppVMFactory(private val app: HeartCareApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(app) as T
            modelClass.isAssignableFrom(IntakeOutputViewModel::class.java) -> IntakeOutputViewModel(app) as T
            modelClass.isAssignableFrom(VitalSignsViewModel::class.java) -> VitalSignsViewModel(app) as T
            modelClass.isAssignableFrom(MedicationViewModel::class.java) -> MedicationViewModel(app) as T
            modelClass.isAssignableFrom(HistoryViewModel::class.java) -> HistoryViewModel(app) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(app) as T
            modelClass.isAssignableFrom(ComplianceReportViewModel::class.java) -> ComplianceReportViewModel(app) as T
            else -> throw IllegalArgumentException("Unknown VM: ${modelClass.name}")
        }
    }
}

/** 在 Composable 内使用：val vm: HomeViewModel = appViewModel() */
@Composable
inline fun <reified VM : ViewModel> appViewModel(): VM =
    viewModel(factory = AppVMFactory(HeartCareApp.instance))

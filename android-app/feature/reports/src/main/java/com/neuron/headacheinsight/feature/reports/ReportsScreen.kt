package com.neuron.headacheinsight.feature.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightSectionCard
import com.neuron.headacheinsight.core.model.ReportBundle
import com.neuron.headacheinsight.domain.BuildReportsUseCase
import com.neuron.headacheinsight.domain.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val buildReportsUseCase: BuildReportsUseCase,
    private val reportRepository: ReportRepository,
) : androidx.lifecycle.ViewModel() {
    private val _state = MutableStateFlow<ReportBundle?>(null)
    val state: StateFlow<ReportBundle?> = _state

    fun load() {
        viewModelScope.launch {
            _state.emit(buildReportsUseCase())
        }
    }

    fun export() {
        val bundle = state.value ?: return
        viewModelScope.launch {
            reportRepository.exportReports(bundle)
        }
    }
}

@Composable
fun ReportsRoute(
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.load() }
    ReportsScreen(bundle = state, onExport = viewModel::export)
}

@Composable
fun ReportsScreen(
    bundle: ReportBundle?,
    onExport: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeadacheInsightSectionCard(title = stringResource(R.string.reports_patient_title)) {
            Text(bundle?.patientText ?: stringResource(R.string.reports_loading))
        }
        HeadacheInsightSectionCard(title = stringResource(R.string.reports_clinician_title)) {
            Text(bundle?.clinicianText ?: stringResource(R.string.reports_loading))
        }
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.reports_export))
        }
    }
}

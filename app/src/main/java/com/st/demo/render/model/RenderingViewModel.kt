package com.st.demo.render.model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.st.blue_sdk.BlueManager
import com.st.blue_sdk.features.sensor_fusion.MemsSensorFusion
import com.st.blue_sdk.features.sensor_fusion.MemsSensorFusionCompat
import com.st.blue_sdk.features.sensor_fusion.MemsSensorFusionInfo
import com.st.demo.sensor_processing.model.PerformanceMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RenderingViewModel @Inject constructor(
    private val blueManager: BlueManager
) : ViewModel() {
    private var currentDeviceId: String? = null
    private var sensorJob: Job? = null
    private val _uiState = MutableStateFlow(PerformanceMetrics())

    val uiState: StateFlow<PerformanceMetrics> =
        _uiState.stateIn(viewModelScope, SharingStarted.Lazily, PerformanceMetrics())

    fun startListening(deviceId: String) {
        currentDeviceId = deviceId
        val node = blueManager.getNode(deviceId) ?: return

        val features = blueManager.nodeFeatures(deviceId).filter {
            it.name == MemsSensorFusionCompat.NAME
        }
        Log.d("TRACKERLOG", "Features enabled:")
        features.forEach {
            Log.d("TRACKERLOG", it.name)
        }

        sensorJob = viewModelScope.launch {
            blueManager.enableFeatures(deviceId, features)
            blueManager.getFeatureUpdates(deviceId, features, autoEnable = false)
                .collect { update ->
                    when (update.featureName) {
                        MemsSensorFusionCompat.NAME -> handleFusionData(update.data as MemsSensorFusionInfo)
                    }
                }
        }
    }

    fun stopListeningClicked(deviceId: String) {
        viewModelScope.launch {
            stopTracking(deviceId)
        }
    }

    private suspend fun CoroutineScope.stopTracking(deviceId: String) {
        sensorJob?.cancel()
        // Get required features
        val features = blueManager.nodeFeatures(deviceId).filter {
            it.name == MemsSensorFusion.NAME
        }

        currentDeviceId?.let { deviceId ->
            blueManager.disableFeatures(deviceId, features)
        }
        currentDeviceId = null
        sensorJob?.cancel()
    }

    private fun handleFusionData(data: MemsSensorFusionInfo) {
        data.quaternions.lastOrNull()?.value?.let { quaternion ->
            _uiState.update {
                it.copy(
                    rawQuaternion = quaternion,
                )
            }
        }
    }
}
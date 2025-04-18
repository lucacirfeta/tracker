package com.st.demo.render.model

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.st.blue_sdk.BlueManager
import com.st.blue_sdk.features.Feature
import com.st.blue_sdk.features.sensor_fusion.MemsSensorFusion
import com.st.blue_sdk.features.sensor_fusion.MemsSensorFusionCompat
import com.st.blue_sdk.features.sensor_fusion.MemsSensorFusionInfo
import com.st.demo.render.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val blueManager: BlueManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private var currentDeviceId: String? = null
    private var sensorJob: Job? = null
    private val _uiState = MutableStateFlow(QuaternionData())
    private var features: List<Feature<*>> = emptyList()

    init {
        loadSavedPosition()
    }

    val uiState: StateFlow<QuaternionData> =
        _uiState.stateIn(viewModelScope, SharingStarted.Lazily, QuaternionData())

    fun startListening(deviceId: String) {
        currentDeviceId = deviceId
        val node = blueManager.getNode(deviceId) ?: return

        features = blueManager.nodeFeatures(deviceId).filter {
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

    private fun handleFusionData(data: MemsSensorFusionInfo) {
        data.quaternions.lastOrNull()?.value?.let { quaternion ->
            _uiState.update {
                it.copy(
                    rawQuaternion = quaternion
                )
            }
        }
    }

    fun calibrate() {
        _uiState.update {
            it.copy(
                offsetQuaternion = it.rawQuaternion,
            )
        }
        savePosition()
    }


    fun savePosition() {
        val prefs = PreferencesManager.getInstance(context)
        _uiState.update { currentState ->
            val newOffset = currentState.rawQuaternion.copy(timeStamp = 0L)
            // Save to preferences
            prefs.saveResetPosition(
                newOffset.qi,
                newOffset.qj,
                newOffset.qk,
                newOffset.qs
            )
            currentState.copy(
                offsetQuaternion = newOffset
            )
        }
    }

    private fun loadSavedPosition() {
        val prefs = PreferencesManager.getInstance(context)
        prefs.loadResetPosition()?.let { savedQuaternion ->
            _uiState.update { currentState ->
                currentState.copy(
                    offsetQuaternion = savedQuaternion
                )
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
        currentDeviceId?.let { deviceId ->
            blueManager.disableFeatures(deviceId, features)
        }
        currentDeviceId = null
        sensorJob?.cancel()
    }
}
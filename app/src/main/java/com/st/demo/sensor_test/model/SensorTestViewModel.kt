package com.st.demo.sensor_test.model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.st.blue_sdk.BlueManager
import com.st.blue_sdk.features.Feature
import com.st.blue_sdk.features.acceleration.Acceleration
import com.st.blue_sdk.features.acceleration.AccelerationInfo
import com.st.blue_sdk.features.gyroscope.Gyroscope
import com.st.blue_sdk.features.gyroscope.GyroscopeInfo
import com.st.blue_sdk.features.magnetometer.Magnetometer
import com.st.blue_sdk.features.magnetometer.MagnetometerInfo
import com.st.blue_sdk.features.sensor_fusion.MemsSensorFusionCompat
import com.st.blue_sdk.features.sensor_fusion.MemsSensorFusionInfo
import com.st.demo.common.model.Vector3
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SensorTestViewModel @Inject constructor(
    private val blueManager: BlueManager
) : ViewModel() {
    private var deviceId: String? = null
    private var sensorJob: Job? = null
    private var features: List<Feature<*>> = emptyList()

    // UI State
    private val _uiState = MutableStateFlow(SensorTestState())
    val uiState: StateFlow<SensorTestState> = _uiState

    fun startTracking(deviceId: String) {
        this.deviceId = deviceId
        val node = blueManager.getNode(deviceId) ?: return

        features = blueManager.nodeFeatures(deviceId).filter {
            it.name == Acceleration.NAME ||
                    it.name == Gyroscope.NAME ||
                    it.name == Magnetometer.NAME ||
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
                        Acceleration.NAME -> handleAccel(update.data as AccelerationInfo)
                        Magnetometer.NAME -> handleMagn(update.data as MagnetometerInfo)
                        Gyroscope.NAME -> handleGyro(update.data as GyroscopeInfo)
                        MemsSensorFusionCompat.NAME -> handleFusionData(update.data as MemsSensorFusionInfo)
                    }
                }
        }
    }

    private fun CoroutineScope.handleFusionData(data: MemsSensorFusionInfo) {
        data.quaternions.lastOrNull()?.value?.let { quaternion ->
            Log.d("TRACKERLOG", "Quaternion: $quaternion")
        }
    }

    private fun CoroutineScope.handleAccel(data: AccelerationInfo) {
        val rawAcc = Vector3(
            x = data.x.value,
            y = data.y.value,
            z = data.z.value
        )

    }

    private fun CoroutineScope.handleMagn(data: MagnetometerInfo) {
        val rawMag = Vector3(
            x = data.x.value,
            y = data.y.value,
            z = data.z.value
        )
    }

    private fun CoroutineScope.handleGyro(data: GyroscopeInfo) {
        val rawGyro = Vector3(
            x = data.x.value,
            y = data.y.value,
            z = data.z.value
        )
    }

    fun stopTracking() {
        deviceId?.let { id ->
            viewModelScope.launch {
                blueManager.disableFeatures(id, features)
            }
        }
        sensorJob?.cancel()
    }
}

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
import com.st.demo.common.utils.QuaternionHelper
import com.st.demo.ml_prevision.ModelPrevision
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SensorTestViewModel @Inject constructor(
    private val blueManager: BlueManager,
    private val modelPrevision: ModelPrevision
) : ViewModel() {
    private var deviceId: String? = null
    private var sensorJob: Job? = null
    private var features: List<Feature<*>> = emptyList()

    // UI State
    private val _uiState = MutableStateFlow(SensorTestState())
    val uiState: StateFlow<SensorTestState> = _uiState

    private var lastUpdateTime = 0L

    fun startTracking(deviceId: String) {
        this.deviceId = deviceId
        val node = blueManager.getNode(deviceId) ?: return

        features = blueManager.nodeFeatures(deviceId).filter {
            it.name == Acceleration.NAME ||
                    it.name == Gyroscope.NAME ||
                    it.name == Magnetometer.NAME ||
                    it.name == MemsSensorFusionCompat.NAME
        }

        Log.d("RENDER_SENSOR_TEST", "Features enabled:")
        features.forEach {
            Log.d("RENDER_SENSOR_TEST", it.name)
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

    private fun handleSensorUpdate() {
        try {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdateTime > 50) { // Aggiorna ogni 50ms
                lastUpdateTime = currentTime

                _uiState.value = _uiState.value.copy(
                    prediction = modelPrevision.getPrediction()
                )
            }
        } catch (e: Exception) {
            Log.e("TRACKERLOG", "Error getPrediction", e)
            throw RuntimeException("getPrediction failed", e)
        }
    }

    private fun CoroutineScope.handleFusionData(data: MemsSensorFusionInfo) {
        data.quaternions.lastOrNull()?.value?.let { quaternion ->
            _uiState.value = _uiState.value.copy(
                quaternion = QuaternionHelper.toMathQuaternion(quaternion),
                lastUpdate = System.currentTimeMillis()
            )
        }
    }

    private fun CoroutineScope.handleAccel(data: AccelerationInfo) {
        val rawAccel = Vector3(
            x = data.x.value,
            y = data.y.value,
            z = data.z.value
        )
        _uiState.value =
            _uiState.value.copy(rawAccel = rawAccel, lastUpdate = System.currentTimeMillis())

        modelPrevision.addSensorData(
            acc = Triple(data.x.value, data.y.value, data.z.value),
            gyro = Triple(
                _uiState.value.rawGyro.x,
                _uiState.value.rawGyro.y,
                _uiState.value.rawGyro.z
            ),
            mag = Triple(
                _uiState.value.rawMag.x,
                _uiState.value.rawMag.y,
                _uiState.value.rawMag.z
            )
        )
        handleSensorUpdate()
    }

    private fun CoroutineScope.handleMagn(data: MagnetometerInfo) {
        val rawMag = Vector3(
            x = data.x.value,
            y = data.y.value,
            z = data.z.value
        )
        _uiState.value =
            _uiState.value.copy(rawMag = rawMag, lastUpdate = System.currentTimeMillis())

        modelPrevision.addSensorData(
            acc = Triple(
                _uiState.value.rawAccel.x,
                _uiState.value.rawAccel.y,
                _uiState.value.rawAccel.z
            ),
            gyro = Triple(
                _uiState.value.rawGyro.x,
                _uiState.value.rawGyro.y,
                _uiState.value.rawGyro.z
            ),
            mag = Triple(data.x.value, data.y.value, data.z.value)
        )
        handleSensorUpdate()
    }

    private fun CoroutineScope.handleGyro(data: GyroscopeInfo) {
        val rawGyro = Vector3(
            x = data.x.value,
            y = data.y.value,
            z = data.z.value
        )
        _uiState.value =
            _uiState.value.copy(rawGyro = rawGyro, lastUpdate = System.currentTimeMillis())

        modelPrevision.addSensorData(
            acc = Triple(
                _uiState.value.rawAccel.x,
                _uiState.value.rawAccel.y,
                _uiState.value.rawAccel.z
            ),
            gyro = Triple(data.x.value, data.y.value, data.z.value),
            mag = Triple(
                _uiState.value.rawMag.x,
                _uiState.value.rawMag.y,
                _uiState.value.rawMag.z
            )
        )
        handleSensorUpdate()
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

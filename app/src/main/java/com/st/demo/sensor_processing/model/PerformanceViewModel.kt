package com.st.demo.sensor_processing.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.st.blue_sdk.BlueManager
import com.st.blue_sdk.features.FeatureUpdate
import com.st.blue_sdk.features.acceleration.Acceleration
import com.st.blue_sdk.features.acceleration.AccelerationInfo
import com.st.blue_sdk.features.gyroscope.Gyroscope
import com.st.blue_sdk.features.gyroscope.GyroscopeInfo
import com.st.blue_sdk.features.humidity.Humidity
import com.st.blue_sdk.features.humidity.HumidityInfo
import com.st.blue_sdk.features.magnetometer.Magnetometer
import com.st.blue_sdk.features.pressure.Pressure
import com.st.blue_sdk.features.pressure.PressureInfo
import com.st.blue_sdk.features.sensor_fusion.MemsSensorFusion
import com.st.blue_sdk.features.sensor_fusion.MemsSensorFusionCompat
import com.st.blue_sdk.features.sensor_fusion.MemsSensorFusionInfo
import com.st.blue_sdk.features.sensor_fusion.Quaternion
import com.st.blue_sdk.features.temperature.Temperature
import com.st.blue_sdk.features.temperature.TemperatureInfo
import com.st.demo.sensor_processing.processor.PerformanceProcessor
import com.st.demo.tracker_sensor.utils.Vector3
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sin


@HiltViewModel
class PerformanceViewModel @Inject constructor(
    private val blueManager: BlueManager
) : ViewModel() {
    private val processor = PerformanceProcessor()
    private val _uiState = MutableStateFlow(PerformanceMetrics())
    private var sensorJob: Job? = null
    private var currentDeviceId: String? = null

    val uiState: StateFlow<PerformanceMetrics> = _uiState

    fun startTracking(deviceId: String) {
        currentDeviceId = deviceId
        sensorJob?.cancel()

        // Get required features
        val features = blueManager.nodeFeatures(deviceId).filter {
//            it.name == Acceleration.NAME ||
//                    it.name == Gyroscope.NAME ||
            it.name == MemsSensorFusionCompat.NAME ||
                    it.name == MemsSensorFusion.NAME
//                    it.name == Pressure.NAME ||
//                    it.name == Humidity.NAME ||
//                    it.name == Temperature.NAME
        }

        sensorJob = viewModelScope.launch {
            blueManager.enableFeatures(deviceId, features)
            blueManager.getFeatureUpdates(deviceId, features, autoEnable = false)
                .collect { update ->
                    processFeatureUpdate(update)
                    _uiState.value = processor.getSessionMetrics()
                }
        }
    }

    private fun processFeatureUpdate(update: FeatureUpdate<*>) {
        try {
            when (update.featureName) {
                Acceleration.NAME -> handleAccel(update.data as AccelerationInfo)
                Gyroscope.NAME -> handleGyro(update.data as GyroscopeInfo)
                Pressure.NAME -> handlePressure(update.data as PressureInfo)
                Humidity.NAME -> handleHumidityTemp(update.data as HumidityInfo)
                MemsSensorFusionCompat.NAME -> handleFusionData(update.data as MemsSensorFusionInfo)
                MemsSensorFusion.NAME -> handleFusionData(update.data as MemsSensorFusionInfo)
                Temperature.NAME -> handleTemperatureData(update.data as TemperatureInfo)
            }
        } catch (e: ClassCastException) {
            Timber.tag("SENSOR_ERROR")
                .e("Data type mismatch for ${update.featureName}: ${e.message}")
        }
    }

    private fun handleFusionData(data: MemsSensorFusionInfo) {
        data.quaternions.lastOrNull()?.value?.let { quaternion ->
            processor.processFusionData(quaternion)
            _uiState.update {
                it.copy(
                    rawQuaternion = quaternion,
                    smoothedQuaternion = processor.currentOrientation.toQuaternion()
                )
            }
            updateOrientationAndGravity()
        }
    }

    // Add extension function for conversion
    fun Vector3.toQuaternion(): Quaternion {
        // Convert Euler angles back to quaternion for visualization
        val cy = cos(Math.toRadians(z * 0.5).toDouble())
        val sy = sin(Math.toRadians(z * 0.5).toDouble())
        val cp = cos(Math.toRadians(y * 0.5).toDouble())
        val sp = sin(Math.toRadians(y * 0.5).toDouble())
        val cr = cos(Math.toRadians(x * 0.5).toDouble())
        val sr = sin(Math.toRadians(x * 0.5).toDouble())

        return Quaternion(
            timeStamp = System.currentTimeMillis(),
            qi = (sr * cp * cy - cr * sp * sy).toFloat(),
            qj = (cr * sp * cy + sr * cp * sy).toFloat(),
            qk = (cr * cp * sy - sr * sp * cy).toFloat(),
            qs = (cr * cp * cy + sr * sp * sy).toFloat()
        )
    }

    private fun handleAccel(data: AccelerationInfo) {
        val rawAccel = Vector3(data.x.value, data.y.value, data.z.value)
        val linearAccel = processor.calculateLinearAcceleration(rawAccel)

        _uiState.update {
            it.copy(
                rawAcceleration = rawAccel,
                linearAcceleration = linearAccel
            )
        }

        processor.processIMU(rawAccel, System.nanoTime())
    }

    private fun handleGyro(data: GyroscopeInfo) {
        val angularVelocity = Vector3(
            data.x.value.degToRad(),
            data.y.value.degToRad(),
            data.z.value.degToRad()
        )
        _uiState.update { it.copy(angularVelocity = angularVelocity) }
    }

    private fun handlePressure(data: PressureInfo) {
        _uiState.update {
            it.copy(
                environment = it.environment.copy(
                    pressure = data.pressure.value,
                    altitude = processor.calculateAltitude(data.pressure.value)
                )
            )
        }
        processor.processEnvironment(
            _uiState.value.environment.pressure,
            _uiState.value.environment.temperature,
            _uiState.value.environment.humidity
        )
    }

    private fun handleHumidityTemp(data: HumidityInfo) {
        _uiState.update {
            it.copy(
                environment = it.environment.copy(
                    humidity = data.humidity.value,
                )
            )
        }
        processor.processEnvironment(
            _uiState.value.environment.pressure,
            uiState.value.environment.temperature,
            data.humidity.value
        )
    }

    private fun handleTemperatureData(data: TemperatureInfo) {
        _uiState.update {
            it.copy(
                environment = it.environment.copy(
                    temperature = data.temperature.value,
                )
            )
        }
        processor.processEnvironment(
            _uiState.value.environment.pressure,
            data.temperature.value,
            uiState.value.environment.humidity
        )
    }

    private fun updateOrientationAndGravity() {
        _uiState.update {
            it.copy(
                orientation = processor.currentOrientation,
            )
        }
    }

    private fun Float.degToRad() = Math.toRadians(this.toDouble()).toFloat()

    fun processAudioInput(buffer: ShortArray) {
        processor.processAudio(buffer, 16000)?.let { impact ->
            _uiState.update { state ->
                state.copy(
                    lastImpact = impact,
                    shotDistribution = state.shotDistribution.toMutableMap().apply {
                        this[impact.type] = this[impact.type]?.plus(1) ?: 0
                    }
                )
            }
        }
    }

    fun resetSession() {
        processor.resetSession()
        _uiState.value = PerformanceMetrics()
    }


    fun stopTrackingClicked(deviceId: String) {
        viewModelScope.launch {
            stopTracking(deviceId)
        }
    }

    private suspend fun CoroutineScope.stopTracking(deviceId: String) {
        sensorJob?.cancel()
        // Get required features
        val features = blueManager.nodeFeatures(deviceId).filter {
            it.name == Acceleration.NAME ||
                    it.name == Gyroscope.NAME ||
                    it.name == Magnetometer.NAME ||
                    it.name == Pressure.NAME ||
                    it.name == Humidity.NAME ||
                    it.name == MemsSensorFusion.NAME
        }

        currentDeviceId?.let { deviceId ->
            blueManager.disableFeatures(deviceId, features)
        }
        currentDeviceId = null
    }
}
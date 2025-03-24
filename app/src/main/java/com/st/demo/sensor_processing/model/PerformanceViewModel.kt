package com.st.demo.sensor_processing.model

import android.util.Log
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
import com.st.blue_sdk.features.magnetometer.MagnetometerInfo
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    val uiState: StateFlow<PerformanceMetrics> =
        _uiState.stateIn(viewModelScope, SharingStarted.Lazily, PerformanceMetrics())

    fun startTracking(deviceId: String) {
        Log.d("TRACKERLOG", "Start tracking for device: $deviceId")

        currentDeviceId = deviceId
        sensorJob?.cancel()

        val features = blueManager.nodeFeatures(deviceId).filter {
            it.name == Acceleration.NAME ||
                    it.name == Magnetometer.NAME ||
                    it.name == Gyroscope.NAME
//                    it.name == MemsSensorFusionCompat.NAME ||
//                    it.name == Pressure.NAME ||
//                    it.name == Humidity.NAME ||
//                    it.name == Temperature.NAME
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
                        Acceleration.NAME -> handleAccel(update)
                        Magnetometer.NAME -> handleMagn(update)
                        Gyroscope.NAME -> handleGyro(update)
                        Pressure.NAME -> handlePressure(update.data as PressureInfo)
                        Humidity.NAME -> handleHumidityTemp(update.data as HumidityInfo)
                        Temperature.NAME -> handleTemperatureData(update.data as TemperatureInfo)
                    }
                    _uiState.update { processor.getSessionMetrics() }
                }
        }
    }

    private fun handleMagn(update: FeatureUpdate<*>) {
        val data = update.data as MagnetometerInfo
        val magn = Vector3(
            x = data.x.value,
            y = data.y.value,
            z = data.z.value
        )
        processor.processMagn(magn, update.timeStamp)
    }

    private fun handleFusionData(data: MemsSensorFusionInfo) {
        data.quaternions.lastOrNull()?.value?.let { quaternion ->
            updateOrientationAndGravity(quaternion)
        }
    }

    private fun updateOrientationAndGravity(quaternion: Quaternion) {
        _uiState.update {
            it.copy(
                orientation = processor.currentOrientation,
                rawQuaternion = quaternion,
                smoothedQuaternion = processor.currentOrientation.toQuaternion()
            )
        }
    }

    private fun handleAccel(update: FeatureUpdate<*>) {
        val data = update.data as AccelerationInfo
        val rawAccel = Vector3(
            x = data.x.value * 0.00981f,
            y = data.y.value * 0.00981f,
            z = data.z.value * 0.00981f
        )
        val linearAccel = processor.calculateLinearAcceleration(rawAccel, update.timeStamp)

        _uiState.update {
            it.copy(
                rawAcceleration = rawAccel,
                linearAcceleration = linearAccel
            )
        }

        processor.processIMU(linearAccel, update.timeStamp)
    }

    private fun handleGyro(update: FeatureUpdate<*>) {
        val data = update.data as GyroscopeInfo
        val gyro = Vector3(
            x = data.x.value.degToRad(),
            y = data.y.value.degToRad(),
            z = data.z.value.degToRad()
        )
        processor.processGyro(gyro, update.timeStamp)
        _uiState.update { it.copy(angularVelocity = gyro) }
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
        sensorJob?.cancel()
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

    private fun Float.degToRad() = Math.toRadians(this.toDouble()).toFloat()

}
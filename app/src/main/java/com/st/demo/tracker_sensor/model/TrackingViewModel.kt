package com.st.demo.tracker_sensor.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.st.blue_sdk.BlueManager
import com.st.blue_sdk.features.Feature
import com.st.blue_sdk.features.FeatureUpdate
import com.st.blue_sdk.features.acceleration.Acceleration
import com.st.blue_sdk.features.acceleration.AccelerationInfo
import com.st.blue_sdk.features.gyroscope.Gyroscope
import com.st.blue_sdk.features.gyroscope.GyroscopeInfo
import com.st.blue_sdk.features.magnetometer.Magnetometer
import com.st.blue_sdk.features.magnetometer.MagnetometerInfo
import com.st.demo.tracker_sensor.utils.MadgwickAHRS
import com.st.demo.tracker_sensor.utils.QuaternionHelper
import com.st.demo.tracker_sensor.utils.Vector3
import com.st.demo.tracker_sensor.utils.toVector2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.sqrt

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val blueManager: BlueManager
) : ViewModel() {

    private var deviceId: String? = null
    private var featureUpdatesJob: Job? = null

    // Sensor features
    private lateinit var accelerometer: Feature<*>
    private lateinit var gyroscope: Feature<*>
    private lateinit var magnetometer: Feature<*>

    // Sensor fusion and tracking
    private val madgwickFilter = MadgwickAHRS(sampleRate = 50f)
    private var lastTimestamp = 0L
    private var velocity = Vector3.ZERO
    private var position = Vector3.ZERO
    private var prevPosition = Vector3.ZERO

    // Configuration
    private companion object {
        const val ZUPT_THRESHOLD = 0.5f // m/s²
        const val HIGHLIGHT_THRESHOLD = 3f // m/s²
        const val MIN_SENSOR_UPDATE_DELTA = 0.001f // seconds
    }

    // UI State
    private val _uiState = MutableStateFlow(TrackingState())
    val uiState: StateFlow<TrackingState> = _uiState

    fun startTracking(deviceId: String) {
        this.deviceId = deviceId
        val node = blueManager.getNode(deviceId) ?: return

        // Get required features
        val features = blueManager.nodeFeatures(deviceId).filter {
            it.name == Acceleration.NAME ||
                    it.name == Gyroscope.NAME ||
                    it.name == Magnetometer.NAME
        }

        // Store feature references
        accelerometer = features.first { it.name == Acceleration.NAME }
        gyroscope = features.first { it.name == Gyroscope.NAME }
        magnetometer = features.first { it.name == Magnetometer.NAME }

        // Enable features and start processing data
        featureUpdatesJob = viewModelScope.launch {
            blueManager.enableFeatures(deviceId, features)
            blueManager.getFeatureUpdates(
                deviceId,
                features,
                autoEnable = false
            ).collect { featureUpdate ->
                processFeatureUpdate(featureUpdate)
            }
        }
    }

    private fun processFeatureUpdate(update: FeatureUpdate<*>) {
        when (update.featureName) {
            Acceleration.NAME -> processAccelerometerData(update)
            Gyroscope.NAME -> processGyroscopeData(update)
            Magnetometer.NAME -> processMagnetometerData(update)
        }
    }

    private fun processAccelerometerData(update: FeatureUpdate<*>) {
        val data = update.data as AccelerationInfo
        val accel = Vector3(
            x = data.x.value * 0.00981f,
            y = data.y.value * 0.00981f,
            z = data.z.value * 0.00981f
        )
        _uiState.update { it.copy(rawAccel = Vector3(data.x.value, data.y.value, data.z.value)) }
        processSensorData(accel, Vector3.ZERO, Vector3.ZERO, update.timeStamp)
    }

    private fun processGyroscopeData(update: FeatureUpdate<*>) {
        val data = update.data as GyroscopeInfo
        val gyro = Vector3(
            x = data.x.value * 0.01745f,
            y = data.y.value * 0.01745f,
            z = data.z.value * 0.01745f
        )
        _uiState.update { it.copy(rawGyro = Vector3(data.x.value, data.y.value, data.z.value)) }
        processSensorData(Vector3.ZERO, gyro, Vector3.ZERO, update.timeStamp)
    }

    private fun processMagnetometerData(update: FeatureUpdate<*>) {
        val data = update.data as MagnetometerInfo
        val mag = Vector3(
            x = data.x.value,
            y = data.y.value,
            z = data.z.value
        )
        _uiState.update { it.copy(rawMag = mag) }
        processSensorData(Vector3.ZERO, Vector3.ZERO, mag, update.timeStamp)
    }

    private fun processSensorData(
        accel: Vector3,
        gyro: Vector3,
        mag: Vector3,
        timestamp: Long
    ) {
        // Handle first update
        if (lastTimestamp == 0L) {
            lastTimestamp = timestamp
            prevPosition = position.copy() // Initialize previous position
            return
        }

        val deltaTime = (timestamp - lastTimestamp) / 1e9f
        lastTimestamp = timestamp

        // Update Madgwick filter with new sensor data
        madgwickFilter.update(
            gyro.x, gyro.y, gyro.z,
            accel.x, accel.y, accel.z,
            mag.x, mag.y, mag.z
        )

        // Calculate gravity vector from orientation
        val gravity = QuaternionHelper.quaternionToGravity(madgwickFilter.quaternion)
        val linearAccel = accel - gravity

        // Apply Zero Velocity Update (ZUPT) to minimize drift
        if (linearAccel.length() < ZUPT_THRESHOLD) {
            velocity = Vector3.ZERO
        } else {
            // Integrate acceleration to update velocity
            velocity += linearAccel * deltaTime
            // Integrate velocity to update position
            position += velocity * deltaTime
        }

        // Calculate incremental displacement from previous position
        val dx = position.x - prevPosition.x
        val dy = position.y - prevPosition.y
        val distanceDelta = sqrt(dx * dx + dy * dy) // XY-plane movement only

        // Optional: Clamp position to half-field boundaries (10x10 meters)
        position = Vector3(
            x = position.x.coerceIn(0f, 10f),
            y = position.y.coerceIn(0f, 10f),
            z = position.z
        )

        // Update UI state with new data
        _uiState.update { state ->
            val newHighlights = if (linearAccel.length() > HIGHLIGHT_THRESHOLD) {
                state.highlights + position.toVector2()
            } else {
                state.highlights
            }

            state.copy(
                position = position.toVector2(),
                totalDistance = state.totalDistance + distanceDelta, // Accumulate all movement
                highlights = newHighlights,
                lastUpdate = timestamp
            )
        }

        // Store current position for next calculation
        prevPosition = position.copy()
    }

    fun stopTracking() {
        deviceId?.let { id ->
            viewModelScope.launch {
                blueManager.disableFeatures(id, listOf(accelerometer, gyroscope, magnetometer))
            }
        }
        featureUpdatesJob?.cancel()
        resetState()
    }

    private fun resetState() {
        prevPosition = Vector3.ZERO
        velocity = Vector3.ZERO
        position = Vector3.ZERO
        lastTimestamp = 0L
        _uiState.value = TrackingState()
    }

}
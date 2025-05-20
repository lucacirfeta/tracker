package com.st.demo.ml_prevision

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jtransforms.fft.FloatFFT_1D
import org.tensorflow.lite.Interpreter
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt

class ModelPrevision @Inject constructor(
    context: Context
) {
    // Configurazione costanti dal Python
    private companion object {
        const val ACC_SCALE = 1000f
        const val GYRO_SCALE = 1000f * (180 / PI).toFloat()
        const val MAG_SCALE = 1000f
        const val WINDOW_SIZE = 100
        private val FLIP_TRANSFORM = floatArrayOf(-1f, -1f, 1f)
        val CATEGORIES = listOf("Forehand", "Backhand", "Smash", "Idle")
    }

    // Componenti del modello
    private val interpreter: Interpreter
    private val scalerMean: FloatArray
    private val scalerScale: FloatArray

    // Buffer dati sensori
    private val sensorBuffer = ArrayDeque<SensorData>()
    private var currentOrientation = "front"

    init {
        // Caricamento modello e parametri
        interpreter = Interpreter(loadModelFile(context))
        val npzParams = loadNpzParams(context)
        scalerMean = npzParams["mean"] ?: FloatArray(27)
        scalerScale = npzParams["scale"] ?: FloatArray(27)
    }

    fun addSensorData(
        acc: Triple<Float, Float, Float>,
        gyro: Triple<Float, Float, Float>,
        mag: Triple<Float, Float, Float>
    ) {
        sensorBuffer.add(SensorData(acc, gyro, mag))
        manageBufferWindow()
    }

    fun getPrediction(): String {
        return if (sensorBuffer.size < WINDOW_SIZE) {
            "Idle"
        } else {
            runBlocking {
                withContext(Dispatchers.Default) {
                    processWindow(sensorBuffer.toList())
                }
            }
        }
    }

    private fun manageBufferWindow() {
        // Mantiene solo gli ultimi WINDOW_SIZE elementi con sovrapposizione 50%
        if (sensorBuffer.size > WINDOW_SIZE * 2) {
            repeat(WINDOW_SIZE) { sensorBuffer.removeFirst() }
        }
    }

    private fun processWindow(window: List<SensorData>): String {
        val (normalizedData, detectedOrientation) = normalizeAndDetectOrientation(window)
        currentOrientation = detectedOrientation

        val transformedData = applyOrientationTransform(normalizedData)
        val features = extractFeatures(transformedData)
        val scaledFeatures = applyScaler(features)

        return runInference(scaledFeatures)
    }

    private fun normalizeAndDetectOrientation(data: List<SensorData>): Pair<List<SensorData>, String> {
        // Normalizzazione fisica
        val normalized = data.map {
            SensorData(
                acc = Triple(
                    it.acc.first / ACC_SCALE,
                    it.acc.second / ACC_SCALE,
                    it.acc.third / ACC_SCALE
                ),
                gyro = Triple(
                    it.gyro.first * (PI.toFloat() / 180f) / GYRO_SCALE,
                    it.gyro.second * (PI.toFloat() / 180f) / GYRO_SCALE,
                    it.gyro.third * (PI.toFloat() / 180f) / GYRO_SCALE
                ),
                mag = Triple(
                    it.mag.first / MAG_SCALE,
                    it.mag.second / MAG_SCALE,
                    it.mag.third / MAG_SCALE
                )
            )
        }

        // Rilevamento orientamento
        val initialAccZ = normalized.take(100).map { it.acc.third }.average()
        val orientation = if (initialAccZ > 0) "front" else "back"

        return Pair(normalized, orientation)
    }

    private fun applyOrientationTransform(data: List<SensorData>): List<SensorData> {
        return data.map {
            if (currentOrientation == "back") {
                SensorData(
                    acc = applyFlipTransform(it.acc),
                    gyro = applyFlipTransform(it.gyro),
                    mag = applyFlipTransform(it.mag)
                )
            } else {
                it
            }
        }
    }

    private fun applyFlipTransform(vector: Triple<Float, Float, Float>): Triple<Float, Float, Float> {
        return Triple(
            vector.first * FLIP_TRANSFORM[0],
            vector.second * FLIP_TRANSFORM[1],
            vector.third * FLIP_TRANSFORM[2]
        )
    }

    private fun extractFeatures(data: List<SensorData>): FloatArray {
        // Features temporali (18 elementi)
        val accFeatures = calculateTemporalFeatures(data.map { it.acc })   // 6 elementi (mean_x, std_x, ...)
        val gyroFeatures = calculateTemporalFeatures(data.map { it.gyro }) // 6 elementi
        val magFeatures = calculateTemporalFeatures(data.map { it.mag })   // 6 elementi

        // Features FFT (9 elementi)
        val fftAcc = calculateFFTPerAxis(data.map { it.acc })  // 3 elementi (asse X/Y/Z)
        val fftGyro = calculateFFTPerAxis(data.map { it.gyro })
        val fftMag = calculateFFTPerAxis(data.map { it.mag })

        return accFeatures + gyroFeatures + magFeatures + fftAcc + fftGyro + fftMag
    }

    private fun calculateFFTPerAxis(data: List<Triple<Float, Float, Float>>): FloatArray {
        // Calcola FFT per ogni asse separatamente
        val xFFT = calculateAxisFFT(data.map { it.first })
        val yFFT = calculateAxisFFT(data.map { it.second })
        val zFFT = calculateAxisFFT(data.map { it.third })

        return floatArrayOf(xFFT, yFFT, zFFT)
    }

    private fun calculateAxisFFT(axisData: List<Float>): Float {
        val fft = FloatFFT_1D(axisData.size.toLong())
        val fftData = axisData.toFloatArray()
        val paddedData = FloatArray(fftData.size * 2).apply {
            fftData.forEachIndexed { i, v -> this[i] = v }
        }
        fft.realForward(paddedData)

        // Media delle magnitudini FFT (come in Python)
        return paddedData.toList()
            .chunked(2) { (re, im) -> sqrt(re.pow(2) + im.pow(2)) }
            .average()
    }

    private fun calculateTemporalFeatures(data: List<Triple<Float, Float, Float>>): FloatArray {
        val x = data.map { it.first }
        val y = data.map { it.second }
        val z = data.map { it.third }

        return floatArrayOf(
            x.average(), x.std(),
            y.average(), y.std(),
            z.average(), z.std()
        )
    }

    private fun applyScaler(features: FloatArray): FloatArray {
        return features.mapIndexed { i, value ->
            (value - scalerMean[i]) / scalerScale[i]
        }.toFloatArray()
    }

    private fun runInference(features: FloatArray): String {
        // Ottieni i parametri di quantizzazione dal modello
        val inputTensor = interpreter.getInputTensor(0)
        val inputScale = inputTensor.quantizationParams().scale
        val inputZeroPoint = inputTensor.quantizationParams().zeroPoint.toFloat()

        // Quantizza i dati di input a INT8
        val quantizedFeatures = features.map { value ->
            (value / inputScale + inputZeroPoint).toInt().coerceIn(-128, 127).toByte()
        }.toByteArray()

        // Prepara buffer di input/output
        val inputBuffer = ByteBuffer.allocateDirect(quantizedFeatures.size).apply {
            put(quantizedFeatures)
            rewind()
        }

        val outputBuffer = ByteBuffer.allocateDirect(CATEGORIES.size).apply {
            order(ByteOrder.nativeOrder())
        }

        // Esegui inference
        interpreter.run(inputBuffer, outputBuffer)

        // Dequantizza l'output
        val outputTensor = interpreter.getOutputTensor(0)
        val outputScale = outputTensor.quantizationParams().scale
        val outputZeroPoint = outputTensor.quantizationParams().zeroPoint.toFloat()

        val output = FloatArray(CATEGORIES.size)
        outputBuffer.rewind()
        for (i in 0 until CATEGORIES.size) {
            output[i] = (outputBuffer.get().toInt() - outputZeroPoint) * outputScale
        }

        return CATEGORIES[output.indices.maxBy { output[it] }]
    }

    private fun loadModelFile(context: Context): ByteBuffer {
        val fileDescriptor = context.assets.openFd("racket_model.tflite")
        val inputStream = fileDescriptor.createInputStream()
        val fileSize = fileDescriptor.length
        val buffer = inputStream.channel.map(
            java.nio.channels.FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileSize
        )
        inputStream.close()
        return buffer
    }

    private fun loadNpzParams(context: Context): Map<String, FloatArray> {
        return try {
            context.assets.open("preprocessing_params.npz").use { inputStream ->
                val zip = ZipInputStream(inputStream)
                val params = mutableMapOf<String, FloatArray>()

                var entry: ZipEntry?
                while (zip.nextEntry.also { entry = it } != null) {
                    entry?.let { zipEntry ->
                        val arrayName = zipEntry.name.removeSuffix(".npy")
                        val numpyData = parseNpyData(zip)
                        params[arrayName] = numpyData
                    }
                }
                params
            }
        } catch (e: Exception) {
            Log.e("TRACKERLOG", "Error loading NPZ", e)
            throw RuntimeException("NPZ loading failed", e)
        }
    }

    private fun parseNpyData(inputStream: InputStream): FloatArray {
        // Read header
        val header = readNpyHeader(inputStream)
        val dtype = header.descr

        // Read binary data
        val byteBuffer = ByteBuffer.wrap(inputStream.readBytes())
        byteBuffer.order(if (dtype.startsWith("<")) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)

        val floatBuffer = byteBuffer.asFloatBuffer()
        val floatArray = FloatArray(floatBuffer.remaining())
        floatBuffer.get(floatArray)

        return floatArray
    }

    private fun readNpyHeader(inputStream: InputStream): NpyHeader {
        // Leggi il magic number corretto (6 byte)
        val magic = ByteArray(6).apply { inputStream.read(this) }

        // Verifica il magic number corretto per .npy (0x93N U M P Y in ISO-8859-1)
        val expectedMagic = byteArrayOf(
            0x93.toByte(),
            'N'.code.toByte(),
            'U'.code.toByte(),
            'M'.code.toByte(),
            'P'.code.toByte(),
            'Y'.code.toByte()
        )

        if (!magic.contentEquals(expectedMagic)) {
            throw IllegalArgumentException("Invalid NPY magic number")
        }

        val version = ByteArray(2).apply { inputStream.read(this) }
        val headerLength = readHeaderLength(inputStream, version)

        // Read header content
        val headerBytes = ByteArray(headerLength).apply { inputStream.read(this) }
        val headerString = String(headerBytes, Charsets.UTF_8)

        // Parse header dictionary
        val pattern = """\{(.*)\}""".toRegex()
        val match =
            pattern.find(headerString) ?: throw IllegalArgumentException("Invalid header format")
        val entries = match.groupValues[1].split(", '")
            .associate {
                val (key, value) = it.replace("'", "").split(": ")
                key.trim() to value.trim()
            }

        return NpyHeader(
            descr = entries["descr"] ?: "",
            fortranOrder = entries["fortran_order"]?.toBoolean() == true,
            shape = parseShape(entries["shape"] ?: "()")
        )
    }

    private fun readHeaderLength(inputStream: InputStream, version: ByteArray): Int {
        return when (version[0]) {
            1.toByte() -> {
                val buffer = ByteArray(2).apply { inputStream.read(this) }
                ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            }

            2.toByte() -> {
                val buffer = ByteArray(4).apply { inputStream.read(this) }
                ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).int
            }

            else -> throw IllegalArgumentException("Unsupported NPZ version")
        }
    }

    private fun parseShape(shapeStr: String): IntArray {
        val a = shapeStr.replace("(", "").replace(")", "")
        val b = a.split(",")
        return b.filter { it.isNotBlank() }
            .map { it.trim().toInt() }
            .toIntArray()
    }

    private data class NpyHeader(
        val descr: String,
        val fortranOrder: Boolean,
        val shape: IntArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as NpyHeader

            if (fortranOrder != other.fortranOrder) return false
            if (descr != other.descr) return false
            if (!shape.contentEquals(other.shape)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = fortranOrder.hashCode()
            result = 31 * result + descr.hashCode()
            result = 31 * result + shape.contentHashCode()
            return result
        }
    }


    data class SensorData(
        val acc: Triple<Float, Float, Float>,
        val gyro: Triple<Float, Float, Float>,
        val mag: Triple<Float, Float, Float>
    )
}

// Estensioni per calcoli statistici
fun List<Float>.average() = sum() / size
fun List<Float>.std(): Float {
    val mean = average()
    return sqrt(map { (it - mean).pow(2) }.average())
}
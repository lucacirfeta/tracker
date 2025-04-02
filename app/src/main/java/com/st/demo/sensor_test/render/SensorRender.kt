package com.st.demo.sensor_test.render

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import dev.romainguy.kotlin.math.Quaternion
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class SensorRenderer(context: Context) : GLSurfaceView.Renderer {
    private var quaternion: Quaternion? = null
    private val vertexBuffer: FloatBuffer
    private val colorBuffer: FloatBuffer
    private var program: Int = 0
    private var positionHandle: Int = 0
    private var colorHandle: Int = 0
    private var mMVPMatrixHandle: Int = 0

    companion object {
        private const val TAG = "RENDER_SENSOR_TEST"
    }

    // Vertici per un cubo 3D
    private val cubeVertices = floatArrayOf(
        // Front face (rosso)
        -0.5f, -0.5f,  0.5f,  // 0: bottom-left
        0.5f, -0.5f,  0.5f,  // 1: bottom-right
        -0.5f,  0.5f,  0.5f,  // 2: top-left
        0.5f,  0.5f,  0.5f,  // 3: top-right

        // Back face (verde)
        0.5f, -0.5f, -0.5f,  // 4: bottom-right
        -0.5f, -0.5f, -0.5f,  // 5: bottom-left
        0.5f,  0.5f, -0.5f,  // 6: top-right
        -0.5f,  0.5f, -0.5f,  // 7: top-left

        // Left face (blu)
        -0.5f, -0.5f, -0.5f,  // 8: back-bottom
        -0.5f, -0.5f,  0.5f,  // 9: front-bottom
        -0.5f,  0.5f, -0.5f,  // 10: back-top
        -0.5f,  0.5f,  0.5f,  // 11: front-top

        // Right face (giallo)
        0.5f, -0.5f,  0.5f,  // 12: front-bottom
        0.5f, -0.5f, -0.5f,  // 13: back-bottom
        0.5f,  0.5f,  0.5f,  // 14: front-top
        0.5f,  0.5f, -0.5f,  // 15: back-top

        // Top face (ciano)
        -0.5f,  0.5f,  0.5f,  // 16: front-left
        0.5f,  0.5f,  0.5f,  // 17: front-right
        -0.5f,  0.5f, -0.5f,  // 18: back-left
        0.5f,  0.5f, -0.5f,  // 19: back-right

        // Bottom face (magenta)
        -0.5f, -0.5f, -0.5f,  // 20: back-left
        0.5f, -0.5f, -0.5f,  // 21: back-right
        -0.5f, -0.5f,  0.5f,  // 22: front-left
        0.5f, -0.5f,  0.5f   // 23: front-right
    )

    // Colori per ogni faccia del cubo
    private val cubeColors = floatArrayOf(
        // Front face (rosso)
        1.0f, 0.0f, 0.0f, 1.0f,
        1.0f, 0.0f, 0.0f, 1.0f,
        1.0f, 0.0f, 0.0f, 1.0f,
        1.0f, 0.0f, 0.0f, 1.0f,

        // Back face (verde)
        0.0f, 1.0f, 0.0f, 1.0f,
        0.0f, 1.0f, 0.0f, 1.0f,
        0.0f, 1.0f, 0.0f, 1.0f,
        0.0f, 1.0f, 0.0f, 1.0f,

        // Left face (blu)
        0.0f, 0.0f, 1.0f, 1.0f,
        0.0f, 0.0f, 1.0f, 1.0f,
        0.0f, 0.0f, 1.0f, 1.0f,
        0.0f, 0.0f, 1.0f, 1.0f,

        // Right face (giallo)
        1.0f, 1.0f, 0.0f, 1.0f,
        1.0f, 1.0f, 0.0f, 1.0f,
        1.0f, 1.0f, 0.0f, 1.0f,
        1.0f, 1.0f, 0.0f, 1.0f,

        // Top face (ciano)
        0.0f, 1.0f, 1.0f, 1.0f,
        0.0f, 1.0f, 1.0f, 1.0f,
        0.0f, 1.0f, 1.0f, 1.0f,
        0.0f, 1.0f, 1.0f, 1.0f,

        // Bottom face (magenta)
        1.0f, 0.0f, 1.0f, 1.0f,
        1.0f, 0.0f, 1.0f, 1.0f,
        1.0f, 0.0f, 1.0f, 1.0f,
        1.0f, 0.0f, 1.0f, 1.0f
    )

    init {
        vertexBuffer = ByteBuffer.allocateDirect(cubeVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(cubeVertices)
                position(0)
            }

        colorBuffer = ByteBuffer.allocateDirect(cubeColors.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(cubeColors)
                position(0)
            }
    }

    private val vertexShaderCode =
        "uniform mat4 uMVPMatrix;" +
                "attribute vec4 vPosition;" +
                "attribute vec4 aColor;" +
                "varying vec4 vColor;" +
                "void main() {" +
                "  gl_Position = uMVPMatrix * vPosition;" +
                "  vColor = aColor;" +
                "}"

    private val fragmentShaderCode =
        "precision mediump float;" +
                "varying vec4 vColor;" +
                "void main() {" +
                "  gl_FragColor = vColor;" +
                "}"

    fun updateQuaternion(q: Quaternion?) {
        this.quaternion = q
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.9f, 0.9f, 0.9f, 1.0f) // Grigio chiaro
        GLES20.glEnable(GLES20.GL_DEPTH_TEST) // Abilita depth test
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val scratch = FloatArray(16)
        val modelMatrix = FloatArray(16)
        Matrix.setIdentityM(modelMatrix, 0)

        // Applica la rotazione dal quaternione
        quaternion?.let { q ->
            val rotationMatrix = FloatArray(16)
            q.toRotationMatrix(rotationMatrix)
            Matrix.multiplyMM(scratch, 0, modelMatrix, 0, rotationMatrix, 0)
        } ?: run {
            Matrix.setIdentityM(scratch, 0)
        }

        // Imposta la posizione della telecamera
        val viewMatrix = FloatArray(16)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 3f, 0f, 0f, 0f, 0f, 1.0f, 0.0f)

        // Calcola la matrice di proiezione
        val projectionMatrix = FloatArray(16)
        Matrix.perspectiveM(projectionMatrix, 0, 45f, 1f, 0.1f, 100f)
        val mvpMatrix = FloatArray(16)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, mvpMatrix, 0, scratch, 0)

        // Disegna il cubo
        GLES20.glUseProgram(program)

        positionHandle = GLES20.glGetAttribLocation(program, "vPosition").also {
            GLES20.glEnableVertexAttribArray(it)
            GLES20.glVertexAttribPointer(
                it,
                3,
                GLES20.GL_FLOAT,
                false,
                0,
                vertexBuffer
            )
        }

        colorHandle = GLES20.glGetAttribLocation(program, "aColor").also {
            GLES20.glEnableVertexAttribArray(it)
            GLES20.glVertexAttribPointer(
                it,
                4,
                GLES20.GL_FLOAT,
                false,
                0,
                colorBuffer
            )
        }

        mMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mvpMatrix, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)   // Front
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 4, 4)   // Back
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 8, 4)   // Left
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 12, 4)  // Right
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 16, 4)  // Top
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 20, 4)  // Bottom

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        // Controllo errori
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Shader error: ${GLES20.glGetShaderInfoLog(shader)}")
        }

        return shader
    }

    private fun Quaternion.toRotationMatrix(matrix: FloatArray) {
        val x = this.x
        val y = this.y
        val z = this.z
        val w = this.w

        val x2 = x * x
        val y2 = y * y
        val z2 = z * z
        val xy = x * y
        val xz = x * z
        val yz = y * z
        val wx = w * x
        val wy = w * y
        val wz = w * z

        matrix[0] = 1.0f - 2.0f * (y2 + z2)
        matrix[1] = 2.0f * (xy - wz)
        matrix[2] = 2.0f * (xz + wy)
        matrix[3] = 0.0f

        matrix[4] = 2.0f * (xy + wz)
        matrix[5] = 1.0f - 2.0f * (x2 + z2)
        matrix[6] = 2.0f * (yz - wx)
        matrix[7] = 0.0f

        matrix[8] = 2.0f * (xz - wy)
        matrix[9] = 2.0f * (yz + wx)
        matrix[10] = 1.0f - 2.0f * (x2 + y2)
        matrix[11] = 0.0f

        matrix[12] = 0.0f
        matrix[13] = 0.0f
        matrix[14] = 0.0f
        matrix[15] = 1.0f
    }
}
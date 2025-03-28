package com.st.demo.render

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.android.filament.Skybox
import com.st.demo.render.model.RenderingViewModel
import com.st.demo.render.utils.QuaternionHelper
import dev.romainguy.kotlin.math.Quaternion
import io.github.sceneview.Scene
import io.github.sceneview.environment.Environment
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenderingScreen(
    viewModel: RenderingViewModel = hiltViewModel(),
    navController: NavController,
    deviceId: String
) {
    val state by viewModel.uiState.collectAsState()

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)

    val childNodes = rememberNodes()
    var modelNode by remember { mutableStateOf<ModelNode?>(null) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        val modelInstance = modelLoader.createModelInstance(
            assetFileLocation = "models/racket.glb" // Percorso del modello
        )
        modelNode = ModelNode(
            modelInstance = modelInstance,
            scaleToUnits = 0.5f,
            centerOrigin = Position(0f, -0.5f, 0f)
        ).apply {
            isVisible = true
        }
        childNodes.add(modelNode!!)
    }


    val lightGrayColor = floatArrayOf(0.827f, 0.827f, 0.827f, 1.0f) // #D3D3D3
    val environment = remember(engine) {
        Environment(
            skybox = Skybox.Builder().color(lightGrayColor).build(engine),
            indirectLight = null
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rendering") },
                actions = {
                    IconButton(onClick = {
                        viewModel.reset()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }) {
                        Icon(Icons.Default.Refresh, "Reset")
                    }
                    IconButton(onClick = { viewModel.savePosition() }) {
                        Icon(Icons.Default.Save, "Save")
                    }
                    RenderControls(
                        viewModel = viewModel,
                        deviceId = deviceId,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                        viewModel.stopListeningClicked(deviceId)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.LightGray)
                .padding(innerPadding)
        ) {
            Scene(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                environment = environment,
                childNodes = childNodes
            )

            LaunchedEffect(state.rawQuaternion, state.offsetQuaternion) {
                val raw = state.rawQuaternion
                val offset = state.offsetQuaternion

                // Convert to math library Quaternion using helper functions
                val adjustedQuaternion = QuaternionHelper.multiply(
                    QuaternionHelper.inverse(offset),
                    raw
                )

                modelNode?.quaternion = Quaternion(
                    x = adjustedQuaternion.qi,
                    y = adjustedQuaternion.qj,
                    z = adjustedQuaternion.qk,
                    w = adjustedQuaternion.qs
                )
            }
        }
    }
}

@Composable
private fun RenderControls(
    viewModel: RenderingViewModel,
    deviceId: String
) {
    var isTracking by remember { mutableStateOf(false) }

    IconButton(onClick = {
        if (isTracking) {
            viewModel.stopListeningClicked(deviceId)
        } else {
            viewModel.startListening(deviceId)
        }
        isTracking = !isTracking
    }) {
        Icon(
            imageVector = if (isTracking) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = if (isTracking) "Stop Tracking" else "Start Tracking"
        )
    }
}
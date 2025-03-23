/*
 * Copyright (c) 2022(-0001) STMicroelectronics.
 * All rights reserved.
 * This software is licensed under terms that can be found in the LICENSE file in
 * the root directory of this software component.
 * If no LICENSE file comes with this software, it is provided AS-IS.
 */
package com.st.demo.feature_detail

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.st.demo.R

@SuppressLint("MissingPermission")
@Composable
fun FeatureDetail(
    navController: NavHostController,
    viewModel: FeatureDetailViewModel,
    deviceId: String,
    featureName: String
) {
    val backHandlingEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.startCalibration(deviceId, featureName)
    }

    BackHandler(enabled = backHandlingEnabled) {
        viewModel.disconnectFeature(deviceId = deviceId, featureName = featureName)

        navController.popBackStack()
    }

    val features = viewModel.featureUpdates

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        Text(stringResource(R.string.st_feature_featureNameLabel, featureName))

        Spacer(modifier = Modifier.height(4.dp))

        Text(stringResource(R.string.st_feature_updatesLabel))

        Spacer(modifier = Modifier.height(4.dp))

        Text("${features.value}")
    }

    LaunchedEffect(true) {
        viewModel.observeFeature(deviceId = deviceId, featureName = featureName)
        viewModel.sendExtendedCommand(featureName = featureName, deviceId = deviceId)
    }
}
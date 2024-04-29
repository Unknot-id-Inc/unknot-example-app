package com.example.unknotexampleapp

import android.Manifest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
inline fun PermissionsProvider(
    permissions: List<String>,
    content: @Composable (Boolean, () -> Unit) -> Unit
) {
    val permissionState = rememberMultiplePermissionsState(
        // ACCESS_BACKGROUND_LOCATION must be requested separately because it requires going to
        // system settings instead of just showing a dialog
        permissions.filter { it != Manifest.permission.ACCESS_BACKGROUND_LOCATION }
    )

    val backgroundPermissionState = permissions.find { it == Manifest.permission.ACCESS_BACKGROUND_LOCATION }?.let {
        rememberPermissionState(it)
    }

    val requestAllPermissions = {
        if (!permissionState.allPermissionsGranted)
            permissionState.launchMultiplePermissionRequest()
        else if (backgroundPermissionState != null && !backgroundPermissionState.status.isGranted)
            backgroundPermissionState.launchPermissionRequest()
    }

    LaunchedEffect(permissionState.allPermissionsGranted) {
        if (!permissionState.allPermissionsGranted ||
            (permissionState.allPermissionsGranted && backgroundPermissionState?.status?.isGranted == false)
        )
            requestAllPermissions()
    }

    content(
        permissionState.allPermissionsGranted && backgroundPermissionState?.status?.isGranted ?: true,
        requestAllPermissions
    )
}
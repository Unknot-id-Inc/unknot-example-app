package com.example.unknotexampleapp

import android.Manifest
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState

/**
 * Constructs permissions list based on [Build.VERSION.SDK_INT].
 * @param base list of permissions required regardless of API version
 * @param vers list of [Build.VERSION_CODES] to list of permissions pairs. If [Build.VERSION_CODES]
 *             is positive, the permissions are required only if the current API is greater than or
 *             equal to it. If negative, the permissions are required only if the current API is
 *             less than the version code.
 */
fun permissionsCompat(base: List<String>, vararg vers: Pair<Int, List<String>>): List<String> =
    base + vers
        .filter {
            if (it.first < 0) -it.first > Build.VERSION.SDK_INT
            else it.first <= Build.VERSION.SDK_INT
        }
        .flatMap { it.second }

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
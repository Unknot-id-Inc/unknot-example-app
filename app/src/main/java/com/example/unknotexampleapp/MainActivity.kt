package com.example.unknotexampleapp

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.example.unknotexampleapp.ui.theme.UnknotExampleAppTheme
import org.unknot.android_sdk.SdkArgs
import org.unknot.android_sdk.ServiceState
//import org.unknot.android_sdk.UnknotServiceCallback
//import org.unknot.android_sdk.UnknotServiceConnection
import org.unknot.android_sdk.UnknotServiceController

private val permissions = listOfNotNull(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_WIFI_STATE,
    Manifest.permission.CHANGE_WIFI_STATE,
    Manifest.permission.CHANGE_NETWORK_STATE,
    Manifest.permission.ACCESS_NETWORK_STATE,
    Manifest.permission.WAKE_LOCK,
    Manifest.permission.FOREGROUND_SERVICE,
    Manifest.permission.ACTIVITY_RECOGNITION,
    Manifest.permission.BLUETOOTH_ADMIN,
    Manifest.permission.ACCESS_BACKGROUND_LOCATION
) +
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) listOf(
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.POST_NOTIFICATIONS
        ) else listOf()) +
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) else listOf())

class MainActivity : ComponentActivity(), UnknotServiceCallback {

    private val serviceConnection = UnknotServiceConnection(this)

    private var serviceState: ServiceState? by mutableStateOf(null)
    private var serviceBound by mutableStateOf(false)
    private var batchCount by mutableIntStateOf(0)

    private val notification = ExampleNotification(this)

    private val sdkArgs = SdkArgs(
        apiKey = BuildConfig.API_KEY,
        deviceId = "435",
        locationId = "",
        authTarget = BuildConfig.AUTH_TARGET,
        ingesterTarget = BuildConfig.INGESTER_TARGET
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        serviceConnection.autoBind(this)

        notification.registerChannel()

        setContent {
            UnknotExampleAppTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    PermissionsProvider(permissions) { allGranted, request ->
                        if (allGranted) {
                            ServiceControls(
                                state = serviceState,
                                bound = serviceBound,
                                batchCount = batchCount,
                                onStart = {
                                    UnknotServiceController.startDataCollection(
                                        ctx = this@MainActivity,
                                        args = sdkArgs,
                                        notification = notification.getNotification("Session running")
                                    )
                                },
                                onStop = {
                                    UnknotServiceController.stopDataCollection(
                                        ctx = this@MainActivity,
                                        notification = null
                                    )
                                }
                            )
                        } else {
                            Button(
                                onClick = { request() }
                            ) {
                                Text("Request Permissions")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onBatchUpdate(count: Int, total: Int) {
        batchCount = count
    }

    override fun onBound() {
        serviceBound = true
    }

    override fun onUnbound() {
        serviceBound = false
    }

    override fun onUpdateServiceState(state: ServiceState) {
        serviceState = state
    }
}

@Composable
fun ServiceControls(
    state: ServiceState?,
    bound: Boolean,
    batchCount: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Field("Service bound", bound)
        Field("Service state",
            when (state) {
                is ServiceState.Running -> "Running"
                is ServiceState.Idle -> "Idle"
                is ServiceState.Error -> "Error"
                ServiceState.Syncing -> "Syncing"
                ServiceState.Unspecified -> "Unspecified"
                null -> "Stopped"
            }
        )

        Field("Session running", state is ServiceState.Running)

        Field("Session ID", (state as? ServiceState.Running)?.sessionId ?: "null")

        Field("Batches to sync", "$batchCount")

        if (state is ServiceState.Running) {
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(
                    contentColor = Color.White,
                    containerColor = Color.Red
                )
            ) {
                Text("STOP SERVICE")
            }
        } else {
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(
                    contentColor = Color.White,
                    containerColor = Color(0xff007700)
                )
            ) {
                Text("START SERVICE")
            }
        }

    }
}

@Composable
fun Field(
    label: String,
    content: @Composable RowScope.() -> Unit
) {
    Row {
        Text(
            text = "$label: ",
            fontWeight = FontWeight.Bold,
        )
        content()
    }
}

@Composable
fun Field(
    label: String,
    value: Boolean
) {
    Field(label) {
        Text(
            text = if (value) "Yes" else "No",
            color = if (value) Color.Green else Color.Red
        )
    }
}

@Composable
fun Field(
    label: String,
    value: String
) {
    Field(label) {
        Text(value)
    }
}

@Preview(showBackground = true)
@Composable
fun ServiceControlsPreview() {
    ServiceControls(
        state = ServiceState.Running("1234", "abcdefg1234-zzz"),
        bound = true,
        batchCount = 0,
        onStart = {},
        onStop = {}
    )
}
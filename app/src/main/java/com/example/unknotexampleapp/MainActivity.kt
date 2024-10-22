package com.example.unknotexampleapp

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.unknotexampleapp.ui.theme.UnknotExampleAppTheme
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import org.unknot.android_sdk.ForwardLocation
import org.unknot.android_sdk.SdkArgs
import org.unknot.android_sdk.ServiceState
import org.unknot.android_sdk.UnknotServiceController

private val basePermissions = listOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_WIFI_STATE,
    Manifest.permission.CHANGE_WIFI_STATE,
    Manifest.permission.CHANGE_NETWORK_STATE,
    Manifest.permission.ACCESS_NETWORK_STATE,
    Manifest.permission.WAKE_LOCK,
    Manifest.permission.FOREGROUND_SERVICE,
    Manifest.permission.ACTIVITY_RECOGNITION,
    Manifest.permission.ACCESS_BACKGROUND_LOCATION
)

@SuppressLint("InlinedApi")
private val permissionsRequired = permissionsCompat(basePermissions,
    Build.VERSION_CODES.TIRAMISU to listOf(
        Manifest.permission.NEARBY_WIFI_DEVICES,
        Manifest.permission.POST_NOTIFICATIONS
    ),
    Build.VERSION_CODES.S to listOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE
    ),
    -Build.VERSION_CODES.R to listOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
    )
)

class MainActivity : ComponentActivity(), UnknotServiceCallback {

    private val serviceConnection = UnknotServiceConnection(this)

    private var serviceState: ServiceState? by mutableStateOf(null)
    private var serviceBound by mutableStateOf(false)
    private var batchCount by mutableIntStateOf(0)
    private var currentLocation by mutableStateOf<ForwardLocation?>(null)

    private val notification = ExampleNotification(this)

    private val sdkArgs = SdkArgs(
        apiKey = BuildConfig.API_KEY,
        deviceId = BuildConfig.DEVICE_ID,
        locationId = "",
        authTarget = BuildConfig.AUTH_TARGET,
        ingesterTarget = BuildConfig.INGESTER_TARGET,
        streamerTarget = BuildConfig.STREAM_TARGET
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
                    PermissionsProvider(permissionsRequired) { allGranted, request ->
                        if (allGranted) {
                            ServiceControls(
                                state = serviceState,
                                bound = serviceBound,
                                batchCount = batchCount,
                                currentLocation = currentLocation,
                                onStart = {
                                    UnknotServiceController.startDataCollection(
                                        ctx = this@MainActivity,
                                        args = sdkArgs,
                                        notification = notification.getNotification("Session running"),
                                        forwardPredictions = true
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

    override fun onLocation(location: ForwardLocation) {
        currentLocation = location
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
fun Map(
    modifier: Modifier = Modifier,
    currentLocation: ForwardLocation?
) {
    val cameraPositionState = rememberCameraPositionState()

    var needsPosition by remember { mutableStateOf(true) }
    val currentMarker = rememberMarkerState("current")

    val ctx = LocalContext.current
    val unknotMarker = remember { markerBmp(ctx.resources, R.drawable.unknot_logo, Color.Green).asImageBitmap() }
    val androidMarker = remember { markerBmp(ctx.resources, R.drawable.ic_android_black_24dp, Color.Red).asImageBitmap() }

    LaunchedEffect(currentLocation) {
        currentLocation?.let {
            val newpos = LatLng(currentLocation.latitude, currentLocation.longitude)
            if (needsPosition) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition(
                            newpos,
                            15f,
                            0f,
                            0f
                        )
                    )
                )
                needsPosition = false
            }

            currentMarker.position = newpos
        }
    }


    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(
            mapToolbarEnabled = false,
            indoorLevelPickerEnabled = false,
            zoomControlsEnabled = false,
            rotationGesturesEnabled = false,
            tiltGesturesEnabled = false
        ),
        properties = MapProperties(
            isMyLocationEnabled = true
        )
    ) {
        if (currentLocation != null) {
            MarkerComposable(currentLocation.provider.name, state = currentMarker) {
                Image(
                    bitmap = if (currentLocation.provider == ForwardLocation.Provider.Unknot)
                        unknotMarker else androidMarker,
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
fun ServiceControls(
    state: ServiceState?,
    bound: Boolean,
    batchCount: Int,
    currentLocation: ForwardLocation?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Map(
            modifier = Modifier
                .fillMaxWidth(.8f)
                //.weight(1f)
                .height(300.dp)
                .padding(bottom = 30.dp)
            ,
            currentLocation = currentLocation
        )

        Column(Modifier.align(Alignment.CenterHorizontally)) {
            Field("Service bound", bound)
            Field(
                "Service state",
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
            Field("Device ID", BuildConfig.DEVICE_ID)
            Field("Session ID", (state as? ServiceState.Running)?.sessionId ?: "null")
            Field("Batches to sync", "$batchCount")

            Spacer(Modifier.height(10.dp))

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
        onStop = {},
        currentLocation = null
    )
}
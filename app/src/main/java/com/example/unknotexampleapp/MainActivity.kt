package com.example.unknotexampleapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowColumn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.example.unknotexampleapp.ui.theme.UnknotExampleAppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.GestureOptions
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import org.unknot.android_sdk.ForwardLocation
import org.unknot.android_sdk.SdkArgs
import org.unknot.android_sdk.ServiceState
import org.unknot.android_sdk.UnknotServiceController
import org.unknot.android_sdk.rest_api.UnknotRest

private val basePermissions = listOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_WIFI_STATE,
    Manifest.permission.CHANGE_WIFI_STATE,
    Manifest.permission.CHANGE_NETWORK_STATE,
    Manifest.permission.ACCESS_NETWORK_STATE,
    Manifest.permission.WAKE_LOCK,
    Manifest.permission.READ_PHONE_STATE
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
    /*-Build.VERSION_CODES.R to listOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
    ),*/
    Build.VERSION_CODES.Q to listOf(
        Manifest.permission.ACTIVITY_RECOGNITION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
    ),
    Build.VERSION_CODES.P to listOf(
        Manifest.permission.FOREGROUND_SERVICE,
    )
)

val DEVICE_ID = stringPreferencesKey("device_id")
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
fun deviceIdFlow(ctx: Context): Flow<String?> = ctx.dataStore.data.map { prefs ->
    prefs[DEVICE_ID]
}

class MainActivity : ComponentActivity(), UnknotServiceCallback {

    private val serviceConnection = UnknotServiceConnection(this)

    private var serviceState: ServiceState? by mutableStateOf(null)
    private var serviceBound by mutableStateOf(false)
    private var batchCount by mutableIntStateOf(0)
    private var currentLocation by mutableStateOf<ForwardLocation?>(null)

    private val notification = ExampleNotification(this)

    private fun sdkArgs(deviceId: String) = SdkArgs(
        apiKey = BuildConfig.API_KEY,
        deviceId = deviceId,
        locationId = "",
        authTarget = BuildConfig.AUTH_TARGET,
        ingesterTarget = BuildConfig.INGESTER_TARGET,
        streamerTarget = BuildConfig.STREAM_TARGET
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        serviceConnection.registerBindingOnLifecycle(application, lifecycle)

        notification.registerChannel()

        setContent {
            UnknotExampleAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding(),
                        contentAlignment = Alignment.Center
                    ) {
                        PermissionsProvider(permissionsRequired) { allGranted, request ->
                            if (allGranted) {
                                val ctx = LocalContext.current
                                val prefsDeviceId by deviceIdFlow(ctx).collectAsStateWithLifecycle(null)

                                LaunchedEffect(prefsDeviceId) {
                                    if (prefsDeviceId == null) {
                                        val rest = UnknotRest(BuildConfig.AUTH_TARGET, BuildConfig.API_KEY)
                                        val newDeviceId = rest.registerDevice(ctx)
                                        ctx.dataStore.edit {
                                            it[DEVICE_ID] = newDeviceId
                                        }
                                    }
                                }

                                prefsDeviceId?.let { deviceId ->
                                    ServiceControls(
                                        modifier = Modifier.fillMaxWidth(),
                                        state = serviceState,
                                        deviceId = prefsDeviceId,
                                        bound = serviceBound,
                                        batchCount = batchCount,
                                        currentLocation = currentLocation,
                                        onStart = {
                                            UnknotServiceController.startDataCollection(
                                                ctx = this@MainActivity,
                                                args = sdkArgs(deviceId),
                                                notification = notification.getNotification("Session running"),
                                                forwardPredictions = true,
                                                // change to true if you only want Unknot locations to be
                                                // provided, even if the service is currently unavailable
                                                // because of some network or other error. When set to false
                                                // Android system locations will be forwarded if no Unknot
                                                // location has been provided for 10 or more seconds
                                                disableForwardAndroidLocation = false
                                            )
                                        },
                                        onStop = {
                                            UnknotServiceController.stopDataCollection(
                                                ctx = this@MainActivity,
                                                notification = null
                                            )
                                        }
                                    )
                                }
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
    }

    override fun onBatchUpdate(count: Int, total: Int) {
        batchCount = count
    }

    override fun onLocation(location: ForwardLocation) {
        println("Forward Location: $location")
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
    val cameraState = rememberCameraState()
    var needsPosition by remember { mutableStateOf(true) }

    val res = LocalResources.current
    val unknotMarker = remember { markerBmp(res, R.drawable.unknot_logo, Color.Green).asImageBitmap() }
    val androidMarker = remember { markerBmp(res, R.drawable.ic_android_black_24dp, Color.Red).asImageBitmap() }
    val currentPosition = remember(currentLocation) {
        currentLocation?.let {
            Position(
                longitude = it.longitude,
                latitude = it.latitude
            )
        }
    }

    LaunchedEffect(currentLocation) {
        currentPosition?.let {
            if (needsPosition) {
                cameraState.animateTo(
                    CameraPosition(
                        target = it,
                        zoom = 15.0,
                        tilt = 0.0,
                        bearing = 0.0
                    )
                )
                needsPosition = false
            }
        }
    }

    MaplibreMap(
        modifier = modifier,
        baseStyle = BaseStyle.Uri("asset://positron.json"),
        cameraState = cameraState,
        options = MapOptions(
            gestureOptions = GestureOptions.RotationLocked,
            ornamentOptions = OrnamentOptions.AllDisabled
        )
    ) {
        if (currentPosition != null && currentLocation != null) {
            val markerSource = rememberGeoJsonSource(
                GeoJsonData.Features(
                    Point(currentPosition)
                )
            )
            SymbolLayer(
                id = "current-location-marker",
                source = markerSource,
                iconImage = image(
                    if (currentLocation.provider == ForwardLocation.Provider.Unknot)
                        unknotMarker else androidMarker
                ),
                iconAnchor = const(SymbolAnchor.Bottom),
                iconAllowOverlap = const(true),
                iconIgnorePlacement = const(true)
            )
        }
    }
}

@Composable
fun ServiceControls(
    state: ServiceState?,
    bound: Boolean,
    deviceId: String?,
    batchCount: Int,
    currentLocation: ForwardLocation?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    notShort: Boolean = currentWindowAdaptiveInfoV2().windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (notShort) {
            Map(
                modifier = Modifier
                    .fillMaxWidth(.8f)
                    //.weight(1f)
                    .height(300.dp)
                    .padding(bottom = 30.dp),
                currentLocation = currentLocation
            )
        }

        if (notShort) {
            ServiceInfo(bound, state, deviceId, batchCount, true)

            Spacer(Modifier.height(10.dp))

            StartStopButton(
                state = state,
                onStart = onStart,
                onStop = onStop
            )
        } else {
            var focused by remember { mutableStateOf(false) }
            StartStopButton(
                modifier = Modifier
                    //.indication(remember { MutableInteractionSource() }, FocusIndication)
                    .onFocusChanged {
                        focused = it.isFocused
                    }
                    .let {
                        if (focused) it.border(3.dp, Color.Red)
                        else it
                    },
                state = state,
                onStart = onStart,
                onStop = onStop
            )

            Spacer(Modifier.height(10.dp))

            ServiceInfo(bound, state, deviceId, batchCount, false)
        }
    }
}

private class FocusIndicationNode(private val interactionSource: InteractionSource) :
    Modifier.Node(), DrawModifierNode {
    private var isFocused = false

    override fun onAttach() {
        coroutineScope.launch {
            var focusCount = 0
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> focusCount++
                    is FocusInteraction.Unfocus -> focusCount--
                }
                val focused = focusCount > 0
                if (isFocused != focused) {
                    isFocused = focused
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (isFocused) {
            drawRect(size = size, color = Color.Red, alpha = 1f, style = Stroke(3f))
        }
    }
}

object FocusIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return FocusIndicationNode(interactionSource)
    }

    override fun hashCode(): Int = -1

    override fun equals(other: Any?) = other === this
}

val ReverseArrangement = object : Arrangement.Vertical {
    override fun Density.arrange(
        totalSize: Int,
        sizes: IntArray,
        outPositions: IntArray
    ) {
        var current = 0
        // Iterate through sizes in reverse to calculate positions from the top
        for (i in sizes.indices.reversed()) {
            outPositions[i] = current
            current += sizes[i]
        }
    }
}

@Composable
fun ServiceInfo(
    bound: Boolean,
    state: ServiceState?,
    deviceId: String?,
    batchCount: Int,
    notShort: Boolean
) {
    FlowColumn(
        horizontalArrangement = Arrangement.spacedBy(10.dp)
        //maxLines = if (notShort) 1 else 2
    ) {
        Field(if (notShort) "Service bound" else "Bound", bound)
        Field(
            if (notShort) "Service state" else "State",
            when (state) {
                is ServiceState.Running -> "Running"
                is ServiceState.Idle -> "Idle"
                is ServiceState.Error -> "Error"
                ServiceState.Syncing -> "Syncing"
                ServiceState.Unspecified -> "Unspecified"
                null -> "Stopped"
            }
        )


        Field(if (notShort) "Session running" else "Running", state is ServiceState.Running)
        Field("Device ID", deviceId)
        Field("Session ID", (state as? ServiceState.Running)?.sessionId ?: "null")
        Field(if (notShort) "Batches to sync" else "Batches", "$batchCount")
    }
}

@Composable
fun StartStopButton(
    state: ServiceState?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state is ServiceState.Running) {
        Button(
            modifier = modifier,
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
            modifier = modifier,
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
    value: String?
) {
    Field(label) {
        Text(value ?: "null")
    }
}

@Preview(showBackground = true, heightDp = 100)
@Composable
fun ServiceControlsPreview() {
    ServiceControls(
        modifier = Modifier.fillMaxSize(),
        state = ServiceState.Running("1234", "abcdefg1234-zzz"),
        deviceId = "123",
        bound = true,
        batchCount = 0,
        onStart = {},
        onStop = {},
        currentLocation = null,
        notShort = false
    )
}
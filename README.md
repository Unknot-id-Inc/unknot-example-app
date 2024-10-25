# Unknot Example App

This app demonstrates basic usage and control of the Unknot Android SDK.

## Prerequisites
- Minimum Android SDK level 28 (Android 9)
- An Unknot API key

## Permissions
The Unknot SDK requires the host app to request these permissions to function:
 - [ACCESS_FINE_LOCATION](https://developer.android.com/reference/android/Manifest.permission#ACCESS_FINE_LOCATION)
 - [ACCESS_COARSE_LOCATION](https://developer.android.com/reference/android/Manifest.permission#ACCESS_COARSE_LOCATION)
 - [POST_NOTIFICATIONS](https://developer.android.com/reference/android/Manifest.permission#POST_NOTIFICATIONS) (only requried for >= API 33)
 - [NEARBY_WIFI_DEVICES](https://developer.android.com/reference/android/Manifest.permission#NEARBY_WIFI_DEVICES) (may be removed in future versions)
 - [ACCESS_BACKGROUND_LOCATION](https://developer.android.com/reference/android/Manifest.permission#ACCESS_BACKGROUND_LOCATION)

 These permissions are also required, but generally do not invoke a user prompt:
 - [ACCESS_WIFI_STATE](https://developer.android.com/reference/android/Manifest.permission#ACCESS_WIFI_STATE)
 - [CHANGE_WIFI_STATE](https://developer.android.com/reference/android/Manifest.permission#CHANGE_WIFI_STATE)
 - [ACCESS_NETWORK_STATE](https://developer.android.com/reference/android/Manifest.permission#ACCESS_NETWORK_STATE)
 - [CHANGE_NETWORK_STATE](https://developer.android.com/reference/android/Manifest.permission#CHANGE_NETWORK_STATE)
 - [WAKE_LOCK](https://developer.android.com/reference/android/Manifest.permission#WAKE_LOCK)
 - [FOREGROUND_SERVICE](https://developer.android.com/reference/android/Manifest.permission#FOREGROUND_SERVICE)
 - [BLUETOOTH_ADMIN](https://developer.android.com/reference/android/Manifest.permission#BLUETOOTH_ADMIN)
 - [BLUETOOTH_SCAN](https://developer.android.com/reference/android/Manifest.permission#BLUETOOTH_SCAN) (only required for >= API 31)
 - [BLUETOOTH_CONNECT](https://developer.android.com/reference/android/Manifest.permission#BLUETOOTH_CONNECT) (only required for >= API 31)

 These permissions are optional:
 - [ACTIVITY_RECOGNITION](https://developer.android.com/reference/android/Manifest.permission#ACTIVITY_RECOGNITION)

 > Refer to [PermissionsProvider.kt](app/src/main/java/com/example/unknotexampleapp/PermissionsProvider.kt) for an example of how to request all these permissions at app startup.

## Gradle
Add to the `dependencies` block of the app module's `build.gradle`:
```
implementation("org.unknot:android-sdk:1.0.22")
```

> **The SDK library is not hosted yet! Check back soon to get further details on how to configure maven to download the library.**

## Config Values
The SDK is configured with 3 values that are usually fixed:
- `API_KEY`: UUID that provides access to your Unknot account.
- `AUTH_TARGET`: URL to an Unknot REST server.
- `INGESTER_TARGET`: URL to an Unknot synchronization server. 

> These values could be hardcoded into your app as normal variables, however it
> may be prudent to try to keep them at least somewhat secret, especially
> `API_KEY`. While there's no straightforward way to truly hide them from anyone
> with access to the APK, this example app uses a method that avoids having to
> commit the values to a repo. Checkout [local.properties.example](local.properties.example) and
> [build.gradle.kts](app/build.gradle.kts#L26) for reference.

## Device ID
Beyond the config values, you will need a Device ID to start a session. If you
do not have an ID for a particular device, you need to register it using the
Unknot REST API. You may register a device in the app itself using the
`registerDevice()` function from the `UnknotRest` class. For instance:
```kotlin
val restApi = UnknotRest(AUTH_TARGET, API_KEY)
val deviceId = restApi.registerDevice("some unique id")
```
Or register a device outside the app, for example with cURL:
```bash
curl -X POST -H "Content-Type: application/x-www-form-urlencoded" -d "api_key=$API_KEY&location_id=0&manufacturer=some brand&model=some model&android_id=some unique id" $AUTH_TARGET/auth/register/device 
```

> Refer to [API-README](API-README.md) for the REST API documentation.

## Starting a session with No Location
The one last piece of information needed to start a session is the Location ID.
Locations can be pre-configured on the server with maps and a set of latitude
and longitude points, however starting a session with dynamic, or No Location,
is also possible. In this case the location prediction algorithms will
dynamically choose the current location based on GPS, WiFi, and any
point-of-references provided.

To start a session with No Location, prepare an `SdkArgs` object like so:
```kotlin
val sdkArgs = SdkArgs(
    apiKey = BuildConfig.API_KEY,
    deviceId = DEVICE_ID,
    locationId = "", // No location is defined by an empty string
    authTarget = BuildConfig.AUTH_TARGET,
    ingesterTarget = BuildConfig.INGESTER_TARGET
)
```

Then pass this object to `UnknotServiceController.startDataCollection()`:
```kotlin
UnknotServiceController.startDataCollection(
    ctx = this@MainActivity,
    args = sdkArgs,
    notification = notification.getNotification("Session running"),
    forwardPredictions = true // flag to enable returning predicted locations from service
)
```
> Note the `notification` parameter. Since `UnknotService` runs as an Android
> Foreground Service, it requires a notification to be displayed as long as the
> service is running in the foreground. Otherwise it might get shut down by
> Android and be unable to continue data collection while the user is using
> other apps.
>
> Providing an Android
> [Notification](https://developer.android.com/reference/android/app/Notification)
> object allows UnknotService to display a notification customized to your app.
> If instead `null` is provided, the service will use a generic notification.
> Checkout
> [ExampleNotification.kt](app/src/main/java/com/example/unknotexampleapp/ExampleNotification.kt)
> for an example of how to construct and provide a custom notification that
> opens the main app when tapped.

## Bind to UnknotService to monitor service state
To get realtime updates on the state of `UnknotService`, it must be
[binded](https://developer.android.com/develop/background-work/services/bound-services#Binding)
to. `UnknotService` is a regular Android Service that runs in a separate
process, so bound communication must use an
[AIDL](https://developer.android.com/guide/components/aidl) binder. This SDK
provides a helper class to make setting up the connection a bit easier. 

For instance, if you want to bind the service to `MainActivity` (as is done in
this example app), first have that class implement the `UnknotServiceCallback`
interface, then instantiate `UnknotServiceConnection` with a reference to the
`MainActivity` instance:
```kotlin
class MainActivity : ComponentActivity(), UnknotServiceCallback {
    private val serviceConnection = UnknotServiceConnection(this)
    ...
```

`UnknotServiceCallback` defines 5 methods to implement that are called on state
changes in the service. In this example app we simply update variables in
`MainActivity` to provide the updated state to the app's UI:
```kotlin
private var serviceState: ServiceState? by mutableStateOf(null)
private var serviceBound by mutableStateOf(false)
private var batchCount by mutableIntStateOf(0)

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

override fun onLocation(location: ForwardLocation) {
    currentLocation = location
}

```

Finally, in `onCreate` for instance, call the `autoBind` method:
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    serviceConnection.autoBind(this)
    ...
```

If you want more control over how the service binding takes place, you can use
normal [Android binding procedures](https://developer.android.com/develop/background-work/services/bound-services#Binding).
Refer to
[UnknotServiceConnection.kt](app/src/main/java/com/example/unknotexampleapp/UnknotServiceConnection.kt)
to see how the internal AIDL interfaces are implemented.

## Service State
The `onUpdateServiceState` callback provides an object derived from the
`ServiceState` class. The specific derived class determines the overall state of
the service, and provides details associated with that state:
```kotlin
when (serviceState) {
    is ServiceState.Running -> {
        println("Service is running: ${serviceState.sessionId}")
    }

    is ServiceState.Syncing -> {
        println("Service is syncing data, no session running")
    }

    is ServiceState.Idle -> {
        println("Service is idle")
    }

    is ServiceState.Error -> {
        println("Service error: ${serviceState.message}")
    }
}
```

## Session ID
Once a session is successfully started, a unique Session Token will be provided.
This token is a reference to the data collected in the current session by the
specific device, including the trajectory predictions. The ID is provided in the
`ServiceState.Running` object as the `sessionId` property.

> Note a session can be running without yet having a `sessionId`. This means
> that the service is collecting data, but has not yet successfully received the
> `sessionId` from the server. When `sessionId` is received,
> `onUpdateServiceState` will be called again with a new `ServiceState.Running`
> object with `sessionId` set. So after a session is started, you should expect
> to see 2 calls to `onUpdateServiceState`, the first with a
> `ServiceState.Running` object with `sessionId` as `null`, and the next with an
> actual value.

## End a running session
```kotlin
UnknotServiceController.stopDataCollection(
    ctx = this@MainActivity,
    notification = null
)
```
After a session is requested to be stopped, `ServiceState` will either
transition directly to `ServiceState.Idle`, or if data from the session still
needs to be synced with the server, will first return `ServiceState.Syncing`,
then `ServiceState.Idle` once the session data is fully synced.

## Retreiving location predictions
When the `UnknotServiceController.startDataCollection()` function is called with the
`forwardPredictions` parameter set to `true`, locations will be returned from the service via the
`onLocation(location: ForwardLocation)` method of `UnknotServiceCallback`. 

Depending on whether predicted locations are available, the `location` parameter will either contain
a predicted location from Unknot, or a location provided by Android FusedLocation. To distinguish
between Unknot locations and Android locations, check the `provider` field of the `ForwardLocation`
object.
```kotlin
when (location.provider) {
    Provider.Unknot -> // Unknot predicted location
    Provider.System -> // Android location
}
```

For Unknot predicted locations, the `level` field will also be set to the string value of the
current level, or "UNK" if the level is unknown.


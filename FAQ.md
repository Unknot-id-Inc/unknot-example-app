# FAQ

## Device ID

- What is `deviceId` actually used for on the backend? Does it affect the location in some way?

`deviceId` is the unique identifier used to distinguish each device in our DB. It's tied to the account it was registered on. It does not affect location. From the app perspective, it is used internally for authentication (along with the API_KEY) to our servers for uploading and receiving data.

- If I call registerDevice("client-id") again, on a second phone, will the server return the same `deviceId`, or will it issue a new one?

It will issue a new one. The parameter "client-id" is just a string used for record keeping (e.g. to keep track of devices on our dashboard), but it does not need to be unique. You can also call registerDevice(Context) in which case it will use [ANDROID_ID](https://developer.android.com/reference/android/provider/Settings.Secure.html#ANDROID_ID). This is the behavior on the Unknot app. However, providing a custom "client-id" provides a more human readable way to distinguish devices, as opposed to deviceId.

- Do I need to persist and reuse that deviceId for each user on every install, or could I (re)register each time without consequence?

Yes, you should persist and reuse `deviceId` after registration (the Unknot app does this using an internal SQLite database). You could re-register on every app start, but then you'd be getting a new device id for the same device. There's no harm in doing that, especially during testing, but we'd certainly recommend to persist `deviceId` for production use.

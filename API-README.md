# API Readme

## REST API

The REST API is used for device registration, retrieving a list of locations associated to an
account, and retrieving a token for use in sending gRPC requests.

The current host used for making requests to the REST API is:
```
http://k8s-api-gatewayi-0992b141be-1361074939.us-east-1.elb.amazonaws.com:80
```

### Device Registration

#### Endpoint
```
GET /auth/register/device
```

#### Parameters
- `api_key`: API key tied to account
- `location_id`: Always `0`
- `manufacturer`: Manufacturer of device, for iOS always "Apple"
- `model`: Model of device
- `android_id`: On Android, this is a 64-bit hex encoded integer that uniquely identifies the and OS build. If a similar UID is available on iOS, put it here, otherwise "null".

#### Response
JSON
- `device_id`: A unique ID that will be tied to this device and account. This ID is later used to request auth tokens, and is displayed on the app's main screen.

### Token Request
```
GET /auth/login
```

#### Parameters
- `api_key`: API key, same as used to register device.
- `device_id`: Device ID returned from registration request.

#### Response
JSON
- `access_token`: Token to be used in gRPC headers.
- `refresh_token`: Refresh token used when primary token expires.
- `customer_id`: Customer ID associated with the account's API key. This is currently used for requesting the location list

> Note, in the current Android build, `refresh_token` is not used. A new token is simply requested using Token Request whenever the current token becomes invalid

### Locations
```
GET /locations
```

#### Parameters
- `latitude`: Current latitude value of device.
- `longitude`: Current longitude value of device.
- `customer_id`: Customer ID returned with Token Request

> If current location lat/lng is unknown, do not include these parameters in the request.

#### Headers
- `Authorization`: "Bearer $ACCESS_TOKEN"

#### Response
JSON
- `id`: Location ID
- `name`: Location name
- `point`:
    - `latitude`: Latitude value
    - `longitude`: Longitude value
- `customer_id`: Customer ID associated with location

Example:
```
{
    "locations": [
        {
            "id": "1",
            "name": "Test Location",
            "point": {
                "latitude": -72.08998,
                "longitude": 41.49223
            },
            "customer_id": "1"
        }
    ]
}
```



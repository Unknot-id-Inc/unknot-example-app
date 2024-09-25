# Trajectory Streaming WebSocket Service

This WebSocket service pushes real-time location prediction information for a given device and session 
ID. 

There are currently two endpoints, one for test and prod servers:
- **test**: wss://trajectory-streamer-test.unknot.id/websocket 
- **prod**: wss://trajectory-streamer.unknot.id/websocket

The service requires the running session to be provided to it via the `session_id` URL parameter. 

So for example, for session 1234 running on test:
```
wss://trajectory-streamer-test.unknot.id/websocket?session_id=1234 
```

For authentication, the `access_token` from the [/auth/login](API-README.md#token-request) endpoint
must be provided via the `Authentication` header.

```
Authentication: Bearer $TOKEN
```

> If the requested session is not currently running, the server will return a 500 error.

Once connected, current and previous trajectory data will be pushed periodically of the form:
```json
{
  "current": [
    {
      "trajectories": [
        {
          "x": 20.9067309139499,
          "y": 52.66904397520534,
          "z": -0.00025266364538367725,
          "timestamp": 1727207295480,
          "time": "2024-09-24 19:48:15",
          "longitude": -81.1982826997724,
          "latitude": 28.58958454367984,
          "layer": "3259_bldg",
          "speed": null,
          "direction": "N",
          "device_id": null,
          "session_id": null,
          "batch_id": null,
          "pose_id": null
        }
      ],
      "batch_id": "13"
    }
  ],
  "previous": [
    {
      "trajectories": [
        {
          "x": 20.9067309139499,
          "y": 52.66904397520534,
          "z": -0.00025266364538367725,
          "timestamp": 1727207295480,
          "time": "2024-09-24 19:48:15",
          "longitude": -81.1982826997724,
          "latitude": 28.58958454367984,
          "layer": "3259_bldg",
          "speed": null,
          "direction": "N",
          "device_id": null,
          "session_id": null,
          "batch_id": null,
          "pose_id": null
        }
      ],
      "batch_id": "12"
    },
    ...
  ]
}
```

`current` contains the most recent prediction. `previous` may contain past corrected predictions.
For the first push after connection, `previous` will contain all previous predictions besides the 
latest. This is so a client connecting midway through a session is still able to reconstruct the
entire trajectory of the session, even points from before first connection. Subsequent pushes
`previous` will be empty, or contain corrections to previous batches. Batches can be identified and
updated via the `batch_id` field.

> One way to structure this data to account for past corrections is to use a Sorted Set (`TreeSet`
> in Java), with object comparisons based on `batch_id`. That way the `current` and `previous`
> arrays can be combined into a single set, which will automatically sort and replace past entries.
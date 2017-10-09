# FastForward HTTP

Plugin that provides an HTTP input and output plugin.

The input is implemented as an HTTP service, see [Endpoints](#endpoints) below for details.

The output is implemented as an HTTP client, capable of interacting with that service.

## Endpoints

### `POST /v1/batch`
Content-Type: application/json

Send a batch of metrics to be processed by ffwd.

#### Request Body

The request body is in JSON, and has the following fields.

| Field      | Description                                                                      |
| ---------- | -------------------------------------------------------------------------------- |
| commonTags | Tags that are common for every metric in the batch                               |
| points     | List of points in the batch, see [Point] below for details on individual points. |

###### Point

| Field     | Description                                |
| --------- | ------------------------------------------ |
| key       | Namespace for metric                       |
| tags      | Tags specific for this metric              |
| value     | Value                                      |
| timestamp | Timestamp, in milliseconds from Unix epoch |


##### Example

```json
{
  "commonTags": {
    "host": "database.example.com"
  },
  "points": [
    {
      "key": "system",
      "tags": {
        "what": "cpu-used-percentage",
        "unit": "%"
      },
      "value": 0.42,
      "timestamp": 1508508795000
    },
    {
      "key": "system",
      "tags": {
        "what": "disk-used-bytes",
        "unit": "B",
        "disk_name": "/dev/sda"
      },
      "value": 42000000000,
      "timestamp": 1508508795000
    }
  ]
}
```


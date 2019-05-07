---
title: HTTP
---



# FastForward HTTP

Plugin that provides an HTTP input and output plugin.

The input is implemented as an HTTP service, see [Endpoints](#endpoints) below for details.

The output is implemented as an HTTP client, capable of interacting with that service.

{::options parse_block_html="true" /}
<div class="api-endpoint">

## Endpoints

By default the HTTP server listens on port 8080.

{: .heading .post}
### POST /v1/batch

<span class="content-type">application/json</span>

Send a batch of metrics to be processed by ffwd.

#### Request Body

The request body is in JSON, and has the following fields.

{: .table .table-bordered}
| Field           | Description                                                                     |
| --------------- | --------------------------------------------------------------------------------|
| `commonTags`      | Tags that are common for every metric in the batch                              |
| `commonResource`  | Resource identifiers that are common for every metric in the batch              |
| `points`          | List of points in the batch, see `[Point]` below for details on individual points |

#### Point

{: .table .table-bordered}
| Field     | Description                                   |
| --------- | --------------------------------------------- |
| `key`       | Namespace for metric                          |
| `tags`      | Tags specific for this metric                 |
| `resource`  | Resource identifiers specific for this metric |
| `value`     | Value                                         |
| `timestamp` | Timestamp, in milliseconds from Unix epoch    |


##### Example

```bash
curl -XPOST -H "Content-Type: application/json" http://localhost:8080/v1/batch -d'
{
  "commonTags": {
    "host": "database.example.com"
  },
  "commonResource": {
     "resource-example": "foo"
  },
  "points": [
    {
      "key": "system",
      "tags": {
        "what": "cpu-used-percentage",
        "unit": "%"
      },
      "resource": {},
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
      "resource": {},
      "value": 42000000000,
      "timestamp": 1508508795000
    }
  ]
}'
```

</div>

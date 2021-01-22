---
title: OpenTelemetry
---

# FastForward OpenTelemetry

This module provies an output plugin for [OpenTelemetry metrics](https://github.com/open-telemetry/opentelemetry-proto/). It does not provide any tracing related functionality.

## Configuration

* `endpoint` - The gRPC endpoint to send metrics to.
* `headers` - An optional map of headers to include in the [MetricService export](https://github.com/open-telemetry/opentelemetry-proto/blob/v0.7.0/opentelemetry/proto/collector/metrics/v1/metrics_service.proto#L32) RPC.

Here is an example configuration using the OpenTelemetry plugin:

```
output:
  plugins:
    - type: opentelemetry
      endpoint: ingestion.example.com:443
      headers:
        Authentication: Bearer Z29vZCBqb2IgZGVjb2RpbmcgdGhpcyBlYXN0ZXIgZWdn
```

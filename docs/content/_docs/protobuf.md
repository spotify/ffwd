---
title: Protobuf
---

# FastForward Protobuf

This module contains an implementation of the protobuf protocol of FastForward.
Protobuf protocol is exposed on port 19091 UDP.

## Configuration

A basic configuration would look like the following.

```yaml
input:
  - type: protobuf
```

## Message Framing

The protocol frames messages as UDP datagrams.

Each message is a frame prefixed with the *version* and the *length* of the
entire frame.

```text
| version | length | data |
| 4       | 4      | *    |
```

The *version* field designates which version of the protocol is in use.
This determines the structure of the *data* field.

The *length* field designates how long the entire frame is supposed to be,
since UDP can crop the message, this is used for detecting buffer underruns.

## Message Structure

Messages in the *data* field are serialized according to the
[protobuf](http://code.google.com/p/protobuf/) protocol.

This serialization in this module piggy-backs from the implementation of
[ffwd-client-java](https://github.com/udoprog/ffwd-client-java).

## Client Implementations

* [java](https://github.com/spotify/ffwd-client-java)
* [c++](https://github.com/udoprog/libffwd-client)


## Troubleshooting

Large UDP payloads can be silently dropped if the socket buffer is not adequately sized. This usually manifests by having gaps in metrics.

By default `net.core.rmem_max` is set very low on most operating systems. Setting `net.core.rmem_max` to `26214400` which is ~25MB allows for bursts of metrics to be processed by FFWD without being dropped.


When ffwd creates the socket for the plugin `receiveBufferSize` should be set to the same size as the max size allowed by the kernel. 
```yaml
    - type: protobuf
      protocol:
        type: udp
        receiveBufferSize: 26214400
```

For more information about udp buffers check out...
https://medium.com/@CameronSparr/increase-os-udp-buffers-to-improve-performance-51d167bb1360


You can monitor for errors on the socket by checking `/proc/net/udp6` or `/proc/net/udp` depending on how you setup the listening socket. The last column is a counter of errors on the socket. Usually any errors on the socket are due to the buffer being full and packets being droppe

The port, part of the ip:port combo, is hex encoded and can be decoded with python.

```
cat /proc/net/udp6
 1222: 0100630A: 0100630A:4A38 01 00000000:00000000 00:00000000 00000000     0        0 36387 2 ffff931c5727ac00 0
```
 
```python
print int("0x4A38", 0)
19000

print int("0x4A93", 0)
19091
```

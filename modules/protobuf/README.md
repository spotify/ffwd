# FastForward Protobuf

This module contains an implementation of the protobuf protocol of FastForward.

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

* [java](https://github.com/udoprog/ffwd-java-client)
* [c++](https://github.com/udoprog/libffwd-client)

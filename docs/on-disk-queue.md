## On-disk serialization (WIP)

Write to a serial, peristed queue in `OutputManager`.
`OutputManager` will also be responsible for truncating this queue to asssert
that a size limitations are respected.

This would allow for a temporary loss of network, without losing data.

#### Operation

The entire queue is serialized into a set of files (`segments`) which are
limited in size according to `maxLogSize`, with the default being `100 MB`.

Each segment is according to a zero-padded, hex-encoded base offset of that
segment (example: `00000000ff`)

Incoming events and metrics are written to the `tail` segment linearly, until
it would be forced to grow larger than `maxLogSize`.
When this happens a new tail `segment` is allocated and the blob will be written
to the newly allocated tail `segment`.

A consumer maintains its `position` in the queue, and this is maintained in the
binary `index` file.
At a regular interval, a process will scan the current offset of all consumers
and trim the head of the queue.
Trimming involves unlinking all `segments` prior to a given `position`.

#### Files

Each `segment` is a binary file, with the following structure.

```
magic   | 4 | 4 byte magic, making up "FFLG" (0x46 0x46 0x4c 0x47) in ASCII.
version | 2 | Unsigned 2-byte short, indicating the current version of the
              segment format.
offset  | 8 | Unsigned offset in number of messages that is the start of this
              log
...
size    | 4 | An unsigned integer indicating the size of the next entry.
blob    | n | A byte blob with the above size.
... other entries until EOF.
```

The `index` is a binary file, with the following structure.

```
...
idlength | 4 | A 4 byte unsigned integer, indicating the length of the next id.
offset   | 8 | An 8 byte unsigned long, indicating the offset of the next id.
id       | n | A UTF-8 encoded string, which is the id of the given consumer.
               The encoded length of this string is given by `idlength`.
... other entries until EOF.
```

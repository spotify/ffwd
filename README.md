# ffwd-java &#187;
[![Build Status](https://travis-ci.org/spotify/ffwd-java.svg?branch=master)](https://travis-ci.org/spotify/ffwd-java)

This is a Java implementation of [ffwd](https://github.com/spotify/ffwd)

This project is currently: __experimental__, use at your own risk.

* [Protobuf Protocol](/modules/protobuf/)
* [Hacking](docs/hacking.md)
* [On-disk Persistent Queue (WIP)](docs/on-disk-queue.md)

# Building

This project is built using Maven.

```bash
$> mvn package
```

You can run the client using `tools/ffwd`.

```bash
$> tools/ffwd agent/ffwd.yaml
```

# Clients

* [java](https://github.com/udoprog/ffwd-java-client)
* [c++](https://github.com/udoprog/libffwd-client)

# Libraries

* [semantic-metrics (ffwd-reporter)](https://github.com/spotify/semantic-metrics)

# Code of Conduct

This project adheres to the [Open Code of Conduct][code-of-conduct]. By
participating, you are expected to honor this code.

[code-of-conduct]: https://github.com/spotify/code-of-conduct/blob/master/code-of-conduct.md

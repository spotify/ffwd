# ffwd-java &#187;
[![Build Status](https://travis-ci.org/spotify/ffwd-java.svg?branch=master)](https://travis-ci.org/spotify/ffwd-java)

This is a Java implementation of [ffwd](https://github.com/spotify/ffwd)

This project is currently: __experimental__, use at your own risk.

* [Protobuf Protocol](/modules/protobuf/)
* [JSON Protocol](/modules/json/)
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

#### Building a Debian Package

This project does not provide a single Debian package, this is primarily
because the current nature of the service (alpha state) does not mesh well with
stable releases.

Instead, you are encouraged to build your own using the provided scripts in
this project.

First run the `prepare-sources` script:

```bash
$ debian/bin/prepare-sources myrel 1
```

`myrel` will be the name of your release, it will be part of your package name
`ffwd-myrel`.

For the next step you'll need a Debian environment:

```bash
$ dpkg-buildpackage -uc -us
```

If you encounter problems, you can troubleshoot the build with `DH_VERBOSE`:

```bash
$ env DH_VERBOSE=1 dpkg-buildpackage -uc -us
```

# Local Debugging

Assuming you have [Maven][maven] installed, you can run the following to setup a local debug agent:

```
$> tools/ffwd agent/ffwd-local-debug.yaml
```

This will setup a ffwd with a lot of input plugins that are printed to stdout.

[maven]: https://maven.apache.org/

# Clients

* [Java](https://github.com/udoprog/ffwd-java-client)
* [Python](https://pypi.python.org/pypi/ffwd)
* [c++](https://github.com/udoprog/libffwd-client)

# Libraries

* [semantic-metrics (ffwd-reporter)](https://github.com/spotify/semantic-metrics)

# Code of Conduct

This project adheres to the [Open Code of Conduct][code-of-conduct]. By
participating, you are expected to honor this code.

[code-of-conduct]: https://github.com/spotify/code-of-conduct/blob/master/code-of-conduct.md

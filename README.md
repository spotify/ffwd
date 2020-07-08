# ffwd-java &#187;
[![Build Status](https://circleci.com/gh/spotify/ffwd.svg?style=svg)](https://circleci.com/gh/spotify/ffwd)
[![License](https://img.shields.io/github/license/spotify/ffwd.svg)](LICENSE)

ffwd is a flexible metric forwarding agent. It is intended to run locally on the system and receive metrics through a wide set of protocols and then forward them to your TSDB.

By running locally, it is easily available to receive pushed data from any application or service that is running on the same system.

ffwd decorates the received metrics with system-wide tags or attributes. By doing this, the application generating the data becomes simpler to build, maintain, and configure since it doesn't have to know where it is running. Only that ffwd is available on the loopback interface.

__Head over to https://spotify.github.io/ffwd/ for documentation.__


# Quick Start

ffwd can be started quickly with docker. This can be useful to run locally when troubleshooting metrics with your service.

```bash
docker run -it -p 19091:19091/udp -p 19000:19000 -p 8080:8080 spotify/ffwd:latest
```

# Production Debugging

If the debug port is enabled, metrics can be emited to a shell with netcat:

`nc localhost 19001`

# Clients

* [Java-UDP](https://github.com/spotify/ffwd-client-java)
* [Java-HTTP](https://github.com/spotify/ffwd-http-client)
* [Python](https://pypi.python.org/pypi/ffwd)
* [c++](https://github.com/udoprog/libffwd-client)

# Libraries

* [semantic-metrics (ffwd-reporter)](https://github.com/spotify/semantic-metrics)


# Developing

This project is built using Maven. The package phase will also build a debian package.

```bash
mvn package
```

You can run the client using `tools/ffwd`.

```bash
$> tools/ffwd agent/ffwd.yaml
```

## Testing

We run unit tests with Maven:

```
$ mvn test
```

A more comprehensive test suite is enabled with the `environment=test`
property.

```
$ mvn -D environment=test verify
```

This adds:

* [Checkstyle](http://checkstyle.sourceforge.net/)
* [FindBugs](http://findbugs.sourceforge.net/)

It is strongly recommended that you run the full test suite before setting up a
pull request, otherwise it will be rejected by the CI system.

# Code of Conduct

This project adheres to the [Open Code of Conduct][code-of-conduct]. By
participating, you are expected to honor this code.

[code-of-conduct]: https://github.com/spotify/code-of-conduct/blob/master/code-of-conduct.md

# Releasing

Releasing is done via the `maven-release-plugin`.

To release, run:

`mvn release:clean release:prepare -D autoVersionSubmodules=true`

You will be prompted for the release version and the next development version.

Add a Github release based on the tag that was created from the above command with notes on what changed.

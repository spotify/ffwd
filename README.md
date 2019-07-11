# ffwd-java &#187;
[![Build Status](https://travis-ci.org/spotify/ffwd.svg?branch=master)](https://travis-ci.org/spotify/ffwd)
[![License](https://img.shields.io/github/license/spotify/ffwd.svg)](LICENSE)


ffwd is a flexible metric forwarding agent. It is intended to run locally on the system and receive metrics through a wide set of protocols and then forward them to your TSDB.

By running locally, it is easily available to receive pushed data from any application or service that is running on the same system.

ffwd decorates the received metrics with system-wide tags or attributes. By doing this, the application generating the data becomes simpler to build, maintain, and configure since it doesn't have to know where it is running. Only that ffwd is available on the loopback interface.


This project is currently: __experimental__, use at your own risk.

__Head over to https://spotify.github.io/ffwd/ for documentation.__

# Building

This project is built using Maven. The package phase will also build a debian package.

```bash
$> mvn package
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
pull request, otherwise it will be rejected by Travis.

# Local Debugging

Assuming you have [Maven][maven] installed, you can run the following to setup a local debug agent:

```
$> tools/ffwd agent/ffwd-local-debug.yaml
```

This will setup a ffwd with a lot of input plugins that are printed to stdout.

[maven]: https://maven.apache.org/

# Clients

* [Java](https://github.com/udoprog/ffwd-java-client)
* [Java-HTTP](https://github.com/spotify/ffwd-http-client)
* [Python](https://pypi.python.org/pypi/ffwd)
* [c++](https://github.com/udoprog/libffwd-client)

# Libraries

* [semantic-metrics (ffwd-reporter)](https://github.com/spotify/semantic-metrics)

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


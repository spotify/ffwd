# ffwd-java &#187;
[![Build Status](https://travis-ci.org/spotify/ffwd.svg?branch=master)](https://travis-ci.org/spotify/ffwd)
[![License](https://img.shields.io/github/license/spotify/ffwd.svg)](LICENSE)


This is a Java implementation of [ffwd](https://github.com/spotify/ffwd-ruby)

This project is currently: __experimental__, use at your own risk.

* [Protobuf Protocol](/modules/protobuf/)
* [JSON Protocol](/modules/json/)
* [HTTP Protocol](/modules/http/)
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

Releasing is done via the `maven-release-plugin` and `nexus-staging-plugin` which are configured via the
`release` profile from the [parent pom](https://github.com/spotify/foss-root). Deploys are staged in oss.sonatype.org before being deployed to Maven Central. Check out the [maven-release-plugin docs](http://maven.apache.org/maven-release/maven-release-plugin/) and the [nexus-staging-plugin docs](https://help.sonatype.com/repomanager2) for more information.

To release, first run:

`mvn -P release release:prepare`

You will be prompted for the release version and the next development version. On success, follow with:

`mvn -P release release:perform`

When that is successful, create [a new release](https://github.com/spotify/ffwd/releases/new) in GitHub pointing to the tag that was created and any relevant release notes.

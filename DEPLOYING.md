To release `ffwd` run the below command. Ensure you are following the [semantic versioning](https://semver.org/) when creating a release!


You will need the following:

* GPG set up on the machine you're deploying from

Preparing a release goes through the [following](https://maven.apache.org/maven-release/maven-release-plugin/examples/prepare-release.html) release phases: 

* Check that there are no uncommitted changes in the sources
* Check that there are no SNAPSHOT dependencies
* Change the version in the POMs from x-SNAPSHOT to a new version (you will be prompted for the versions to use)
* Transform the SCM information in the POM to include the final destination of the tag
* Run the project tests against the modified POMs to confirm everything is in working order
* Commit the modified POMs
* Tag the code in the SCM with a version name (this will be prompted for)
* Bump the version in the POMs to a new value y-SNAPSHOT (these values will also be prompted for)
* Commit the modified POMs


```
# make and deploy a release
mvn release:clean release:prepare -Prelease

```


Then update https://github.com/spotify/ffwd/releases with release notes!

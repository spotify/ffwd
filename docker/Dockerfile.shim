FROM openjdk:11-jdk-alpine

RUN /usr/bin/java -version

COPY agent/target/ffwd-agent-0.0.1-SNAPSHOT.jar /ffwd-shim.jar
COPY docker/ffwd-shim.yaml /ffwd.yaml
COPY docker/ffwd-shim.log4j2.xml /ffwd.log4j2.xml

ENTRYPOINT /usr/bin/java -cp /ffwd-shim.jar -Dlog4j.configurationFile=/ffwd.log4j2.xml com.spotify.ffwd.FastForwardAgent

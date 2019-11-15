FROM maven:3.5.4-jdk-11 as builder
COPY . .
RUN mvn clean package
RUN mv agent/target/ffwd-agent-*.jar agent/target/ffwd-full.jar

# final image
FROM openjdk:11.0.3-slim-stretch

EXPOSE 19091/udp
EXPOSE 19000/tcp
EXPOSE 19000/udp
EXPOSE 8080/tcp

ENTRYPOINT ["/usr/bin/ffwd"]
CMD ["/etc/ffwd/ffwd.yaml"]

COPY agent/target/ffwd-full.jar /usr/share/ffwd/ffwd-full.jar
COPY agent/ffwd.yaml /etc/ffwd/ffwd.yaml
COPY agent/ffwd.sh /usr/bin/ffwd.sh

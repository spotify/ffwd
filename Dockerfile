FROM maven:3.5.4-jdk-8 as builder
COPY . .
RUN mvn clean package
RUN mv agent/target/ffwd-agent-*.jar agent/target/ffwd-full.jar

# final image
FROM openjdk:8-slim-stretch

EXPOSE 19091/udp
EXPOSE 19000/tcp
EXPOSE 19000/udp
EXPOSE 8080/tcp

ENTRYPOINT ["/usr/bin/ffwd"]
CMD ["/etc/ffwd/ffwd.yaml"]

COPY --from=0 agent/target/ffwd-full.jar /usr/share/ffwd/ffwd-full.jar
COPY agent/ffwd.yaml /etc/ffwd/ffwd.yaml
COPY agent/ffwd /usr/bin/ffwd


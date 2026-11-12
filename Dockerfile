# Build
FROM eclipse-temurin:21-jdk@sha256:0a3f329821dcadf54b2a3033a15e2e9f007069a9db43d7f51897e6b80e726b07

USER root

RUN apt-get update && apt-get install -y --no-install-recommends \
	git \
	curl \
	tar \
	maven

VOLUME /keycloak-nats-main
COPY . /keycloak-nats-main
RUN cd /keycloak-nats-main && ./gradlew shadowJar

RUN ls -la /keycloak-nats-main/build/libs


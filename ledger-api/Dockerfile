# Build and runtime are separate stages so the image ships a JRE and a jar, not a
# JDK, Maven and the whole dependency cache. The result is a few hundred megabytes
# instead of well over a gigabyte.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Dependencies are copied and resolved before the source. Docker caches each
# layer, so editing a Java file no longer re-downloads every dependency — which
# on a slow connection is most of the build time.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY ledger-api/pom.xml ledger-api/pom.xml
COPY ledger-notifier/pom.xml ledger-notifier/pom.xml
RUN chmod +x mvnw && ./mvnw -pl ledger-api -am -B -q dependency:go-offline

COPY ledger-api/src ledger-api/src
# Tests are not run here. They need Docker themselves, and running them inside a
# build that Docker is running is a problem worth not having; CI runs them.
RUN ./mvnw -pl ledger-api -am -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

# A non-root user, because a container that is compromised should not also be
# root inside its own filesystem.
RUN useradd --system --uid 1001 ledger
USER ledger

COPY --from=build /build/ledger-api/target/ledger-api.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# See ledger-api/Dockerfile for why the build is split in two stages.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY ledger-api/pom.xml ledger-api/pom.xml
COPY ledger-notifier/pom.xml ledger-notifier/pom.xml
RUN chmod +x mvnw && ./mvnw -pl ledger-notifier -am -B -q dependency:go-offline

COPY ledger-notifier/src ledger-notifier/src
RUN ./mvnw -pl ledger-notifier -am -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 1001 ledger
USER ledger

COPY --from=build /build/ledger-notifier/target/ledger-notifier.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

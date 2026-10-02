FROM maven:3.9.13-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml ./
COPY src ./src
COPY web ./web
RUN mvn -B -q test package

FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY --from=build /app/target/scheduler-1.0.0.jar app.jar
RUN mkdir -p /diagnostics && chown 10001:10001 /diagnostics
USER 10001:10001
EXPOSE 7540
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

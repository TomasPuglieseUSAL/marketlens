# Requiere Java 21 (pom.xml); compila el backend sin Maven local.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src/ ./src/
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin marketlens
COPY --from=build /workspace/target/*.jar /app/app.jar
USER marketlens
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

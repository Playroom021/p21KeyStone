# ---- Backend image (Spring Boot 3 / Java 21) ----
# Build:  docker build -t keystone-backend .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --create-home keystone
COPY --from=build /build/target/*.jar app.jar
USER keystone
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

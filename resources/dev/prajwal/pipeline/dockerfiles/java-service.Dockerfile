# Shared template for Spring Boot / plain Java services built with Maven.
ARG JAVA_VERSION=21

FROM maven:3.9-eclipse-temurin-${JAVA_VERSION} AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY . .
RUN mvn -B -q package -DskipTests && cp "$(ls target/*.jar | grep -v original | head -n 1)" /app.jar

FROM eclipse-temurin:${JAVA_VERSION}-jre
RUN useradd --system app
USER app
COPY --from=build /app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

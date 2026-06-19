FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src src
RUN mvn -B -ntp clean verify

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app

COPY --from=build /workspace/target/app.jar app.jar

EXPOSE 5000
ENTRYPOINT ["java", "-jar", "app.jar"]

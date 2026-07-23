FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -q package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S attendance && adduser -S attendance -G attendance
WORKDIR /app
COPY --from=build /workspace/target/attendance-*.jar app.jar
USER attendance
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN mkdir -p /var/wepa/uploads
COPY --from=build /app/target/*.jar /app/wepa.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/wepa.jar"]

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN mkdir -p /tmp/wepa/uploads /var/wepa/uploads
COPY --from=build /app/target/SGC1.0-1.0-SNAPSHOT.jar /app/wepa.jar
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/wepa.jar"]

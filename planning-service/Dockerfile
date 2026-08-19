FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY subject-service/pom.xml subject-service/pom.xml
COPY assessment-service/pom.xml assessment-service/pom.xml
COPY study-activity-service/pom.xml study-activity-service/pom.xml
COPY planning-service/pom.xml planning-service/pom.xml
COPY planning-service/src planning-service/src
RUN mvn -pl planning-service -am package -DskipTests
FROM eclipse-temurin:21-jre
COPY --from=build /workspace/planning-service/target/*.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]

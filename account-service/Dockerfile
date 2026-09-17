FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY messaging-support/pom.xml messaging-support/pom.xml
COPY messaging-support/src messaging-support/src
COPY account-service/pom.xml account-service/pom.xml
COPY subject-service/pom.xml subject-service/pom.xml
COPY assessment-service/pom.xml assessment-service/pom.xml
COPY study-activity-service/pom.xml study-activity-service/pom.xml
COPY planning-service/pom.xml planning-service/pom.xml
COPY account-service/src account-service/src
RUN mvn --batch-mode --no-transfer-progress -pl account-service -am package -DskipTests
FROM eclipse-temurin:21-jre
COPY --from=build /workspace/account-service/target/*.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]

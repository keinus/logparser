FROM eclipse-temurin:21.0.7_6-jre-ubi9-minimal

WORKDIR /app

COPY build/libs/logparser-0.2.1.jar /app/logparser-0.2.1.jar

ENTRYPOINT ["java","-jar","/app/logparser-0.2.1.jar"]

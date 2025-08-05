FROM eclipse-temurin:21.0.7_6-jre-ubi9-minimal

WORKDIR /app

COPY build/libs/logparser-0.1.0.jar logparser.jar

ENTRYPOINT ["java","-jar","./logparser.jar"]

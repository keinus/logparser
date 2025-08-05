FROM eclipse-temurin:21.0.7_6-jre-ubi9-minimal

WORKDIR /app

COPY build/libs/* /app/

ENTRYPOINT ["java","-jar","./logparser-0.2.0.jar"]

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY target/*.jar app.jar

EXPOSE 9003

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-9003} -jar app.jar"]

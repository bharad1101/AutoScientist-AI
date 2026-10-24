FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
COPY src/ src/

RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /app

RUN useradd --system --uid 10001 appuser

COPY --from=build /app/target/autoscientist-backend-1.0.0-SNAPSHOT.jar app.jar

# Create persistent-data directories and grant write access
RUN mkdir -p /app/uploads/datasets /app/data/h2 \
    && chown -R appuser:appuser /app/uploads /app/data

USER appuser

# Render supplies $PORT; we forward it to SERVER_PORT which application.yml reads.
# If PORT is unset (local docker run), the app defaults to 8080.
ENV PORT=8080

EXPOSE ${PORT}

ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75.0 -DSERVER_PORT=${PORT} -jar app.jar"]

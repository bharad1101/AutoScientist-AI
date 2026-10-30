FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
COPY src/ src/

RUN mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# gosu lets the entrypoint fix root-owned persistent-disk permissions and then
# drop privileges before starting the JVM.
RUN apt-get update \
    && apt-get install -y --no-install-recommends gosu \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 appuser \
    && useradd --system --uid 10001 --gid appuser --create-home appuser

COPY --from=build /app/target/autoscientist-backend-1.0.0-SNAPSHOT.jar app.jar
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

RUN chmod 0755 /usr/local/bin/docker-entrypoint.sh \
    && mkdir -p /app/uploads/datasets /app/data \
    && chown -R appuser:appuser /app/uploads /app/data

ENV SPRING_PROFILES_ACTIVE=mysql
ENV FILE_UPLOAD_DIR=/app/uploads/datasets

# Render supplies $PORT; the entrypoint forwards it to SERVER_PORT.
ENV PORT=8080

VOLUME ["/app/uploads"]

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]

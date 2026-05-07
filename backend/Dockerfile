# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21 AS build

ARG MAVEN_EXTRA_CA_CERT_BASE64=""
ARG MAVEN_INSECURE_SSL="true"

ENV MAVEN_SSL_FLAGS=""

RUN if [ "$MAVEN_INSECURE_SSL" = "true" ]; then \
      printf '%s' "-Dmaven.resolver.transport=wagon -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true" > /tmp/maven-ssl-flags; \
    else \
      printf '%s' "" > /tmp/maven-ssl-flags; \
    fi

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates \
    && if [ -n "$MAVEN_EXTRA_CA_CERT_BASE64" ]; then \
        printf '%s' "$MAVEN_EXTRA_CA_CERT_BASE64" | base64 -d > /usr/local/share/ca-certificates/maven-extra-ca.crt; \
        update-ca-certificates; \
        keytool -importcert -noprompt \
          -alias maven-extra-ca \
          -file /usr/local/share/ca-certificates/maven-extra-ca.crt \
          -keystore "$JAVA_HOME/lib/security/cacerts" \
          -storepass changeit; \
      fi \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace/backend
COPY backend/pom.xml ./
RUN sh -lc 'mvn -B $(cat /tmp/maven-ssl-flags) -DskipTests dependency:go-offline'

COPY backend/src ./src
RUN sh -lc 'mvn -B $(cat /tmp/maven-ssl-flags) -DskipTests package'

FROM eclipse-temurin:21-jre-noble AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        tesseract-ocr \
        tesseract-ocr-eng \
        tesseract-ocr-kaz \
        tesseract-ocr-rus \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system app \
    && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app \
    && mkdir -p /app/storage \
    && chown -R app:app /app

WORKDIR /app
COPY --from=build --chown=app:app /workspace/backend/target/backend-0.0.1-SNAPSHOT.jar /app/app.jar

ENV JAVA_TOOL_OPTIONS="-Djava.awt.headless=true"
ENV APP_STORAGE_DIR="/app/storage"

EXPOSE 8080
VOLUME ["/app/storage"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://127.0.0.1:8080/api/liveness >/dev/null || exit 1

USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

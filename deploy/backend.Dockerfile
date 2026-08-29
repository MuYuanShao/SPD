FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
COPY backend/.mvn backend/.mvn
COPY backend/mvnw backend/mvnw
RUN chmod +x backend/mvnw \
    && backend/mvnw -f backend/pom.xml dependency:go-offline

COPY backend/src backend/src
RUN backend/mvnw -f backend/pom.xml package

FROM eclipse-temurin:17-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/backend/target/app.jar app.jar

EXPOSE 1818
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM eclipse-temurin:25-jdk-alpine AS build
RUN apk add --no-cache curl unzip
WORKDIR /build
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN sh mvnw -B dependency:go-offline -Dspotless.skip=true
COPY src src
RUN sh mvnw -B package -DskipTests -Dspotless.skip=true

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S vendafacil && adduser -S vendafacil -G vendafacil
WORKDIR /app
COPY --from=build --chown=vendafacil:vendafacil /build/target/vendafacil-0.2.0-SNAPSHOT.jar app.jar
USER vendafacil
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

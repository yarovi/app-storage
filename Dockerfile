# Etapa de build
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Imagen final mínima
FROM gcr.io/distroless/java17
COPY --from=builder /app/target/app-Storage-0.0.1-SNAPSHOT.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]

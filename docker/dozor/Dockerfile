FROM gradle:8.9-jdk17 AS build
WORKDIR /workspace
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src
COPY config ./config
RUN gradle installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/build/install/dozor /app/dozor
COPY docker/dozor/dozor.compose.yaml /app/config/dozor.yaml
COPY docker/dozor/dozor.demo.yaml /app/config/dozor.demo.yaml
ENV DOZOR_CONFIG=/app/config/dozor.demo.yaml
EXPOSE 8080
CMD ["/app/dozor/bin/dozor"]

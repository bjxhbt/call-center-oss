FROM eclipse-temurin:17-jdk-jammy AS tools
WORKDIR /build
COPY tools/ ./tools/
COPY app/och-api.jar ./application.jar
RUN mkdir classes unpack && cd unpack && jar -xf ../application.jar \
    && cd .. && javac -encoding UTF-8 -cp 'tools/*:unpack/BOOT-INF/classes:unpack/BOOT-INF/lib/*' -d classes tools/*.java

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN mkdir -p app config tools database logs runtime/public/files runtime/public/recordings \
    && chown -R 10001:10001 /app
COPY --chown=10001:10001 app/och-api.jar ./app/och-api.jar
COPY --chown=10001:10001 config/application.yml ./config/application.yml
COPY --chown=10001:10001 database/*.sql ./database/
COPY --chown=10001:10001 tools/*.jar ./tools/
COPY --from=tools --chown=10001:10001 /build/classes/ ./tools/
USER 10001:10001
EXPOSE 14320
ENTRYPOINT ["java", "-Xms256m", "-Xmx1024m", "-jar", "/app/app/och-api.jar", "--spring.config.location=file:/app/config/application.yml"]

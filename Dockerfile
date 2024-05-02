

# 1st Docker build stage: build the project with Maven
FROM maven:3.6.3-openjdk-17 as builder
WORKDIR /project
COPY . /project/

ENV APP_BIND_ADDRESS 0.0.0.0
ENV APP_BIND_PORT 3000
#ENV LOGI_BACKEND_HOST 192.168.57.35
ENV LOGI_BACKEND_HOST atarcdb3.postgres.database.azure.com
ENV LOGI_BACKEND_PORT 5432
#ENV KEYCLOAK_BASE_URL https://keycloak.local:8443
ENV KEYCLOAK_BASE_URL http://auth.local
ENV KEYCLOAK_REALM vertx
ENV KEYCLOAK_CLIENT_ID vertx-service
ENV KEYCLOAK_CLIENT_SECRET ecb85cc5-f90d-4a03-8fac-24dcde57f40c

RUN mvn package -DskipTests -B

# 2nd Docker build stage: copy builder output and configure entry point
FROM openjdk:17-alpine
ENV APP_DIR /application
ENV APP_FILE container-uber-jar.jar

EXPOSE 3000

WORKDIR $APP_DIR
COPY --from=builder /project/target/*-fat.jar $APP_DIR/$APP_FILE

COPY --from=builder /project/server.jks $APP_DIR/server.jks

#COPY --from=builder /project/src/main/resources/app-conf.json $APP_DIR/app-conf.json

ENTRYPOINT ["sh", "-c"]
CMD ["exec java -jar $APP_FILE"]
#CMD ["exec java -jar $APP_FILE -conf app-conf.json"]

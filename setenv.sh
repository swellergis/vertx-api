#!/bin/bash

# export APP_BIND_ADDRESS=127.0.0.1
export APP_BIND_ADDRESS=0.0.0.0
export APP_BIND_PORT=3000

export LOGI_BACKEND_HOST=192.168.57.35
# export LOGI_BACKEND_HOST=atarcdb3.postgres.database.azure.com
export LOGI_BACKEND_PORT=5432

export KEYCLOAK_BASE_URL=https://auth.local
export KEYCLOAK_REALM=vertx
export KEYCLOAK_CLIENT_ID=vertx-service
export KEYCLOAK_CLIENT_SECRET=ecb85cc5-f90d-4a03-8fac-24dcde57f40c

mvn clean package
# java -jar target/vertx-api-1.0.0-fat.jar
#!/bin/sh
set -eu

JAVA_OPTS_VALUE="${JAVA_OPTS:-}"
DEFAULT_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom -Duser.timezone=UTC"
BASE_CMD="java ${DEFAULT_OPTS} ${JAVA_OPTS_VALUE}"

if [ "${APPLICATIONINSIGHTS_ENABLED:-true}" = "true" ] && [ -n "${APPLICATIONINSIGHTS_CONNECTION_STRING:-}" ]; then
  exec sh -c "${BASE_CMD} -javaagent:/opt/applicationinsights/applicationinsights-agent.jar -Dapplicationinsights.configuration.file=/opt/applicationinsights/applicationinsights.json -jar /app/app.jar"
fi

exec sh -c "${BASE_CMD} -jar /app/app.jar"

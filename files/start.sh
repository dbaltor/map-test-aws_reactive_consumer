#!/bin/sh
set -e
exec java -Djava.security.egd=file:/dev/./urandom -jar /app.jar both 100 2 /realtimelocation.csv
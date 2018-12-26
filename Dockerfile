FROM openjdk:8-jdk-alpine
VOLUME /tmp
ARG JAR_FILE
COPY ${JAR_FILE} app.jar
COPY *.csv /
COPY start.sh /usr/local/bin/
RUN ln -s usr/local/bin/start.sh / # backwards compat
ENTRYPOINT ["start.sh"]

EXPOSE 8080


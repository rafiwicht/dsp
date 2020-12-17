FROM flink:1.10.0-scala_2.12

USER root
COPY JAR-1.0.0.jar /opt
RUN mkdir /data
RUN chmod 777 /data

USER flink
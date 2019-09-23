FROM openjdk:11
COPY target/scala-2.12/queue-assembly-0.0.8.jar /
ENTRYPOINT ["java","-jar","queue-assembly-0.0.8.jar"]
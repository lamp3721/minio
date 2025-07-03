FROM openjdk:17-slim-bullseye
# 就是填写 jre的镜像文件
# arm： docker.1ms.run/library/openjdk:17-slim-bullseye

COPY *.jar /app.jar

EXPOSE 902

ENTRYPOINT ["java", "-jar", "/app.jar"]
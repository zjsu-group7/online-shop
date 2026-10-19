# syntax=docker/dockerfile:1
# Online Shop 在线购物系统 —— 运行镜像
#
# 说明：本镜像直接使用宿主机已构建好的可执行 jar（fat jar），
# 构建前请先执行： mvn clean package -DskipTests
# 产物位于 target/online-shop.jar（见 pom.xml 的 <finalName>）。

FROM eclipse-temurin:17-jre
WORKDIR /app

# 数据目录（H2 文件库）与图片上传目录
RUN mkdir -p /app/data /app/uploads

COPY target/online-shop.jar app.jar

EXPOSE 8080

# 声明持久化挂载点，避免容器重建后数据丢失
VOLUME ["/app/data", "/app/uploads"]

ENTRYPOINT ["java", "-jar", "app.jar"]

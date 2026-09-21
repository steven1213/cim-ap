# 构建阶段
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# 先拷贝 pom 利用缓存
COPY pom.xml .
COPY cim-core/pom.xml cim-core/
COPY cim-spring-support/pom.xml cim-spring-support/
COPY cim-bootstrap/pom.xml cim-bootstrap/
RUN mvn -q -DskipTests dependency:go-offline || true
# 拷贝源码并打包
COPY . .
RUN mvn -q -DskipTests clean package -pl cim-bootstrap -am

# 运行阶段
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/cim-bootstrap/target/cim-bootstrap-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# Bone 平台多阶段构建 Dockerfile
# 使用方式:
#   docker build -f docker/Dockerfile --build-arg MODULE=bone-iam -t bone/bone-iam:latest .

ARG JAVA_VERSION=17
ARG MAVEN_VERSION=3.9.6

# 阶段1: Maven 构建
FROM maven:${MAVEN_VERSION}-eclipse-temurin-${JAVA_VERSION} AS builder
ARG MODULE=bone-iam

WORKDIR /build
COPY pom.xml .
COPY bone-parent/pom.xml bone-parent/
COPY bone-framework/pom.xml bone-framework/
COPY bone-engine/pom.xml bone-engine/
COPY bone-platform/pom.xml bone-platform/
COPY bone-business/pom.xml bone-business/
COPY bone-sdk/pom.xml bone-sdk/
COPY bone-tool/pom.xml bone-tool/

# 下载依赖（利用缓存层）
RUN mvn dependency:go-offline -B -q || true

COPY . .
RUN mvn clean package -B -q -pl ${MODULE} -am -DskipTests=true

# 阶段2: 运行时镜像
FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine
ARG MODULE=bone-iam

# 安全: 使用非 root 用户运行
RUN addgroup -S bone && adduser -S bone -G bone

WORKDIR /app
COPY --from=builder /build/bone-platform/${MODULE}/target/${MODULE}-*.jar app.jar

RUN chown -R bone:bone /app
USER bone

# JVM 调优参数
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:InitialRAMPercentage=50.0 -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
ENV BONE_DB_URL=""
ENV BONE_DB_USERNAME=""
ENV BONE_DB_PASSWORD=""
ENV BONE_REDIS_HOST=""
ENV BONE_REDIS_PASSWORD=""

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]

FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# 빌드된 JAR 파일을 복사 (먼저 mvnw.cmd clean package 필요)
COPY target/sj-lab-openapi.jar /app/sj-lab-openapi.jar

# 활성 프로파일과 원천 주소(OPENAPI_UPSTREAM_BASE_URL)는 helm/ConfigMap 에서 주입
ENTRYPOINT ["java", "-jar", "/app/sj-lab-openapi.jar"]

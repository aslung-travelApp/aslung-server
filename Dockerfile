# 1단계: 빌드 환경 (Maven 이미지를 사용해서 빌드함)
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY . .

# 메이븐으로 빌드 실행 (테스트 생략)
RUN mvn clean package -DskipTests

# 2단계: 실행 환경 (가벼운 이미지)
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
# 메이븐은 결과물을 'target' 폴더에 만듭니다
COPY --from=builder /app/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
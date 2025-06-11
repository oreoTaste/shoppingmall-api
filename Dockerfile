# --- 1단계: 빌드 환경 ---
# Gradle과 JDK 17이 포함된 이미지를 'builder'라는 이름의 빌드 환경으로 사용합니다.
FROM gradle:8.5-jdk17-jammy AS builder

# 작업 디렉터리를 생성합니다.
WORKDIR /app

# build.gradle과 settings.gradle 등 빌드에 필요한 파일을 먼저 복사합니다.
# (소스코드보다 먼저 복사하면, 의존성이 변경되지 않았을 경우 캐시를 활용해 빌드 속도가 빨라집니다)
COPY build.gradle settings.gradle /app/

# 의존성을 다운로드합니다.
RUN gradle dependencies

# 나머지 전체 소스 코드를 복사합니다.
COPY . /app/

# Gradle 빌드를 실행하여 JAR 파일을 생성합니다. 테스트는 건너뜁니다.
RUN gradle build -x test


# --- 2단계: 실행 환경 ---
# 실제 애플리케이션을 실행할 환경입니다. JRE만 포함된 가벼운 이미지를 사용합니다.
FROM eclipse-temurin:17-jre-jammy

# 작업 디렉터리를 생성합니다.
WORKDIR /app

# 빌드 환경(builder)의 build/libs 폴더에서 생성된 JAR 파일을 복사해옵니다.
COPY --from=builder /app/build/libs/*.jar app.jar

# [추가] 컨테이너 내부에 파일이 저장될 디렉토리를 생성합니다.
# 이 디렉토리가 볼륨과 매핑됩니다.
RUN mkdir -p /app/uploads

# 8080 포트를 외부에 노출하도록 설정합니다.
EXPOSE 8080

# 컨테이너가 시작될 때 이 명령어를 실행하여 애플리케이션을 구동합니다.
ENTRYPOINT ["java", "-jar", "app.jar"]
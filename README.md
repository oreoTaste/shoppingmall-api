# 🛒 Shopping Mall Product AI Inspection API

이 프로젝트는 쇼핑몰에 등록되는 상품의 **텍스트 정보(상품명, 공시사항)**와 **이미지 내의 텍스트**를 AI(Gemini, ChatGPT)로 분석하여 금칙어 포함 여부를 자동으로 검수하는 배치 시스템입니다.

---

## 🚀 주요 기능

### 1. AI 기반 상품 검수 (Multi-Model 지원)
* **Gemini & ChatGPT 연동:** Google Gemini(2.5-flash-lite 등)와 OpenAI GPT-4o 모델을 선택적으로 사용하여 검수를 수행합니다.
* **텍스트 + 이미지 통합 분석:** 상품명, 상세 정보뿐만 아니라 첨부된 이미지 내의 텍스트까지 OCR 및 시각 지능으로 분석하여 변형된 금칙어(유사 발음, 특수문자 삽입 등)를 탐지합니다.

### 2. 효율적인 배치 처리 시스템
* **S3 데이터 수집:** 매일 정해진 시간에 AWS S3에서 검수 대상 ZIP 파일(TSV 포함)을 자동으로 수집합니다.
* **병렬 실행:** `CompletableFuture`와 전용 스레드 풀을 사용하여 대량의 검수 요청을 병렬로 처리함으로써 성능을 극대화했습니다.
* **재시도 메커니즘:** AI API 호출 실패나 일시적인 오류 발생 시 설정된 횟수만큼 자동 재시도를 수행합니다.

### 3. 고급 이미지 전처리
* **이미지 분할(Splitting):** AI 모델이 인식하기 어려운 긴 상세 이미지를 기준 높이(1600px)로 자동 분할합니다.
* **최적화:** 흑백 변환 및 WebP 포맷 압축을 통해 API 전송 비용을 절감하고 처리 속도를 높였습니다.

### 4. 금칙어 관리 및 동기화
* **카테고리별 관리:** 상품의 카테고리(대/중/소/세분류)에 따라 적용될 금칙어를 유연하게 관리합니다.
* **자동 동기화:** 외부 시스템의 금칙어 목록을 배치 작업을 통해 DB에 실시간으로 반영합니다.

---

## 🛠 기술 스택

* **Framework:** Spring Boot 3.4.6
* **Language:** Java 17
* **Database:** PostgreSQL, MyBatis, Spring JDBC
* **AI SDK:** Google Gemini API, OpenAI API
* **Cloud:** AWS S3 (Storage)
* **Build Tool:** Gradle
* **DevOps:** Docker, Docker Compose

---

## ⚙️ 주요 설정 (`application.properties`)

애플리케이션 실행을 위해 다음 환경 변수 설정이 필요합니다.

```properties
# AI API Keys
gemini.api.key=YOUR_GEMINI_KEY
openai.api.key=YOUR_OPENAI_KEY

# AWS S3 Settings
cloud.aws.credentials.access-key=YOUR_ACCESS_KEY
cloud.aws.credentials.secret-key=YOUR_SECRET_KEY
cloud.aws.bucket.name=YOUR_BUCKET

# Batch Settings
batch.size-per-minute=300
batch.multi-thread-count=3
```

---

## 🏃 실행 방법

Docker 사용 시
```bash
# 이미지 빌드 및 컨테이너 실행
docker-compose up -d --build
```
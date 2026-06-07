# ChainWatch 구현 프로세스

## 1. 프로젝트 개요

### 프로젝트명
ChainWatch

### 한 줄 정의
AI 기반 온체인 이상거래 탐지 시스템

### 프로젝트 설명
ChainWatch는 블록체인 네트워크에서 발생하는 트랜잭션 데이터를 실시간으로 수집하고, 대규모 자금 이동, 고래 지갑 활동, 거래소 입출금 패턴 등을 분석하여 이상거래를 탐지하는 백엔드 중심 프로젝트입니다.

탐지된 이벤트는 AI가 자연어 리포트로 요약하고, 사용자는 대시보드와 알림을 통해 위험 신호를 확인할 수 있습니다.

## 2. 목표

- Ethereum 온체인 데이터를 실시간 또는 준실시간으로 수집한다.
- 룰 기반 이상거래 탐지 엔진을 구축한다.
- 탐지 이벤트를 저장하고 조회 가능한 API를 제공한다.
- AI 기반 자연어 분석 리포트를 생성한다.
- React 대시보드와 Slack 또는 Discord 알림으로 운영 가시성을 확보한다.

## 3. 기술 스택

### Backend
- Java 21
- Spring Boot 3
- Spring Security
- Spring Data JPA
- QueryDSL
- WebFlux

### Blockchain
- Ethereum JSON-RPC
- Web3j
- Alchemy 또는 Infura
- Etherscan API

### Data / Infra
- PostgreSQL
- Redis
- Kafka
- Docker
- Docker Compose

### AI / Analysis
- Python
- FastAPI
- Pandas
- Claude API
- Gemini API
- LM Studio
- Hermes

### DevOps
- GitHub Actions
- AWS EC2
- Nginx
- Prometheus
- Grafana

### Frontend
- React
- TypeScript

## 4. 핵심 기능

### 4.1 온체인 트랜잭션 수집
Ethereum RPC 또는 Etherscan API를 통해 블록과 트랜잭션 데이터를 수집한다.

```text
Ethereum Network
  ↓
RPC API
  ↓
ChainWatch Collector
  ↓
Kafka
```

수집 대상 데이터:

- 트랜잭션 해시
- 송신 지갑 주소
- 수신 지갑 주소
- 전송 금액
- 가스비
- 블록 번호
- 발생 시간
- 컨트랙트 주소

### 4.2 이상 거래 탐지
- 고액 이체 탐지
- 단시간 반복 거래 탐지
- 특정 지갑의 비정상 활성도 탐지
- 거래소 지갑 입출금 패턴 탐지
- 블랙리스트 또는 관심 지갑 연관 탐지

### 4.3 Kafka 기반 비동기 처리
- 수집 서버와 탐지 서버를 분리한다.
- 수집, 탐지, 리포트 생성, 알림 발송을 이벤트 기반으로 연결한다.

### 4.4 AI 분석 리포트 생성
- 탐지 이벤트를 AI 서버로 전달한다.
- Claude, Gemini, LM Studio, Hermes 중 하나를 통해 요약 및 위험 해설을 생성한다.

### 4.5 알림 기능
- 위험도 높은 이벤트 발생 시 Slack 또는 Discord로 알림을 전송한다.

### 4.6 대시보드
- 이상거래 목록 조회
- 이벤트 상세 조회
- 위험도별 필터링
- 지갑 주소 및 기간 검색
- AI 분석 리포트 확인

## 5. 전체 아키텍처

```text
Ethereum / Etherscan API
        ↓
ChainWatch Collector Server
        ↓
Kafka
        ↓
Detection Server
        ↓
PostgreSQL / Redis
        ↓
AI Analysis Server
        ↓
API Server
        ↓
React Dashboard
        ↓
Slack / Discord Alert
```

## 6. 서비스 구성 제안

### 6.1 Collector Server
역할:

- Ethereum 블록 및 트랜잭션 수집
- 원본 트랜잭션 정규화
- Kafka 토픽 발행

주요 기술:

- Spring Boot
- WebFlux
- Web3j
- Kafka Producer

### 6.2 Detection Server
역할:

- Kafka에서 트랜잭션 소비
- 룰 기반 이상거래 탐지
- 이벤트 저장
- Redis 캐시 활용

주요 기술:

- Spring Boot
- Kafka Consumer
- JPA
- QueryDSL
- PostgreSQL
- Redis

### 6.3 AI Analysis Server
역할:

- 탐지 이벤트 요약
- 자연어 분석 리포트 생성
- 모델별 응답 포맷 표준화

주요 기술:

- Python
- FastAPI
- Pandas
- LLM API 연동

### 6.4 API Server
역할:

- 대시보드용 REST API 제공
- 이벤트 조회 및 필터링
- 인증 및 권한 처리

주요 기술:

- Spring Boot
- Spring Security
- JPA / QueryDSL

### 6.5 Frontend Dashboard
역할:

- 이벤트 모니터링 화면 제공
- 검색 및 필터 기능 제공
- AI 분석 결과 시각화

주요 기술:

- React
- TypeScript

## 7. 구현 단계

### 1단계. 프로젝트 초기 세팅
목표:

- 멀티모듈 또는 서비스 분리 구조 설계
- 공통 개발 환경 구성
- Docker Compose 기반 로컬 인프라 구성

작업 항목:

- GitHub 저장소 구조 정의
- `backend`, `ai`, `frontend`, `infra` 디렉토리 구성
- PostgreSQL, Redis, Kafka, Zookeeper 또는 KRaft 기반 Kafka 설정
- Spring Boot 기본 프로젝트 생성
- FastAPI 기본 프로젝트 생성
- React + TypeScript 프로젝트 생성

산출물:

- 실행 가능한 로컬 개발환경
- 초기 디렉토리 구조
- 기본 Docker Compose 파일

### 2단계. 온체인 수집기 구현
목표:

- Ethereum 블록 및 트랜잭션 데이터를 안정적으로 수집한다.

작업 항목:

- Web3j 또는 RPC 클라이언트 설정
- 최신 블록 폴링 또는 websocket 구독 구현
- 트랜잭션 DTO 정규화
- Kafka `raw-transactions` 토픽 발행
- 장애 복구를 위한 마지막 처리 블록 번호 관리

산출물:

- 실시간 트랜잭션 수집 파이프라인
- Kafka 적재 확인

### 3단계. 이상거래 탐지 엔진 구현
목표:

- 초기 버전의 룰 기반 탐지 기능을 완성한다.

작업 항목:

- Kafka Consumer 구현
- 이상거래 탐지 규칙 정의
- 위험도 스코어링 기준 설계
- 탐지 이벤트를 PostgreSQL에 저장
- 빠른 조회를 위한 Redis 캐시 적용

예시 규칙:

- 일정 금액 이상 전송 시 고액 이체로 분류
- 짧은 시간 동안 다수 주소로 분산 전송 시 의심 패턴으로 분류
- 거래소 지갑과 대형 자금 이동이 동시에 발생하면 위험도 상향

산출물:

- 탐지 엔진 1차 버전
- 이벤트 저장 및 조회 가능 상태

### 4단계. AI 리포트 서버 구현
목표:

- 탐지 이벤트를 사람이 읽을 수 있는 자연어 리포트로 변환한다.

작업 항목:

- Detection Server와 AI Server 간 API 또는 Kafka 연동
- 프롬프트 템플릿 설계
- 모델별 어댑터 인터페이스 정의
- 응답 파싱 및 저장
- 실패 시 재시도 또는 대체 모델 처리

산출물:

- AI 요약 리포트 생성 API
- 이벤트별 분석 문서 저장 기능

### 5단계. API 서버 및 대시보드 구현
목표:

- 사용자에게 탐지 결과를 제공하는 조회 시스템을 구축한다.

작업 항목:

- 이벤트 목록 조회 API
- 이벤트 상세 조회 API
- 지갑 주소, 위험도, 기간 필터 API
- React 대시보드 페이지 구현
- 차트 및 테이블 기반 시각화

산출물:

- 운영자용 대시보드 MVP
- 기본 검색 및 필터 기능

### 6단계. 알림 및 운영 기능 구현
목표:

- 운영자가 고위험 이벤트를 실시간으로 인지할 수 있도록 한다.

작업 항목:

- Slack 또는 Discord Webhook 연동
- 위험도 기준 알림 정책 설계
- 중복 알림 방지 로직 구현
- 알림 이력 저장

산출물:

- 실시간 경고 알림 시스템

### 7단계. 배포 및 모니터링 구성
목표:

- 운영 가능한 배포 환경과 관측 체계를 마련한다.

작업 항목:

- GitHub Actions CI/CD 구성
- Docker 이미지 빌드 자동화
- AWS EC2 배포
- Nginx 리버스 프록시 설정
- Prometheus 메트릭 수집
- Grafana 대시보드 구성

산출물:

- 배포 자동화 파이프라인
- 시스템 모니터링 환경

## 8. 권장 디렉토리 구조

```text
ChainWatch/
├── backend/
│   ├── collector-server/
│   ├── detection-server/
│   ├── api-server/
│   └── common/
├── ai/
│   └── analysis-server/
├── frontend/
│   └── dashboard/
├── infra/
│   ├── docker/
│   ├── nginx/
│   └── monitoring/
├── docs/
└── docker-compose.yml
```

## 9. 데이터 모델 초안

### Transaction
- id
- txHash
- fromAddress
- toAddress
- amount
- gasFee
- blockNumber
- timestamp
- contractAddress
- network

### DetectionEvent
- id
- eventType
- riskScore
- summary
- txHash
- walletAddress
- detectedAt
- status

### AiReport
- id
- detectionEventId
- modelName
- promptVersion
- reportText
- createdAt

## 10. Kafka 토픽 설계 예시

- `raw-transactions`
- `detected-events`
- `ai-report-requests`
- `alert-events`

## 11. API 설계 예시

### 이벤트 조회
- `GET /api/events`
- `GET /api/events/{id}`

### 리포트 조회
- `GET /api/reports/{eventId}`

### 검색 / 필터
- `GET /api/events?wallet=0x...&risk=HIGH&from=2026-06-01&to=2026-06-07`

## 12. 우선순위 제안

### MVP 범위
- Ethereum 메인넷 또는 테스트넷 수집
- 고액 이체 중심 룰 기반 탐지
- 이벤트 저장
- AI 요약 리포트 생성
- 이벤트 목록 대시보드
- Slack 또는 Discord 알림

### 추후 확장
- 다중 체인 지원
- 머신러닝 기반 이상탐지 모델
- 지갑 관계 그래프 분석
- 거래소 주소 자동 식별
- 사용자별 알림 정책 커스터마이징

## 13. 구현 순서 요약

1. 개발 환경과 인프라를 먼저 구성한다.
2. Collector Server로 온체인 수집 파이프라인을 만든다.
3. Detection Server에서 룰 기반 탐지를 구현한다.
4. PostgreSQL과 Redis로 저장 및 조회 성능을 확보한다.
5. AI Analysis Server로 자연어 리포트를 생성한다.
6. API Server와 React Dashboard로 사용자 접근 경로를 만든다.
7. Slack 또는 Discord 알림을 연결한다.
8. GitHub Actions, AWS, Prometheus, Grafana로 운영 환경을 완성한다.

## 14. 권장 첫 스프린트

### Sprint 1 목표
- 로컬 인프라 실행
- Collector Server 기본 구현
- Kafka 적재 확인
- Detection Server에서 단일 룰 탐지 구현
- PostgreSQL 저장까지 연결

### Sprint 1 완료 기준
- 특정 블록의 트랜잭션을 수집할 수 있다.
- Kafka 토픽으로 메시지를 발행할 수 있다.
- 탐지 규칙 1개 이상이 동작한다.
- 탐지 이벤트가 DB에 저장된다.

## 15. 결론

ChainWatch는 수집, 탐지, AI 분석, 알림, 시각화가 분리된 이벤트 기반 아키텍처로 설계하는 것이 적합합니다.

초기에는 룰 기반 탐지와 운영 중심 대시보드에 집중하고, 이후 AI 리포트 고도화와 다중 체인 확장으로 발전시키는 방식이 가장 현실적인 구현 전략입니다.

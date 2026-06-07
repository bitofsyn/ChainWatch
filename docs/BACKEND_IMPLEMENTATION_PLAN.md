# ChainWatch Backend 구현 계획

## 문서 사용 규칙

- 완료된 항목은 `- [x] ~~항목명~~` 형식으로 표시한다.
- 진행 전 항목은 `- [ ] 항목명` 형식으로 유지한다.
- 작업 시작 전에 이 문서의 해당 섹션을 먼저 확인한다.
- 작업 완료 후 코드 변경과 함께 이 문서도 같이 업데이트한다.

예시:

```md
- [x] ~~Spring Boot 초기 세팅~~
- [ ] Kafka Producer 구현
```

## 구현 목표

ChainWatch 백엔드는 아래 흐름을 우선 완성하는 것을 목표로 한다.

`Ethereum RPC 수집 -> Transaction 저장 -> 이상거래 탐지 -> DetectionEvent 저장 -> API 조회 -> AI 분석 연계 -> 알림 발송`

## 현재 구현 상태

- [x] ~~Spring Boot 3 / Java 21 백엔드 초기 골격 구성~~
- [x] ~~기본 보안 설정 및 헬스체크 API 구성~~
- [x] ~~Transaction / DetectionEvent 도메인 모델 구성~~
- [x] ~~이상거래 이벤트 조회 API 초안 구성~~
- [x] ~~Ethereum RPC 기반 Collector 1차 골격 구성~~

## 1. 프로젝트 기초 구성

- [x] ~~Spring Boot 프로젝트 초기화~~
- [x] ~~Gradle Wrapper 및 빌드 환경 정리~~
- [x] ~~기본 패키지 구조 정리~~
- [x] ~~애플리케이션 실행 및 테스트 검증~~
- [ ] 환경별 설정 분리 (`local`, `dev`, `prod`)
- [x] ~~공통 예외 응답 포맷 정의~~
- [ ] API 응답 공통 래퍼 적용 여부 결정

## 2. 온체인 수집 영역

- [x] ~~Ethereum RPC 설정 프로퍼티 추가~~
- [x] ~~Web3j 빈 구성~~
- [x] ~~수동 블록 수집 API 초안 구현~~
- [x] ~~블록 트랜잭션 -> Transaction 엔티티 변환 로직 구현~~
- [x] ~~Collector 예외 처리 개선~~
- [x] ~~마지막 수집 블록 번호 저장 전략 설계~~
- [x] ~~최신 블록 자동 수집 스케줄러 구현~~
- [ ] 중복 수집 방지 정책 고도화
- [x] ~~Etherscan API 대체 경로 설계~~
- [ ] Kafka 발행용 수집 이벤트 모델 정의
- [ ] Kafka Producer 연동

## 3. 거래 데이터 저장 및 조회 영역

- [x] ~~Transaction 엔티티 구성~~
- [x] ~~TransactionRepository 구성~~
- [ ] Transaction 조회 API 구현
- [ ] 지갑 주소 기준 검색 API 구현
- [ ] 블록 번호 / 기간 기준 검색 API 구현
- [ ] 페이징 및 정렬 기준 적용
- [ ] PostgreSQL 기준 스키마 점검
- [ ] 인덱스 전략 설계 (`tx_hash`, `from_address`, `to_address`, `block_number`, `timestamp`)

## 4. 이상거래 탐지 영역

- [x] ~~DetectionEvent 엔티티 구성~~
- [x] ~~DetectionEventRepository 구성~~
- [x] ~~DetectionEvent 목록 조회 API 초안 구현~~
- [x] ~~탐지 규칙 인터페이스 설계~~
- [x] ~~고액 이체 탐지 규칙 구현~~
- [x] ~~고래 지갑 활동 탐지 규칙 구현~~
- [x] ~~짧은 시간 반복 이체 탐지 규칙 구현~~
- [x] ~~거래소 입출금 패턴 탐지 규칙 구현~~
- [ ] 위험도 스코어링 기준 정의
- [x] ~~Detection 서비스 구현~~
- [x] ~~Transaction 저장 후 Detection 연결 방식 결정~~
- [ ] 탐지 결과 저장 및 중복 이벤트 정책 정의

## 5. AI 분석 연계 영역

- [ ] AI 분석 요청 모델 정의
- [ ] DetectionEvent -> AI 분석 프롬프트 변환 구조 설계
- [ ] Python FastAPI 분석 서버 인터페이스 정의
- [ ] Claude / Gemini / LM Studio / Hermes 어댑터 전략 결정
- [ ] AI 분석 결과 저장 모델 추가
- [ ] 이벤트 상세 API에 AI 리포트 연계

## 6. 알림 영역

- [ ] Slack Webhook 연동
- [ ] Discord Webhook 연동
- [ ] 위험도 기준 알림 발송 정책 정의
- [ ] 중복 알림 방지 로직 구현
- [ ] 알림 발송 이력 저장 여부 결정

## 7. 운영 및 인프라 영역

- [ ] Docker Compose 작성
- [ ] PostgreSQL 컨테이너 구성
- [ ] Redis 컨테이너 구성
- [ ] Kafka 컨테이너 구성
- [ ] 로컬 개발 실행 문서 정리
- [ ] GitHub Actions 백엔드 CI 구성
- [ ] Actuator 메트릭 확장
- [ ] Prometheus / Grafana 연계 구조 초안 작성

## 8. 권장 구현 순서

### Step 1. Collector 안정화

- [x] ~~Collector 예외 처리~~
- [x] ~~자동 수집 스케줄러~~
- [ ] Kafka 발행 구조

### Step 2. Detection MVP 완성

- [x] ~~탐지 규칙 인터페이스~~
- [x] ~~고액 이체 규칙~~
- [x] ~~DetectionEvent 자동 생성~~

### Step 3. 조회 API 확장

- [ ] Transaction 조회 API
- [ ] Event 필터 API
- [ ] 상세 조회 API

### Step 4. 인프라 전환

- [ ] H2 -> PostgreSQL 전환
- [ ] Redis / Kafka 로컬 구성

### Step 5. AI / 알림 연계

- [ ] AI 분석 요청 플로우
- [ ] Slack / Discord 알림

## 바로 다음 작업

- [x] ~~Collector 예외 처리 개선~~
- [x] ~~마지막 수집 블록 추적 구조 추가~~
- [x] ~~최신 블록 자동 수집 스케줄러 구현~~
- [ ] Kafka 발행 구조 설계 시작
- [x] ~~Etherscan 실호출 검증 및 응답 매핑 보강~~
- [x] ~~거래소 / 고래 / 반복 이체 규칙 추가~~

## 메모

- 현재는 개발 속도를 위해 H2 기반으로 동작한다.
- Collector는 `chainwatch.ethereum.rpc-url` 설정 시에만 활성화된다.
- Etherscan 사용 시 `ETHERSCAN_API_KEY` 환경변수가 필요하다.
- 다음 저장소 변경 시 이 문서도 함께 업데이트한다.

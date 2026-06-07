# ChainWatch 로컬 인프라 실행 가이드

## 구성

- PostgreSQL
- Redis
- Kafka (KRaft)
- Kafka UI

## 실행

루트 디렉토리에서 실행:

```bash
docker compose up -d
```

정상 확인:

```bash
docker compose ps
```

Kafka UI:

- `http://localhost:8081`

## 백엔드 로컬 실행

환경변수 설정:

```bash
export ETHERSCAN_API_KEY=your_key
```

백엔드 실행:

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'
```

헬스 체크:

```bash
curl http://localhost:18080/api/health
```

수동 최신 블록 수집:

```bash
curl -X POST http://localhost:18080/api/collector/blocks/latest
```

## Kafka 토픽

백엔드가 자동 생성 또는 사용하는 토픽:

- `chainwatch.collected-transactions`
- `chainwatch.detected-events`

## 로컬 포트

- PostgreSQL: `55432`
- Redis: `6379`
- Kafka external: `9094`
- Kafka UI: `8081`
- Backend: `18080`

## 비고

- 기본 `application.yml`은 H2 기반 개발용 설정이다.
- `local` 프로필을 활성화하면 PostgreSQL, Redis, Kafka를 사용한다.
- 호스트 머신에 로컬 PostgreSQL이 이미 떠 있는 경우를 피하기 위해 Docker PostgreSQL은 `55432` 포트를 사용한다.
- Kafka broker 내부 주소는 `kafka:9092`, 호스트 머신에서는 `localhost:9094`를 사용한다.
- Kafka 토픽은 애플리케이션 시작 시 자동 생성되며, 수집 데이터는 `chainwatch.collected-transactions`, 탐지 이벤트는 `chainwatch.detected-events`로 발행된다.

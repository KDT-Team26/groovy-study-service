# groovy-study-service

## 1. Repo: groovy-study-service

**Groovy**는 태그 기반으로 스터디 그룹을 매칭하고, 참여 신청/승인, 캘린더 일정 관리, 회고록 공유,
실시간 알림까지 지원하는 스터디 커뮤니티 플랫폼입니다. 

`groovy-study-service`는 그중 **스터디** 도메인을 담당하는 서비스입니다. 스터디 그룹 생성·수정·
삭제, 참여 신청/승인/거절, 정원 초과 시 대기열, 태그 기반 매칭, 레벨/경험치 시스템을 담당하며,
`calendar-service`와 `content-service`가 스터디 상세/멤버십 확인을 위해 동기 호출하는 대상이기도
합니다.

## 2. 주요 기능

- 스터디 그룹 CRUD, 태그 기반 매칭 조회
- 참여 신청 / 신청 취소 / 승인·거절 / 탈퇴
- 정원 초과 시 대기열 등록/취소, 자리가 나면 대기열 순번대로 알림
- 스터디 레벨/경험치 시스템(다른 서비스가 호출하는 내부 적립 API 포함)
- 마이페이지 위임 엔드포인트(내가 만든 스터디, 내 신청 내역) — Study 데이터를 반환하므로 이 서비스가 소유

## 3. 시스템 아키텍처

```
 브라우저 ──▶ api-gateway ──▶ study-service(:8082)
                                   │  ┌─ JPA/Flyway ─▶ MySQL: study_db
                                   │  ├─ 동기 HTTP ──▶ identity-service(:8081)  이름 조회/JWKS/선호 태그
                                   │  └─ Kafka 발행 ─▶ notification-events 토픽 (Outbox)
                                   ▲
                calendar-service, content-service가 스터디 상세/멤버십 조회를 위해 호출
```

### DB / 계정 / 테이블

| 항목 | 값 |
| --- | --- |
| DB(스키마)명 | `study_db` |
| 전용 계정 | `study_service` (다른 서비스 DB 접근 불가) |
| 마이그레이션 | Flyway, 이 서비스가 자체 이력 소유 |

| 테이블 | 역할 | 비고 |
| --- | --- | --- |
| `studies` | 스터디 | `capacity`, `level`, `exp_point`, `leader_id`(→ identity_db.users.id, FK 없이 값 참조) |
| `applications` | 참여 신청 | `study_id` FK, `applicant_id`(→ identity_db.users.id), `status`(PENDING/APPROVED/REJECTED) |
| `study_meeting_days` | 스터디 모임 요일 | `study_id` FK, 이 DB 안에서 완결 |
| `study_tags` | 스터디-태그 매핑 | `study_id`/`tag_id` FK, 이 DB 안에서 완결 |
| `tags` | 태그 **로컬 사본** | `identity_db.tags`가 정본, `study_tags`의 FK 무결성 목적으로 이 스키마에도 별도 유지. **실시간 동기화 없음(수동 시드 필요)** |
| `study_waitlists` | 대기열 | `study_id` FK, `user_id`(→ identity_db.users.id) |
| `outbox_events` | Transactional Outbox | 알림 이벤트를 트랜잭션 내에서 기록 후 스케줄러가 Kafka로 발행 |

## 4. 기술 스택

- Java 21, Spring Boot 4.1.0, Gradle 멀티모듈
- Spring Security + Spring Data JPA + Bean Validation, Flyway
- `jjwt` — JWT는 **검증만** 수행(identity-service가 발급자), `libs:security-common`의 `JwksKeyLocator`가 공개키를 가져와 캐시
- Kafka(`spring-boot-starter-kafka`) — Transactional Outbox 패턴으로 알림 이벤트 발행
- Resilience4j(circuitbreaker + retry) — identity-service 호출(JWKS, 이름 조회, 선호 태그)에 적용
- Micrometer + OTel → Tempo, `/actuator/prometheus`
- 공용 라이브러리 5종 전부 의존(5개 서비스 중 처음으로 전부 사용): `event-contract`, `observability`, `web-common`, `security-common`, `client-common`(`ResilientCallExecutor`, `UserServiceClient` — 이 서비스에서 처음 등장)

## 5. 다른 MSA 서비스와의 네트워크 호출 관계

| 방향 | 상대 | 엔드포인트 | 용도 |
| --- | --- | --- | --- |
| 나가는 호출 | identity-service | `GET /.well-known/jwks.json` | JWT 서명 검증 |
| 나가는 호출 | identity-service | `GET /api/users/names?ids=...` | 방장/신청자 이름 배치 조회 |
| 나가는 호출 | identity-service | `GET /api/tags/me` | 태그 매칭 조회 시 로그인 유저 선호 태그 대체값 |
| 나가는 발행 | Kafka `notification-events` | Outbox → `NotificationPayload` | 신청/승인/대기열 등 알림을 notification-service가 소비 |
| 들어오는 호출 | calendar-service, content-service | `GET /api/studies/{id}`, `GET /api/studies/summary`, `GET /api/studies/{id}/members`, `GET /api/users/me/studies`, `GET /api/users/me/applications` | 스터디 상세/멤버십/요약 정보 |
| 들어오는 호출 | content-service | `POST /api/studies/{id}/exp` | 회고록/댓글 작성 시 경험치 적립 |
| api-gateway 라우팅 | — | `/api/studies/**`, `/api/users/me/studies`, `/api/users/me/applications` | 외부 요청 진입점 |

## 6. 로컬 실행 방법

컨테이너 빌드(빌드 컨텍스트는 레포 루트):

```bash
# MySQL(study_db)·Kafka·identity-service가 떠 있는 상태에서
docker build -t groovy-study-service .
docker run -p 8082:8082 \
  -e SPRING_DEV_DB_URL="jdbc:mysql://host.docker.internal:3306/study_db?..." \
  -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 \
  -e JWT_JWKS_URL=http://host.docker.internal:8081/.well-known/jwks.json \
  -e IDENTITY_SERVICE_URL=http://host.docker.internal:8081 \
  groovy-study-service
```

## 7. 모니터링 스택에서 관측되는 부분

- **Prometheus**: `job=study-service`로 `:8082/actuator/prometheus` 15초 스크래핑. HikariCP 커넥션 풀 지표가 서비스 단위로 분리 수집됩니다.
- **Alertmanager**: HikariCP pending 발생, JVM 힙 40% 초과, CPU 95% 초과 알림 규칙 적용.
- **Grafana**: JVM(Micrometer)·Loki 로그 대시보드 프로비저닝(Kafka Outbox 발행 지연 등을 보는 전용 패널은 없음 — 알려진 한계).
- **Tempo**: api-gateway에서 시작된 트레이스가 이 서비스를 거쳐 identity-service 호출까지 이어붙습니다.
- **계약 테스트**: `TagPreferenceClientContractTest`(identity-service `/api/tags/me` 응답 검증), `libs:client-common`의 `UserServiceClientContractTest`가 CI에 포함되어 있습니다. 다만 이 서비스가 **발행하는** `NotificationPayload` 쪽 계약 테스트는 원본 모놀리스 때부터 없었고, 이번 이관에서도 새로 만들지 않고 TODO로 남겨져 있습니다.

-- MSA 전환(study-service 추출, Phase 7 DB per Service 패턴 재사용): groovy(레거시)의
-- studies/applications/study_meeting_days/study_tags/tags/study_waitlists/outbox_events를
-- study_db 스키마로 옮겨온다. leader_id/applicant_id/user_id는 identity-service 소속(User
-- Aggregate)이라 이 스키마에 FK를 걸지 않는다(notification_db의 recipient_user_id와 동일한
-- 이유). tags는 아직 소유권이 최종 확정되지 않은 Shared Kernel이라 groovy_db에도 같은 구조의
-- 사본이 남아있다(알려진 한계, docs/Groovy_MSA_도메인경계_재검토.md 참고).

CREATE TABLE `tags` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` enum('OPERATING_POLICY','STUDY_MODE') NOT NULL,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKt48xdq560gs3gap9g7jg36kgc` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `studies` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `capacity` int NOT NULL,
  `description` text NOT NULL,
  `meeting_end_time` time DEFAULT NULL,
  `meeting_start_time` time DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  `leader_id` bigint NOT NULL,
  `level` int NOT NULL DEFAULT 1,
  `exp_point` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_studies_leader_id` (`leader_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `applications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `status` enum('APPROVED','PENDING','REJECTED') NOT NULL,
  `applicant_id` bigint NOT NULL,
  `study_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_application_study_applicant` (`study_id`,`applicant_id`),
  KEY `idx_applications_applicant_id` (`applicant_id`),
  CONSTRAINT `fk_applications_study` FOREIGN KEY (`study_id`) REFERENCES `studies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `study_meeting_days` (
  `study_id` bigint NOT NULL,
  `day_of_week` enum('FRI','MON','SAT','SUN','THU','TUE','WED') NOT NULL,
  PRIMARY KEY (`study_id`,`day_of_week`),
  CONSTRAINT `fk_study_meeting_days_study` FOREIGN KEY (`study_id`) REFERENCES `studies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `study_tags` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `study_id` bigint NOT NULL,
  `tag_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_study_tag` (`study_id`,`tag_id`),
  KEY `fk_study_tags_tag` (`tag_id`),
  CONSTRAINT `fk_study_tags_tag` FOREIGN KEY (`tag_id`) REFERENCES `tags` (`id`),
  CONSTRAINT `fk_study_tags_study` FOREIGN KEY (`study_id`) REFERENCES `studies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `study_waitlists` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `study_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_study_waitlist_study_user` (`study_id`, `user_id`),
  CONSTRAINT `fk_study_waitlists_study` FOREIGN KEY (`study_id`) REFERENCES `studies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `outbox_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `event_id` varchar(36) NOT NULL,
  `event_type` varchar(50) NOT NULL,
  `payload` TEXT NOT NULL,
  `published` boolean NOT NULL DEFAULT false,
  `published_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outbox_event_id` (`event_id`),
  KEY `idx_outbox_unpublished` (`published`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

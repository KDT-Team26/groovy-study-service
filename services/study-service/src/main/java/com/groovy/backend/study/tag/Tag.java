package com.groovy.backend.study.tag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * MSA 전환(study-service 추출): Tag 마스터 데이터는 아직 소유권이 최종 확정되지 않은 Shared
 * Kernel이다(docs/Groovy_MSA_도메인경계_재검토.md "Tag" 절 참고). StudyTag가 Study 소유
 * 데이터로 이 서비스로 옮겨오면서, 매칭 쿼리(JPQL Study-StudyTag 조인)가 같은 스키마 안에서
 * 성립해야 하므로 Tag 마스터 테이블도 이 서비스에 읽기전용 사본으로 함께 둔다. groovy(레거시)도
 * UserTag(선호 태그)를 위해 자신만의 사본을 그대로 갖고 있다 — 두 스키마에 같은 시드 데이터가
 * 중복된다(알려진 한계, 둘 다 같은 Flyway 시드로 관리).
 */
@Entity
@Table(name = "tags")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tag {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TagCategory category;

	@Builder
	public Tag(String name, TagCategory category) {
		this.name = name;
		this.category = category;
	}
}

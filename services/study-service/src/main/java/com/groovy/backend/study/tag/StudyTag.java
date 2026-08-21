package com.groovy.backend.study.tag;

import com.groovy.backend.common.entity.BaseTimeEntity;
import com.groovy.backend.study.Study;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Study Aggregate가 소유하는 데이터(docs/Groovy_MSA_도메인경계_재검토.md) — Study와 함께
// study-service로 옮겨왔다. 같은 스키마 안이라 groovy 때와 동일하게 엔티티 연관관계를 유지한다.
@Entity
@Table(
	name = "study_tags",
	uniqueConstraints = @UniqueConstraint(name = "uk_study_tag", columnNames = {"study_id", "tag_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudyTag extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "study_id", nullable = false)
	private Study study;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tag_id", nullable = false)
	private Tag tag;

	@Builder
	public StudyTag(Study study, Tag tag) {
		this.study = study;
		this.tag = tag;
	}
}

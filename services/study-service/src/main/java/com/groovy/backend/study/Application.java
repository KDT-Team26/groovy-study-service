package com.groovy.backend.study;

import com.groovy.backend.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(
	name = "applications",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_application_study_applicant",
		columnNames = {"study_id", "applicant_id"}
	)
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "study_id", nullable = false)
	private Study study;

	// applicant는 User Aggregate(identity-service 소속) 소속이라 FK 값만 저장한다.
	@Column(name = "applicant_id", nullable = false)
	private Long applicantId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ApplicationStatus status;

	@Builder
	public Application(Study study, Long applicantId, ApplicationStatus status) {
		this.study = study;
		this.applicantId = applicantId;
		this.status = status;
	}

	public void approve() {
		this.status = ApplicationStatus.APPROVED;
	}

	public void reject() {
		this.status = ApplicationStatus.REJECTED;
	}

	// (study_id, applicant_id)에 유니크 제약이 있어 거절된 신청은 새 row를 만드는 대신
	// 기존 row를 PENDING으로 되돌려 재사용한다.
	public void reapply() {
		this.status = ApplicationStatus.PENDING;
	}

	public boolean isPending() {
		return this.status == ApplicationStatus.PENDING;
	}
}

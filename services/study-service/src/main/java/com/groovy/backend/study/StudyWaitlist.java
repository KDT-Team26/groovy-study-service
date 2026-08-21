package com.groovy.backend.study;

import com.groovy.backend.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
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

/**
 * 정원이 가득 찬 스터디의 "빈자리 알림" 대기열 등록. 본인이 명시적으로 취소하거나
 * 실제로 그 스터디에 신청이 승인되어 합류한 경우에만 제거되고, 알림을 보낸 것만으로는
 * 제거되지 않는다(자리가 비었다 찼다를 반복하면 계속 알림을 받을 수 있음).
 */
@Entity
@Table(
	name = "study_waitlists",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_study_waitlist_study_user",
		columnNames = {"study_id", "user_id"}
	)
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudyWaitlist extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "study_id", nullable = false)
	private Study study;

	// user는 User Aggregate(identity-service 소속) 소속이라 FK 값만 저장한다.
	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Builder
	public StudyWaitlist(Study study, Long userId) {
		this.study = study;
		this.userId = userId;
	}
}

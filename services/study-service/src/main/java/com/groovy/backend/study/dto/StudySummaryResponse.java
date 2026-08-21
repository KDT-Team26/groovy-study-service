package com.groovy.backend.study.dto;

import com.groovy.backend.study.Study;

/**
 * MSA 전환(study-service 추출): groovy(레거시)의 Memoir가 memoir.getStudy().getTitle() 처럼
 * 엔티티 연관관계로 바로 읽던 표시용 필드(제목/레벨/경험치)를, Study가 다른 서비스로 옮겨간
 * 뒤에는 배치 조회로 받아야 해서 추가한 최소 응답 DTO(StudyServiceClient가 소비).
 */
public record StudySummaryResponse(
	String id,
	String title,
	Integer level,
	Integer expPoint
) {

	public static StudySummaryResponse from(Study study) {
		return new StudySummaryResponse(
			String.valueOf(study.getId()),
			study.getTitle(),
			study.getLevel(),
			study.getExpPoint()
		);
	}
}

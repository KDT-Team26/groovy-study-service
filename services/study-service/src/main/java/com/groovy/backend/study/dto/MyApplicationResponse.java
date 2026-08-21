package com.groovy.backend.study.dto;

import java.time.LocalDateTime;

import com.groovy.backend.study.Application;
import com.groovy.backend.study.ApplicationStatus;

// 마이페이지에서 내가 신청한 스터디 전체(대기/승인/거절)를 스터디명과 함께 한 번에 보여주기 위한 응답 DTO.
public record MyApplicationResponse(
	String id,
	String studyId,
	String studyTitle,
	ApplicationStatus status,
	LocalDateTime appliedAt
) {

	public static MyApplicationResponse from(Application application) {
		return new MyApplicationResponse(
			String.valueOf(application.getId()),
			String.valueOf(application.getStudy().getId()),
			application.getStudy().getTitle(),
			application.getStatus(),
			application.getCreatedAt()
		);
	}
}

package com.groovy.backend.study.dto;

import java.time.LocalDateTime;

import com.groovy.backend.study.Application;
import com.groovy.backend.study.ApplicationStatus;

// applicant가 더 이상 Application 엔티티에 물려있는 User 참조가 아니라 applicantId(Long)뿐이라
// 이름은 호출부(ApplicationService)가 UserServiceClient를 통해 조회해서 넘겨준다.
public record ApplicationResponse(
	String id,
	String studyId,
	String userId,
	String userName,
	ApplicationStatus status,
	LocalDateTime appliedAt
) {

	public static ApplicationResponse from(Application application, String applicantName) {
		return new ApplicationResponse(
			String.valueOf(application.getId()),
			String.valueOf(application.getStudy().getId()),
			String.valueOf(application.getApplicantId()),
			applicantName,
			application.getStatus(),
			application.getCreatedAt()
		);
	}
}

package com.groovy.backend.study.dto;

// 프론트엔드는 StudyMatch를 { study: Study, matchedTagCount, matchScore } 중첩 구조로 소비하므로
// StudyResponse를 그대로 감싸서 내려준다.
public record StudyMatchResponse(
	StudyResponse study,
	long matchedTagCount,
	double matchScore
) {

	public static StudyMatchResponse of(StudyResponse study, long matchedTagCount, double matchScore) {
		return new StudyMatchResponse(study, matchedTagCount, matchScore);
	}
}

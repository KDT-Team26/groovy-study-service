package com.groovy.backend.study.dto;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import com.groovy.backend.study.MeetingDay;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StudyUpdateRequest(

	@NotBlank(message = "스터디 제목은 필수입니다.")
	String title,

	@NotBlank(message = "스터디 설명은 필수입니다.")
	String description,

	@NotNull(message = "정원은 필수입니다.")
	@Min(value = 1, message = "정원은 1명 이상이어야 합니다.")
	Integer capacity,

	Set<MeetingDay> meetingDays,

	LocalTime meetingStartTime,

	LocalTime meetingEndTime,

	List<Long> tagIds
) {

	public StudyUpdateRequest {
		meetingDays = meetingDays == null ? Set.of() : meetingDays;
		tagIds = tagIds == null ? List.of() : tagIds;
	}
}

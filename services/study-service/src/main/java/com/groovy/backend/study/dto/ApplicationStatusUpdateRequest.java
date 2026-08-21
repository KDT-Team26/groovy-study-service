package com.groovy.backend.study.dto;

import com.groovy.backend.study.ApplicationStatus;

import jakarta.validation.constraints.NotNull;

public record ApplicationStatusUpdateRequest(

	@NotNull(message = "변경할 상태 값은 필수입니다.")
	ApplicationStatus status
) {
}

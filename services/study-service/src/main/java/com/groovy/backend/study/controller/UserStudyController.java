package com.groovy.backend.study.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groovy.backend.common.response.ApiResponse;
import com.groovy.backend.study.dto.MyApplicationResponse;
import com.groovy.backend.study.dto.StudyResponse;
import com.groovy.backend.study.service.ApplicationService;
import com.groovy.backend.study.service.StudyService;

import lombok.RequiredArgsConstructor;

/**
 * MSA 전환(study-service 추출): groovy(레거시) UserController에 남아있던 "/api/users/me/studies",
 * "/api/users/me/applications"를 그대로 옮겨왔다 — Study 데이터를 반환하는 엔드포인트라 이번
 * 단계에서 이관 대상이었다(groovy UserController 주석 참고). 프론트엔드(front/src/api/users.ts)가
 * 이 URL을 그대로 호출하므로 경로 자체는 바꾸지 않았다.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserStudyController {

	private final StudyService studyService;
	private final ApplicationService applicationService;

	@GetMapping("/me/studies")
	public ApiResponse<List<StudyResponse>> getMyStudies(@AuthenticationPrincipal Long userId) {
		return ApiResponse.of("SUCCESS", "내가 만든 스터디 조회에 성공했습니다.", studyService.getMyStudies(userId));
	}

	@GetMapping("/me/applications")
	public ApiResponse<List<MyApplicationResponse>> getMyApplications(@AuthenticationPrincipal Long userId) {
		return ApiResponse.of("SUCCESS", "내 신청 내역 조회에 성공했습니다.", applicationService.getMyApplications(userId));
	}
}

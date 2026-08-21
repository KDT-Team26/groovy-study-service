package com.groovy.backend.study.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.groovy.backend.common.response.ApiResponse;
import com.groovy.backend.study.Study;
import com.groovy.backend.study.dto.StudyCreateRequest;
import com.groovy.backend.study.dto.StudyMatchResponse;
import com.groovy.backend.study.dto.StudyResponse;
import com.groovy.backend.study.dto.StudySummaryResponse;
import com.groovy.backend.study.dto.StudyUpdateRequest;
import com.groovy.backend.study.service.ApplicationService;
import com.groovy.backend.study.service.StudyService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/studies")
@RequiredArgsConstructor
public class StudyController {

	private final StudyService studyService;
	private final ApplicationService applicationService;

	@PostMapping
	public ApiResponse<StudyResponse> createStudy(
		@AuthenticationPrincipal Long userId,
		@Valid @RequestBody StudyCreateRequest request
	) {
		return ApiResponse.of("SUCCESS", "스터디 그룹이 생성되었습니다.", studyService.createStudy(userId, request));
	}

	@GetMapping
	public ApiResponse<Page<StudyResponse>> getStudies(
		@PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
	) {
		return ApiResponse.of("SUCCESS", "스터디 목록 조회에 성공했습니다.", studyService.getStudies(pageable));
	}

	@GetMapping("/{studyId}")
	public ApiResponse<StudyResponse> getStudy(@AuthenticationPrincipal Long userId, @PathVariable Long studyId) {
		return ApiResponse.of("SUCCESS", "스터디 상세 조회에 성공했습니다.", studyService.getStudy(userId, studyId));
	}

	// 리터럴 경로("/match")가 "/{studyId}" 변수 경로보다 우선 매칭되므로 순서와 무관하게 안전하다.
	@GetMapping("/match")
	public ApiResponse<Page<StudyMatchResponse>> getMatchedStudies(
		@AuthenticationPrincipal Long userId,
		@RequestParam(required = false) List<Long> tagIds,
		@PageableDefault(size = 10) Pageable pageable
	) {
		return ApiResponse.of("SUCCESS", "태그 매칭 스터디 조회에 성공했습니다.", studyService.getMatchedStudies(userId, tagIds, pageable));
	}

	// MSA 전환(study-service 추출): groovy(레거시)의 Memoir가 memoir.getStudy().getTitle()처럼
	// 엔티티 연관관계로 바로 읽던 표시용 필드를 배치로 조회할 때 쓰는 서비스 간 내부 API.
	// 게이트웨이에 라우트를 추가하지 않는다(공개 API 아님).
	@GetMapping("/summary")
	public ApiResponse<Map<Long, StudySummaryResponse>> getStudySummaries(@RequestParam List<Long> ids) {
		Map<Long, StudySummaryResponse> summaries = studyService.getStudiesByIds(ids).stream()
			.collect(Collectors.toMap(Study::getId, StudySummaryResponse::from));
		return ApiResponse.of("SUCCESS", "스터디 요약 조회에 성공했습니다.", summaries);
	}

	@PutMapping("/{studyId}")
	public ApiResponse<StudyResponse> updateStudy(
		@AuthenticationPrincipal Long userId,
		@PathVariable Long studyId,
		@Valid @RequestBody StudyUpdateRequest request
	) {
		return ApiResponse.of("SUCCESS", "스터디 정보가 수정되었습니다.", studyService.updateStudy(userId, studyId, request));
	}

	@DeleteMapping("/{studyId}")
	public ApiResponse<Void> deleteStudy(@AuthenticationPrincipal Long userId, @PathVariable Long studyId) {
		studyService.deleteStudy(userId, studyId);
		return ApiResponse.of("SUCCESS", "스터디가 삭제되었습니다.");
	}

	@DeleteMapping("/{studyId}/membership")
	public ApiResponse<Void> leave(@AuthenticationPrincipal Long userId, @PathVariable Long studyId) {
		applicationService.leave(userId, studyId);
		return ApiResponse.of("SUCCESS", "스터디에서 탈퇴했습니다.");
	}

	// MSA 전환(study-service 추출): groovy(레거시)의 Calendar가 스터디 일정 변경 알림 수신자
	// (승인된 멤버 전원)를 구할 때 쓰는 서비스 간 내부 API. 게이트웨이에 라우트를 추가하지 않는다.
	@GetMapping("/{studyId}/members")
	public ApiResponse<List<Long>> getApprovedMemberIds(@PathVariable Long studyId) {
		return ApiResponse.of("SUCCESS", "승인된 멤버 목록 조회에 성공했습니다.", applicationService.getApprovedMemberUserIds(studyId));
	}
}

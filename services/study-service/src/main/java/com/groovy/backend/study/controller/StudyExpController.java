package com.groovy.backend.study.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groovy.backend.common.response.ApiResponse;
import com.groovy.backend.study.service.StudyService;

import lombok.RequiredArgsConstructor;

/**
 * MSA 전환(study-service 추출): groovy(레거시)의 Memoir/MemoirComment가 회고록/댓글 작성 시
 * 연결된 스터디에 경험치를 적립하던 StudyService#addExpAndNotifyLevelUp을, Study가 다른
 * 서비스로 옮겨간 뒤에는 서비스 간 동기 HTTP로 호출한다(groovy의 StudyServiceClient가 소비).
 * 공개 API가 아니므로(사람이 직접 호출할 일이 없다) API Gateway에 라우트를 추가하지 않는다.
 * 유효한 JWT만 요구할 뿐, 호출자가 누구인지는 신경 쓰지 않는다(단순화된 서비스 간 인증 —
 * 알려진 한계, 완전한 서비스 간 인증(mTLS 등)은 아직 없다).
 */
@RestController
@RequestMapping("/api/studies/{studyId}/exp")
@RequiredArgsConstructor
public class StudyExpController {

	private final StudyService studyService;

	public record AddExpRequest(int amount) {
	}

	@PostMapping
	public ApiResponse<Void> addExp(@PathVariable Long studyId, @RequestBody AddExpRequest request) {
		studyService.addExpAndNotifyLevelUp(studyId, request.amount());
		return ApiResponse.of("SUCCESS", "경험치가 반영되었습니다.");
	}
}

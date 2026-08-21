package com.groovy.backend.study.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groovy.backend.study.ApplicationStatus;
import com.groovy.backend.study.Study;
import com.groovy.backend.study.StudyWaitlist;
import com.groovy.backend.study.repository.ApplicationRepository;
import com.groovy.backend.study.repository.StudyRepository;
import com.groovy.backend.study.repository.WaitlistRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WaitlistService {

	private final WaitlistRepository waitlistRepository;
	private final ApplicationRepository applicationRepository;
	// StudyService를 쓰면 StudyService.updateStudy() -> WaitlistService -> StudyService로
	// 순환 의존이 생겨서, 여기서는 StudyRepository를 직접 참조한다(groovy 때와 동일한 예외적 케이스).
	private final StudyRepository studyRepository;

	@Transactional
	public void register(Long userId, Long studyId) {
		Study study = getStudy(studyId);

		if (study.isLeader(userId)) {
			log.warn("방장의 대기열 등록 시도: studyId={}, userId={}", studyId, userId);
			throw new IllegalArgumentException("방장은 대기열에 등록할 수 없습니다.");
		}
		if (applicationRepository.existsByStudyIdAndApplicantIdAndStatus(studyId, userId, ApplicationStatus.APPROVED)) {
			log.warn("이미 멤버인 유저의 대기열 등록 시도: studyId={}, userId={}", studyId, userId);
			throw new IllegalArgumentException("이미 참여 중인 스터디입니다.");
		}

		long approvedCount = applicationRepository.countByStudyIdAndStatus(studyId, ApplicationStatus.APPROVED) + 1;
		if (approvedCount < study.getCapacity()) {
			log.warn("정원이 남은 스터디의 대기열 등록 시도: studyId={}, userId={}", studyId, userId);
			throw new IllegalArgumentException("정원이 남아있는 스터디는 대기열에 등록할 수 없습니다.");
		}
		if (waitlistRepository.existsByStudyIdAndUserId(studyId, userId)) {
			log.warn("중복 대기열 등록 시도: studyId={}, userId={}", studyId, userId);
			throw new IllegalArgumentException("이미 빈자리 알림을 등록했습니다.");
		}

		waitlistRepository.save(StudyWaitlist.builder().study(study).userId(userId).build());
		log.info("대기열 등록: studyId={}, userId={}", studyId, userId);
	}

	@Transactional
	public void cancel(Long userId, Long studyId) {
		StudyWaitlist waitlist = waitlistRepository.findByStudyIdAndUserId(studyId, userId)
			.orElseThrow(() -> {
				log.warn("대기열 등록 내역 없음: studyId={}, userId={}", studyId, userId);
				return new IllegalArgumentException("빈자리 알림을 등록하지 않았습니다.");
			});

		waitlistRepository.delete(waitlist);
		log.info("대기열 취소: studyId={}, userId={}", studyId, userId);
	}

	@Transactional
	public void removeIfRegistered(Long studyId, Long userId) {
		waitlistRepository.findByStudyIdAndUserId(studyId, userId)
			.ifPresent(waitlist -> {
				waitlistRepository.delete(waitlist);
				log.info("합류 완료로 대기열 자동 제거: studyId={}, userId={}", studyId, userId);
			});
	}

	public boolean isRegistered(Long studyId, Long userId) {
		return waitlistRepository.existsByStudyIdAndUserId(studyId, userId);
	}

	public List<Long> findRecipientUserIds(Long studyId) {
		return waitlistRepository.findByStudyId(studyId).stream()
			.map(StudyWaitlist::getUserId)
			.toList();
	}

	private Study getStudy(Long studyId) {
		return studyRepository.findById(studyId)
			.orElseThrow(() -> {
				log.warn("존재하지 않는 스터디: studyId={}", studyId);
				return new IllegalArgumentException("존재하지 않는 스터디입니다.");
			});
	}
}

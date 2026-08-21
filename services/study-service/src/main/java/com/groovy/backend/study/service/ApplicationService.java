package com.groovy.backend.study.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groovy.backend.study.Application;
import com.groovy.backend.study.ApplicationStatus;
import com.groovy.backend.study.Study;
import com.groovy.backend.client.UserServiceClient;
import com.groovy.backend.study.dto.ApplicationResponse;
import com.groovy.backend.study.dto.MyApplicationResponse;
import com.groovy.backend.study.notification.NotificationOutboxPublisher;
import com.groovy.backend.study.repository.ApplicationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationService {

	private final ApplicationRepository applicationRepository;
	private final UserServiceClient userServiceClient;
	private final StudyService studyService;
	private final WaitlistService waitlistService;
	private final NotificationOutboxPublisher notificationOutboxPublisher;

	@Transactional
	public ApplicationResponse apply(Long applicantId, Long studyId) {
		Study study = studyService.getStudyEntity(studyId);

		if (study.isLeader(applicantId)) {
			log.warn("방장 자가 신청 시도: studyId={}, applicantId={}", studyId, applicantId);
			throw new IllegalArgumentException("방장은 본인 스터디에 참여 신청할 수 없습니다.");
		}

		Optional<Application> existingApplication = applicationRepository.findByStudyIdAndApplicantId(studyId, applicantId);
		if (existingApplication.isPresent() && existingApplication.get().getStatus() != ApplicationStatus.REJECTED) {
			log.warn("중복 신청 시도: studyId={}, applicantId={}", studyId, applicantId);
			throw new IllegalArgumentException("이미 참여 신청한 스터디입니다.");
		}

		long currentMemberCount = applicationRepository.countByStudyIdAndStatus(studyId, ApplicationStatus.APPROVED)
				+ 1;
		if (currentMemberCount >= study.getCapacity()) {
			log.warn("정원 마감된 스터디 신청 시도: studyId={}, applicantId={}", studyId, applicantId);
			throw new IllegalArgumentException("정원이 마감된 스터디입니다.");
		}

		Application saved;
		if (existingApplication.isPresent()) {
			saved = existingApplication.get();
			saved.reapply();
		} else {
			saved = applicationRepository.save(Application.builder()
					.study(study)
					.applicantId(applicantId)
					.status(ApplicationStatus.PENDING)
					.build());
		}
		log.info("스터디 참여 신청: studyId={}, applicantId={}, applicationId={}", studyId, applicantId, saved.getId());

		String applicantName = userServiceClient.findNamesByIds(List.of(applicantId)).get(applicantId);
		notificationOutboxPublisher.applicationSubmitted(
			study.getLeaderId(), applicantName, studyId, study.getTitle());

		return ApplicationResponse.from(saved, applicantName);
	}

	@Transactional
	public void cancel(Long applicantId, Long studyId) {
		Application application = applicationRepository.findByStudyIdAndApplicantId(studyId, applicantId)
				.orElseThrow(() -> {
					log.warn("신청 내역 없음: studyId={}, applicantId={}", studyId, applicantId);
					return new IllegalArgumentException("신청 내역이 존재하지 않습니다.");
				});

		if (!application.isPending()) {
			log.warn("취소 불가 상태 신청 취소 시도: studyId={}, applicantId={}, status={}", studyId, applicantId, application.getStatus());
			throw new IllegalArgumentException("대기 중인 신청만 취소할 수 있습니다.");
		}

		applicationRepository.delete(application);
		log.info("스터디 참여 신청 취소: studyId={}, applicantId={}", studyId, applicantId);
	}

	// "탈퇴" — 이미 승인되어 멤버가 된 사람이 스스로 나가는 것. cancel()과 달리 APPROVED 상태에서만 허용된다.
	@Transactional
	public void leave(Long applicantId, Long studyId) {
		Application application = applicationRepository.findByStudyIdAndApplicantId(studyId, applicantId)
				.orElseThrow(() -> {
					log.warn("신청 내역 없음: studyId={}, applicantId={}", studyId, applicantId);
					return new IllegalArgumentException("신청 내역이 존재하지 않습니다.");
				});

		if (application.getStatus() != ApplicationStatus.APPROVED) {
			log.warn("탈퇴 불가 상태: studyId={}, applicantId={}, status={}", studyId, applicantId, application.getStatus());
			throw new IllegalArgumentException("참여 중인 멤버만 탈퇴할 수 있습니다.");
		}

		Study study = studyService.getStudyEntityForUpdate(studyId);
		long approvedCountBeforeLeave = applicationRepository.countByStudyIdAndStatus(studyId, ApplicationStatus.APPROVED);
		boolean wasFull = approvedCountBeforeLeave + 1 >= study.getCapacity();

		applicationRepository.delete(application);
		log.info("스터디 탈퇴: studyId={}, applicantId={}", studyId, applicantId);

		if (wasFull) {
			List<Long> recipientUserIds = waitlistService.findRecipientUserIds(studyId);
			if (!recipientUserIds.isEmpty()) {
				notificationOutboxPublisher.waitlistSeatOpened(recipientUserIds, studyId, study.getTitle());
			}
		}
	}

	public List<ApplicationResponse> getApplications(Long userId, Long studyId) {
		Study study = studyService.getStudyEntity(studyId);
		studyService.validateLeader(study, userId);

		List<Application> applications = applicationRepository.findByStudyId(studyId);
		Map<Long, String> namesByApplicantId = userServiceClient.findNamesByIds(
			applications.stream().map(Application::getApplicantId).distinct().toList());

		return applications.stream()
				.map(application -> ApplicationResponse.from(application, namesByApplicantId.get(application.getApplicantId())))
				.toList();
	}

	@Transactional
	public ApplicationResponse updateStatus(Long userId, Long studyId, Long applicationId, ApplicationStatus status) {
		if (status != ApplicationStatus.APPROVED && status != ApplicationStatus.REJECTED) {
			log.warn("잘못된 신청 상태 지정 시도: studyId={}, applicationId={}, status={}", studyId, applicationId, status);
			throw new IllegalArgumentException("승인 또는 거절 상태만 지정할 수 있습니다.");
		}

		Study study = studyService.getStudyEntityForUpdate(studyId);
		studyService.validateLeader(study, userId);

		Application application = applicationRepository.findById(applicationId)
				.filter(app -> app.getStudy().getId().equals(studyId))
				.orElseThrow(() -> {
					log.warn("존재하지 않는 신청: studyId={}, applicationId={}", studyId, applicationId);
					return new IllegalArgumentException("존재하지 않는 신청입니다.");
				});

		if (status == ApplicationStatus.APPROVED) {
			long approvedCount = applicationRepository.countByStudyIdAndStatus(studyId, ApplicationStatus.APPROVED) + 1;
			if (approvedCount >= study.getCapacity()) {
				log.warn("정원 초과 승인 시도: studyId={}, applicationId={}", studyId, applicationId);
				throw new IllegalArgumentException("정원이 가득 차 승인할 수 없습니다.");
			}
			application.approve();
			waitlistService.removeIfRegistered(studyId, application.getApplicantId());
		} else {
			application.reject();
		}
		log.info("스터디 신청 상태 변경: studyId={}, applicationId={}, status={}", studyId, applicationId, status);

		notificationOutboxPublisher.applicationDecided(
			application.getApplicantId(), status == ApplicationStatus.APPROVED, studyId, study.getTitle());

		String applicantName = userServiceClient.findNamesByIds(List.of(application.getApplicantId())).get(application.getApplicantId());
		return ApplicationResponse.from(application, applicantName);
	}

	// 마이페이지 "참여 중인 스터디 / 신청 내역"용. 상태 무관 내 신청 전체를 반환한다.
	public List<MyApplicationResponse> getMyApplications(Long userId) {
		return applicationRepository.findByApplicantId(userId).stream()
				.map(MyApplicationResponse::from)
				.toList();
	}

	// Memoir/Calendar가 "이 유저가 이 스터디의 승인된 멤버인지"를 확인할 때 쓰는 공개 API.
	public boolean isApprovedMember(Long studyId, Long userId) {
		return applicationRepository.existsByStudyIdAndApplicantIdAndStatus(studyId, userId, ApplicationStatus.APPROVED);
	}

	// Calendar가 스터디 일정 변경 알림 수신자(승인된 멤버 전원)를 구할 때 쓰는 공개 API.
	public List<Long> getApprovedMemberUserIds(Long studyId) {
		return applicationRepository.findByStudyIdAndStatus(studyId, ApplicationStatus.APPROVED).stream()
				.map(Application::getApplicantId)
				.toList();
	}

	// Memoir/Calendar가 "내가 승인되어 참여 중인 스터디" 목록을 구할 때 쓰는 공개 API.
	public List<Study> getApprovedStudies(Long userId) {
		return applicationRepository.findByApplicantIdAndStatus(userId, ApplicationStatus.APPROVED).stream()
				.map(Application::getStudy)
				.toList();
	}
}

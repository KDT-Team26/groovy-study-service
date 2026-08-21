package com.groovy.backend.study.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groovy.backend.study.ApplicationStatus;
import com.groovy.backend.study.Study;
import com.groovy.backend.study.client.TagPreferenceClient;
import com.groovy.backend.client.UserServiceClient;
import com.groovy.backend.study.dto.StudyCreateRequest;
import com.groovy.backend.study.dto.StudyMatchResponse;
import com.groovy.backend.study.dto.StudyResponse;
import com.groovy.backend.study.dto.StudyUpdateRequest;
import com.groovy.backend.common.exception.ForbiddenException;
import com.groovy.backend.study.notification.NotificationOutboxPublisher;
import com.groovy.backend.study.repository.ApplicationRepository;
import com.groovy.backend.study.repository.ApplicationRepository.StudyMemberCount;
import com.groovy.backend.study.repository.StudyRepository;
import com.groovy.backend.study.tag.repository.StudyTagRepository.StudyMatchCount;
import com.groovy.backend.study.tag.service.TagService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MSA 전환(study-service 추출): groovy(레거시) StudyService에서 그대로 옮겨왔다. 가장 큰 차이는
 * 인증 principal이 email이 아니라 userId(Long)라는 점 — notification-service가 이미 쓰던 패턴
 * (uid 클레임을 바로 principal로 쓴다)을 재사용해서, "email로 User를 조회해 id를 얻는" 절차 자체가
 * 필요 없어졌다. leaderName처럼 표시용 이름만 UserServiceClient로 identity-service에 물어본다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyService {

	private final StudyRepository studyRepository;
	private final ApplicationRepository applicationRepository;
	private final UserServiceClient userServiceClient;
	private final TagPreferenceClient tagPreferenceClient;
	private final TagService tagService;
	private final WaitlistService waitlistService;
	private final NotificationOutboxPublisher notificationOutboxPublisher;

	@Transactional
	public StudyResponse createStudy(Long leaderId, StudyCreateRequest request) {
		Study study = Study.builder()
			.title(request.title())
			.description(request.description())
			.leaderId(leaderId)
			.capacity(request.capacity())
			.meetingDays(request.meetingDays())
			.meetingStartTime(request.meetingStartTime())
			.meetingEndTime(request.meetingEndTime())
			.build();

		Study savedStudy = studyRepository.save(study);
		tagService.replaceStudyTags(savedStudy, request.tagIds());
		log.info("스터디 생성: studyId={}, leaderId={}", savedStudy.getId(), leaderId);

		String leaderName = userServiceClient.findNamesByIds(List.of(leaderId)).get(leaderId);
		// 스터디장은 신청 없이 항상 멤버로 집계되므로, 생성 직후 멤버 수는 1명이다.
		return StudyResponse.from(savedStudy, leaderName, 1L, request.tagIds());
	}

	public Page<StudyResponse> getStudies(Pageable pageable) {
		Page<Study> studies = studyRepository.findAll(pageable);
		List<Long> studyIds = studies.getContent().stream().map(Study::getId).toList();

		Map<Long, List<Long>> tagIdsByStudyId = tagService.getStudyTagIdsGroupedByStudyIds(studyIds);
		Map<Long, Long> approvedMemberCountByStudyId = getApprovedMemberCounts(studyIds);
		Map<Long, String> leaderNameByLeaderId = resolveLeaderNames(studies.getContent());

		return studies.map(study -> StudyResponse.from(
			study,
			leaderNameByLeaderId.get(study.getLeaderId()),
			resolveMemberCount(approvedMemberCountByStudyId, study.getId()),
			tagIdsByStudyId.getOrDefault(study.getId(), List.of())
		));
	}

	// 마이페이지 "내가 만든 스터디" 목록. 내가 방장인 스터디만 조회한다.
	public List<StudyResponse> getMyStudies(Long userId) {
		List<Study> myStudies = studyRepository.findByLeaderId(userId);
		List<Long> studyIds = myStudies.stream().map(Study::getId).toList();

		Map<Long, List<Long>> tagIdsByStudyId = tagService.getStudyTagIdsGroupedByStudyIds(studyIds);
		Map<Long, Long> approvedMemberCountByStudyId = getApprovedMemberCounts(studyIds);
		String userName = userServiceClient.findNamesByIds(List.of(userId)).get(userId);

		return myStudies.stream()
			.map(study -> StudyResponse.from(
				study,
				userName,
				resolveMemberCount(approvedMemberCountByStudyId, study.getId()),
				tagIdsByStudyId.getOrDefault(study.getId(), List.of())
			))
			.toList();
	}

	// 비로그인으로도 접근 가능한 엔드포인트라 userId가 null일 수 있다 — 이 경우 내 신청/대기열
	// 상태는 전부 기본값으로 둔다.
	public StudyResponse getStudy(Long userId, Long studyId) {
		Study study = getStudyEntity(studyId);
		long memberCount = applicationRepository.countByStudyIdAndStatus(studyId, ApplicationStatus.APPROVED) + 1;
		List<Long> tagIds = tagService.getStudyTagIds(studyId);
		String leaderName = resolveLeaderNames(List.of(study)).get(study.getLeaderId());

		if (userId == null) {
			return StudyResponse.from(study, leaderName, memberCount, tagIds, "NONE", false);
		}

		String myApplicationStatus = resolveMyApplicationStatus(study, userId);
		boolean myWaitlistRegistered = waitlistService.isRegistered(studyId, userId);
		return StudyResponse.from(study, leaderName, memberCount, tagIds, myApplicationStatus, myWaitlistRegistered);
	}

	private String resolveMyApplicationStatus(Study study, Long userId) {
		if (study.isLeader(userId)) {
			return "APPROVED";
		}
		return applicationRepository.findByStudyIdAndApplicantId(study.getId(), userId)
			.map(application -> application.getStatus().name())
			.orElse("NONE");
	}

	@Transactional
	public StudyResponse updateStudy(Long userId, Long studyId, StudyUpdateRequest request) {
		Study study = getStudyEntityForUpdate(studyId);
		validateLeader(study, userId);

		Integer previousCapacity = study.getCapacity();
		long approvedCount = applicationRepository.countByStudyIdAndStatus(studyId, ApplicationStatus.APPROVED);
		boolean wasFull = approvedCount + 1 >= previousCapacity;

		study.update(request.title(), request.description(), request.capacity(), request.meetingDays(), request.meetingStartTime(), request.meetingEndTime());
		tagService.replaceStudyTags(study, request.tagIds());
		log.info("스터디 수정: studyId={}, userId={}", studyId, userId);

		boolean isFullNow = approvedCount + 1 >= study.getCapacity();
		if (wasFull && !isFullNow) {
			List<Long> recipientUserIds = waitlistService.findRecipientUserIds(studyId);
			if (!recipientUserIds.isEmpty()) {
				notificationOutboxPublisher.waitlistSeatOpened(recipientUserIds, studyId, study.getTitle());
			}
		}

		long memberCount = approvedCount + 1;
		String leaderName = resolveLeaderNames(List.of(study)).get(study.getLeaderId());
		return StudyResponse.from(study, leaderName, memberCount, request.tagIds());
	}

	// 회고록/댓글 작성 등 스터디 경험치가 쌓이는 모든 지점에서 이 메서드를 통해 exp를 올린다.
	// groovy(레거시)의 Memoir/MemoirComment가 StudyServiceClient(HTTP)로 이 메서드를 대신 호출한다
	// (StudyExpController 참고).
	@Transactional
	public void addExpAndNotifyLevelUp(Long studyId, int expAmount) {
		Study study = getStudyEntity(studyId);
		boolean leveledUp = study.addExp(expAmount);
		if (!leveledUp) {
			return;
		}

		List<Long> memberUserIds = getMemberUserIds(study);
		log.info("스터디 레벨업: studyId={}, newLevel={}", study.getId(), study.getLevel());
		notificationOutboxPublisher.studyLevelUp(memberUserIds, study.getId(), study.getTitle(), study.getLevel());
	}

	// 방장 + 승인된 멤버 전원의 유저 id 목록(레벨업 등 스터디 전체 알림 대상 조회용).
	private List<Long> getMemberUserIds(Study study) {
		List<Long> memberUserIds = new ArrayList<>();
		memberUserIds.add(study.getLeaderId());
		applicationRepository.findByStudyIdAndStatus(study.getId(), ApplicationStatus.APPROVED)
			.forEach(application -> memberUserIds.add(application.getApplicantId()));
		return memberUserIds;
	}

	@Transactional
	public void deleteStudy(Long userId, Long studyId) {
		Study study = getStudyEntity(studyId);
		validateLeader(study, userId);

		applicationRepository.deleteAllByStudyId(studyId);
		tagService.deleteStudyTags(studyId);
		studyRepository.delete(study);
		log.info("스터디 삭제: studyId={}, userId={}", studyId, userId);
	}

	public Page<StudyMatchResponse> getMatchedStudies(Long userId, List<Long> tagIds, Pageable pageable) {
		List<Long> targetTagIds = (tagIds != null && !tagIds.isEmpty()) ? tagIds : tagPreferenceClient.getMyPreferredTagIds();
		log.info("스터디 매칭 조회: userId={}, targetTagIds={}", userId, targetTagIds);
		Pageable pageRange = Pageable.ofSize(pageable.getPageSize()).withPage(pageable.getPageNumber());

		if (targetTagIds.isEmpty()) {
			Pageable idDescPage = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "id"));
			Page<Study> studyPage = studyRepository.findAllWithLeader(idDescPage);
			return buildMatchPage(studyPage, Map.of(), targetTagIds);
		}

		Page<StudyMatchCount> matchCountPage = tagService.getMatchedStudyIds(targetTagIds, pageRange);
		List<Long> pageIds = matchCountPage.getContent().stream().map(StudyMatchCount::getStudyId).toList();
		Map<Long, Long> matchedCountByStudyId = matchCountPage.getContent().stream()
			.collect(Collectors.toMap(StudyMatchCount::getStudyId, StudyMatchCount::getMatchedCount));

		Map<Long, Study> studyById = studyRepository.findAllWithLeaderByIdIn(pageIds).stream()
			.collect(Collectors.toMap(Study::getId, study -> study));
		List<Study> orderedStudies = pageIds.stream().map(studyById::get).toList();
		Page<Study> studyPage = new PageImpl<>(orderedStudies, pageRange, matchCountPage.getTotalElements());

		return buildMatchPage(studyPage, matchedCountByStudyId, targetTagIds);
	}

	private Page<StudyMatchResponse> buildMatchPage(Page<Study> studyPage, Map<Long, Long> matchedCountByStudyId, List<Long> targetTagIds) {
		List<Long> studyIds = studyPage.getContent().stream().map(Study::getId).toList();

		Map<Long, List<Long>> studyTagIdsByStudyId = tagService.getStudyTagIdsGroupedByStudyIds(studyIds);
		Map<Long, Long> approvedMemberCountByStudyId = getApprovedMemberCounts(studyIds);
		Map<Long, String> leaderNameByLeaderId = resolveLeaderNames(studyPage.getContent());

		return studyPage.map(study -> {
			List<Long> studyTagIds = studyTagIdsByStudyId.getOrDefault(study.getId(), List.of());
			long matchedCount = matchedCountByStudyId.getOrDefault(study.getId(), 0L);
			double matchScore = targetTagIds.isEmpty() ? 0.0 : matchedCount * 100.0 / targetTagIds.size();

			StudyResponse studyResponse = StudyResponse.from(
				study,
				leaderNameByLeaderId.get(study.getLeaderId()),
				resolveMemberCount(approvedMemberCountByStudyId, study.getId()),
				studyTagIds
			);
			return StudyMatchResponse.of(studyResponse, matchedCount, matchScore);
		});
	}

	// 목록/매칭 등 다건 조회에서 study별 leaderId를 모아 한 번의 배치 조회로 이름을 채운다(N+1 회피).
	private Map<Long, String> resolveLeaderNames(List<Study> studies) {
		List<Long> leaderIds = studies.stream().map(Study::getLeaderId).distinct().toList();
		return userServiceClient.findNamesByIds(leaderIds);
	}

	private Map<Long, Long> getApprovedMemberCounts(List<Long> studyIds) {
		if (studyIds.isEmpty()) {
			return Map.of();
		}

		return applicationRepository.countByStudyIdInAndStatus(studyIds, ApplicationStatus.APPROVED).stream()
			.collect(Collectors.toMap(StudyMemberCount::getStudyId, StudyMemberCount::getMemberCount));
	}

	private long resolveMemberCount(Map<Long, Long> approvedMemberCountByStudyId, Long studyId) {
		return approvedMemberCountByStudyId.getOrDefault(studyId, 0L) + 1;
	}

	// ApplicationService/WaitlistService/StudyExpController 등 같은 서비스 내 다른 컴포넌트가
	// Study 엔티티를 조회할 때 쓰는 공개 API.
	public Study getStudyEntity(Long studyId) {
		return studyRepository.findById(studyId)
			.orElseThrow(() -> {
				log.warn("존재하지 않는 스터디: studyId={}", studyId);
				return new IllegalArgumentException("존재하지 않는 스터디입니다.");
			});
	}

	Study getStudyEntityForUpdate(Long studyId) {
		return studyRepository.findByIdForUpdate(studyId)
			.orElseThrow(() -> {
				log.warn("존재하지 않는 스터디: studyId={}", studyId);
				return new IllegalArgumentException("존재하지 않는 스터디입니다.");
			});
	}

	void validateLeader(Study study, Long userId) {
		if (!study.isLeader(userId)) {
			log.warn("스터디 방장 아님: studyId={}, userId={}", study.getId(), userId);
			throw new ForbiddenException("스터디 방장만 수행할 수 있는 작업입니다.");
		}
	}

	// groovy(레거시)의 Memoir가 "내가 방장인 스터디" 목록을 구할 때, UserStudyController를 통해
	// 간접적으로 쓰는 공개 API.
	public List<Study> getStudiesLedBy(Long leaderId) {
		return studyRepository.findByLeaderId(leaderId);
	}

	// StudyController#getStudySummaries(배치 조회, groovy의 StudyServiceClient가 소비)가 쓴다.
	public List<Study> getStudiesByIds(List<Long> studyIds) {
		return studyRepository.findAllWithLeaderByIdIn(studyIds);
	}
}

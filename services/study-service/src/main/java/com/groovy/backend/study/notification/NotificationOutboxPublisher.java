package com.groovy.backend.study.notification;

import java.util.List;

import org.springframework.stereotype.Component;

import com.groovy.backend.eventcontract.notification.NotificationPayload;
import com.groovy.backend.study.outbox.OutboxEventWriter;

import lombok.RequiredArgsConstructor;

/**
 * MSA 전환(study-service 추출): groovy(레거시)의 NotificationOutboxPublisher에서 Study/
 * Application/Waitlist가 쓰던 4개 메서드만 옮겨온다. Memoir/Calendar가 쓰던 나머지 메서드
 * (memoirCommentAdded, memoirLikeAdded, studyScheduleChanged)는 groovy에 그대로 남아있다
 * (해당 도메인이 아직 groovy에 있으므로).
 */
@Component
@RequiredArgsConstructor
public class NotificationOutboxPublisher {

	private final OutboxEventWriter outboxEventWriter;

	public void applicationSubmitted(Long recipientUserId, String applicantName, Long studyId, String studyTitle) {
		publish(recipientUserId, "APPLICATION_RECEIVED", "새 참여 신청이 도착했어요",
			"%s님이 \"%s\"에 참여 신청했어요.".formatted(applicantName, studyTitle), studyId);
	}

	public void applicationDecided(Long recipientUserId, boolean approved, Long studyId, String studyTitle) {
		if (approved) {
			publish(recipientUserId, "APPLICATION_APPROVED", "참여 신청이 승인됐어요",
				"\"%s\"에 참여가 확정됐어요.".formatted(studyTitle), studyId);
		} else {
			publish(recipientUserId, "APPLICATION_REJECTED", "참여 신청이 거절됐어요",
				"\"%s\" 참여 신청이 거절됐어요.".formatted(studyTitle), studyId);
		}
	}

	public void waitlistSeatOpened(List<Long> recipientUserIds, Long studyId, String studyTitle) {
		for (Long recipientUserId : recipientUserIds) {
			publish(recipientUserId, "WAITLIST_SEAT_OPENED", "빈자리가 생겼어요",
				"\"%s\"에 빈자리가 생겼어요. 지금 신청해보세요.".formatted(studyTitle), studyId);
		}
	}

	public void studyLevelUp(List<Long> recipientUserIds, Long studyId, String studyTitle, int newLevel) {
		for (Long recipientUserId : recipientUserIds) {
			publish(recipientUserId, "STUDY_LEVEL_UP", "스터디 레벨이 올랐어요",
				"\"%s\"이(가) 레벨 %d이 됐어요!".formatted(studyTitle, newLevel), studyId);
		}
	}

	private void publish(Long recipientUserId, String type, String title, String message, Long targetId) {
		outboxEventWriter.write(type, new NotificationPayload(recipientUserId, type, title, message, targetId));
	}
}

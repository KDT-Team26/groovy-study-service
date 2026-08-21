package com.groovy.backend.study;

import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import com.groovy.backend.common.entity.BaseTimeEntity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "studies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Study extends BaseTimeEntity {

	// 회고록/댓글 작성 시 이 값만큼 경험치가 누적되고, EXP_PER_LEVEL마다 레벨이 오른다.
	private static final int EXP_PER_LEVEL = 100;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String description;

	// leader는 User Aggregate(identity-service 소속) 소속이라 FK 값만 저장한다. 이름 등 상세
	// 정보가 필요하면 UserServiceClient로 identity-service를 호출한다.
	@Column(name = "leader_id", nullable = false)
	private Long leaderId;

	@Column(nullable = false)
	private Integer capacity;

	@ElementCollection(fetch = FetchType.EAGER)
	@Fetch(FetchMode.SUBSELECT)
	@CollectionTable(name = "study_meeting_days", joinColumns = @JoinColumn(name = "study_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "day_of_week", nullable = false)
	private Set<MeetingDay> meetingDays = new LinkedHashSet<>();

	@Column
	private LocalTime meetingStartTime;

	@Column
	private LocalTime meetingEndTime;

	@Column(nullable = false)
	private Integer level = 1;

	@Column(name = "exp_point", nullable = false)
	private Integer expPoint = 0;

	@Builder
	public Study(String title, String description, Long leaderId, Integer capacity, Set<MeetingDay> meetingDays, LocalTime meetingStartTime, LocalTime meetingEndTime) {
		this.title = title;
		this.description = description;
		this.leaderId = leaderId;
		this.capacity = capacity;
		this.meetingDays = meetingDays != null ? new LinkedHashSet<>(meetingDays) : new LinkedHashSet<>();
		this.meetingStartTime = meetingStartTime;
		this.meetingEndTime = meetingEndTime;
	}

	public void update(String title, String description, Integer capacity, Set<MeetingDay> meetingDays, LocalTime meetingStartTime, LocalTime meetingEndTime) {
		this.title = title;
		this.description = description;
		this.capacity = capacity;
		this.meetingDays.clear();
		if (meetingDays != null) {
			this.meetingDays.addAll(meetingDays);
		}
		this.meetingStartTime = meetingStartTime;
		this.meetingEndTime = meetingEndTime;
	}

	public boolean isLeader(Long userId) {
		return this.leaderId.equals(userId);
	}

	// 레벨이 실제로 올랐는지(호출한 쪽이 레벨업 알림을 보낼지 판단할 수 있도록)를 반환한다.
	public boolean addExp(int amount) {
		int previousLevel = this.level;
		this.expPoint += amount;
		this.level = this.expPoint / EXP_PER_LEVEL + 1;
		return this.level > previousLevel;
	}
}

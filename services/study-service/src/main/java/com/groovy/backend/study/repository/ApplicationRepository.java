package com.groovy.backend.study.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groovy.backend.study.Application;
import com.groovy.backend.study.ApplicationStatus;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

	boolean existsByStudyIdAndApplicantIdAndStatus(Long studyId, Long applicantId, ApplicationStatus status);

	Optional<Application> findByStudyIdAndApplicantId(Long studyId, Long applicantId);

	List<Application> findByStudyId(Long studyId);

	List<Application> findByStudyIdAndStatus(Long studyId, ApplicationStatus status);

	List<Application> findByApplicantIdAndStatus(Long applicantId, ApplicationStatus status);

	List<Application> findByApplicantId(Long applicantId);

	long countByStudyIdAndStatus(Long studyId, ApplicationStatus status);

	@Query("SELECT a.study.id AS studyId, COUNT(a) AS memberCount "
		+ "FROM Application a "
		+ "WHERE a.status = :status AND a.study.id IN :studyIds "
		+ "GROUP BY a.study.id")
	List<StudyMemberCount> countByStudyIdInAndStatus(@Param("studyIds") List<Long> studyIds, @Param("status") ApplicationStatus status);

	void deleteAllByStudyId(Long studyId);

	interface StudyMemberCount {
		Long getStudyId();

		Long getMemberCount();
	}
}

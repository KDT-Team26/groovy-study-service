package com.groovy.backend.study.tag.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groovy.backend.study.tag.StudyTag;

public interface StudyTagRepository extends JpaRepository<StudyTag, Long> {

	List<StudyTag> findByStudyId(Long studyId);

	List<StudyTag> findByStudyIdIn(List<Long> studyIds);

	void deleteAllByStudyId(Long studyId);

	@Query(
		value = "SELECT s.id AS studyId, COUNT(st.id) AS matchedCount "
			+ "FROM Study s LEFT JOIN StudyTag st ON st.study = s AND st.tag.id IN :tagIds "
			+ "GROUP BY s.id "
			+ "ORDER BY COUNT(st.id) DESC, s.id DESC",
		countQuery = "SELECT COUNT(s) FROM Study s"
	)
	Page<StudyMatchCount> findMatchedStudyIds(@Param("tagIds") List<Long> tagIds, Pageable pageable);

	interface StudyMatchCount {
		Long getStudyId();

		Long getMatchedCount();
	}
}

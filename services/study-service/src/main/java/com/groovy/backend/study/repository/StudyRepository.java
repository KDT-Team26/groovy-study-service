package com.groovy.backend.study.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groovy.backend.study.Study;

import jakarta.persistence.LockModeType;

public interface StudyRepository extends JpaRepository<Study, Long> {

	List<Study> findByLeaderId(Long leaderId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM Study s WHERE s.id = :id")
	Optional<Study> findByIdForUpdate(@Param("id") Long id);

	@Query("SELECT s FROM Study s")
	Page<Study> findAllWithLeader(Pageable pageable);

	@Query("SELECT s FROM Study s WHERE s.id IN :ids")
	List<Study> findAllWithLeaderByIdIn(@Param("ids") List<Long> ids);
}

package com.groovy.backend.study.tag.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groovy.backend.study.Study;
import com.groovy.backend.study.tag.StudyTag;
import com.groovy.backend.study.tag.Tag;
import com.groovy.backend.study.tag.repository.StudyTagRepository;
import com.groovy.backend.study.tag.repository.StudyTagRepository.StudyMatchCount;
import com.groovy.backend.study.tag.repository.TagRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MSA 전환(study-service 추출): groovy(레거시) TagService에서 StudyTag 관련 메서드만 옮겨왔다.
 * UserTag(선호 태그)/전체 태그 목록 조회는 groovy에 그대로 남아있다(User가 identity-service로
 * 이미 이관됐고, Tag 마스터 소유권이 아직 최종 확정되지 않았기 때문 — Study.java 주석 참고).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagService {

	private final TagRepository tagRepository;
	private final StudyTagRepository studyTagRepository;

	@Transactional
	public void replaceStudyTags(Study study, List<Long> tagIds) {
		List<Tag> tags = resolveTags(tagIds);
		Set<Long> newTagIds = tags.stream().map(Tag::getId).collect(Collectors.toSet());

		List<StudyTag> existingStudyTags = studyTagRepository.findByStudyId(study.getId());
		Set<Long> existingTagIds = existingStudyTags.stream()
			.map(studyTag -> studyTag.getTag().getId())
			.collect(Collectors.toSet());

		List<StudyTag> toRemove = existingStudyTags.stream()
			.filter(studyTag -> !newTagIds.contains(studyTag.getTag().getId()))
			.toList();
		studyTagRepository.deleteAll(toRemove);

		List<StudyTag> toAdd = tags.stream()
			.filter(tag -> !existingTagIds.contains(tag.getId()))
			.map(tag -> StudyTag.builder().study(study).tag(tag).build())
			.toList();
		studyTagRepository.saveAll(toAdd);
		log.info("스터디 태그 변경: studyId={}, tagIds={}", study.getId(), tagIds);
	}

	@Transactional
	public void deleteStudyTags(Long studyId) {
		studyTagRepository.deleteAllByStudyId(studyId);
		log.info("스터디 태그 삭제: studyId={}", studyId);
	}

	public List<Long> getStudyTagIds(Long studyId) {
		return studyTagRepository.findByStudyId(studyId).stream()
			.map(studyTag -> studyTag.getTag().getId())
			.toList();
	}

	public Page<StudyMatchCount> getMatchedStudyIds(List<Long> tagIds, Pageable pageable) {
		return studyTagRepository.findMatchedStudyIds(tagIds, pageable);
	}

	public Map<Long, List<Long>> getStudyTagIdsGroupedByStudyIds(List<Long> studyIds) {
		if (studyIds.isEmpty()) {
			return Map.of();
		}

		return studyTagRepository.findByStudyIdIn(studyIds).stream()
			.collect(Collectors.groupingBy(
				studyTag -> studyTag.getStudy().getId(),
				Collectors.mapping(studyTag -> studyTag.getTag().getId(), Collectors.toList())
			));
	}

	private List<Tag> resolveTags(List<Long> tagIds) {
		if (tagIds.isEmpty()) {
			return List.of();
		}

		List<Tag> tags = tagRepository.findAllById(tagIds);
		if (tags.size() != new HashSet<>(tagIds).size()) {
			log.warn("존재하지 않는 태그 포함: tagIds={}", tagIds);
			throw new IllegalArgumentException("존재하지 않는 태그가 포함되어 있습니다.");
		}

		return tags;
	}
}

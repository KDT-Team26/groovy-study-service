package com.groovy.backend.study.tag.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groovy.backend.study.tag.Tag;

public interface TagRepository extends JpaRepository<Tag, Long> {
}

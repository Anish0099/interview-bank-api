package com.anish.ib.repository;

import com.anish.ib.domain.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    Optional<Question> findBySlug(String slug);

    boolean existsBySlug(String slug);
}

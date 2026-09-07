package com.anish.ib.repository;

import com.anish.ib.domain.RawPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RawPostRepository extends JpaRepository<RawPost, Long> {
    Optional<RawPost> findBySourceAndSourceId(String source, String sourceId);

    boolean existsBySourceAndSourceId(String source, String sourceId);

    List<RawPost> findFirst100ByProcessedFalseOrderByScrapedAtAsc();
}

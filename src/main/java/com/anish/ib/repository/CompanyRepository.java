package com.anish.ib.repository;

import com.anish.ib.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findBySlug(String slug);

    Optional<Company> findByCanonicalNameIgnoreCase(String canonicalName);

    @Query(value = """
        SELECT c.*, similarity(c.canonical_name, :name) AS score
        FROM companies c
        WHERE c.canonical_name % :name
           OR EXISTS (
             SELECT 1 FROM unnest(c.aliases) a WHERE a % :name
           )
        ORDER BY GREATEST(
          similarity(c.canonical_name, :name),
          COALESCE((SELECT MAX(similarity(a, :name)) FROM unnest(c.aliases) a), 0)
        ) DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Company> fuzzyMatch(@Param("name") String name, @Param("limit") int limit);
}

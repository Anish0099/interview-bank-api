package com.anish.ib.repository;

import com.anish.ib.domain.Experience;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperienceRepository extends JpaRepository<Experience, Long> {
    Page<Experience> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);

    Page<Experience> findByCompanyIdAndRoleIdOrderByCreatedAtDesc(Long companyId, Long roleId, Pageable pageable);

    long countByCompanyId(Long companyId);
}

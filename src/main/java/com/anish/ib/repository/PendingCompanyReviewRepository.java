package com.anish.ib.repository;

import com.anish.ib.domain.PendingCompanyReview;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingCompanyReviewRepository extends JpaRepository<PendingCompanyReview, Long> {
}

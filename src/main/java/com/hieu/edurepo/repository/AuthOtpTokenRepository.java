package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.AuthOtpToken;
import com.hieu.edurepo.enums.OtpPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface AuthOtpTokenRepository extends JpaRepository<AuthOtpToken, Long> {
    Optional<AuthOtpToken> findTopByEmailIgnoreCaseAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String email, OtpPurpose purpose);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthOtpToken> findFirstByEmailIgnoreCaseAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String email, OtpPurpose purpose);

    List<AuthOtpToken> findByEmailIgnoreCaseAndPurposeAndConsumedAtIsNull(String email, OtpPurpose purpose);
}

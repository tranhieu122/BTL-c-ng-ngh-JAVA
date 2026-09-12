package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.AuthOtpToken;
import com.hieu.edurepo.enums.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuthOtpTokenRepository extends JpaRepository<AuthOtpToken, Long> {
    Optional<AuthOtpToken> findTopByEmailIgnoreCaseAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String email, OtpPurpose purpose);

    List<AuthOtpToken> findByEmailIgnoreCaseAndPurposeAndConsumedAtIsNull(String email, OtpPurpose purpose);
}

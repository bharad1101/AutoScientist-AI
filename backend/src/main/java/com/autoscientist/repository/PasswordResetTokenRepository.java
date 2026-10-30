package com.autoscientist.repository;

import com.autoscientist.entity.PasswordResetToken;
import com.autoscientist.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    Optional<PasswordResetToken> findFirstByUserOrderByCreatedAtDesc(User user);

    long deleteByUser(User user);

    long deleteByExpiresAtBefore(LocalDateTime cutoff);
}

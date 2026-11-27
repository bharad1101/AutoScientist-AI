package com.autoscientist.repository;

import com.autoscientist.entity.PasswordResetToken;
import com.autoscientist.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    Optional<PasswordResetToken> findFirstByUserOrderByCreatedAtDesc(User user);

    long deleteByUser(User user);

    long deleteByExpiresAtBefore(LocalDateTime cutoff);

    /**
     * Atomically claims an unused, unexpired token. Returns the number of rows
     * updated; exactly one means this caller won the race.
     */
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now "
            + "where t.tokenHash = :tokenHash and t.usedAt is null and t.expiresAt > :now")
    int consumeIfValid(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);
}

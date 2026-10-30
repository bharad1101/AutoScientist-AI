package com.autoscientist.service;

import com.autoscientist.entity.User;

/**
 * Delivers the single-use password reset link. Implementations must never log
 * the raw token and must report delivery failure without leaking it upstream.
 */
public interface PasswordResetNotifier {

    boolean send(User user, String rawToken, long ttlMinutes);
}

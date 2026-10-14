package com.sporthub.identity.service;

import com.sporthub.identity.domain.User;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** All session issuance and account security changes take this lock before session row locks. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class UserSecurityLock {
    private final JdbcTemplate jdbc;
    private final EntityManager entities;
    public UserSecurityLock(JdbcTemplate jdbc, EntityManager entities) { this.jdbc=jdbc; this.entities=entities; }
    public void lock(UUID user) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", Object.class,
                "identity-user-security:" + user);
    }
    public User current(User user) {
        lock(user.getId()); entities.refresh(user); return user;
    }
}

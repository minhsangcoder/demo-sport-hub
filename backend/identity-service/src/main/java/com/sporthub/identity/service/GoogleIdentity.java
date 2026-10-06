package com.sporthub.identity.service;

public record GoogleIdentity(
        String googleSub,
        String email,
        boolean emailVerified,
        String name,
        String avatarUrl) {
}

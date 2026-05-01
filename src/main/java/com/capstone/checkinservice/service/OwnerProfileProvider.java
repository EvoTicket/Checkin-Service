package com.capstone.checkinservice.service;

import java.util.Optional;

public interface OwnerProfileProvider {
    Optional<OwnerProfile> findOwnerProfile(Long ownerId);

    record OwnerProfile(
            Long ownerId,
            String displayName,
            String email,
            String phone
    ) {
    }
}

package com.capstone.checkinservice.service;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class NoopOwnerProfileProvider implements OwnerProfileProvider {
    @Override
    public Optional<OwnerProfile> findOwnerProfile(Long ownerId) {
        return Optional.empty();
    }
}

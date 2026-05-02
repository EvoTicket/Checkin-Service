package com.capstone.checkinservice.security;

public record TokenMetaData(Long userId, boolean isOrganization, Long organizationId) {
}

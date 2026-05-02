package com.capstone.checkinservice.crypto;

public record QrTokenHeader(
        String alg,
        String kid,
        String typ
) {
}

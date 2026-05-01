package com.capstone.checkinservice.crypto.key;

import java.util.Optional;

public interface QrKeyProvider {
    QrSigningKey getCurrentSigningKey();

    Optional<QrVerificationKey> findVerificationKey(String kid);
}

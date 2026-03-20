package org.GLM.negoriator.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NegotiationSessionRepository extends JpaRepository<NegotiationSession, UUID> {
}

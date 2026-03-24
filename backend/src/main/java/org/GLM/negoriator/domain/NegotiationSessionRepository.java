package org.GLM.negoriator.domain;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NegotiationSessionRepository extends JpaRepository<NegotiationSession, UUID> {

	@Query("select session from NegotiationSession session where session.id = :id")
	Optional<NegotiationSession> findDetailedById(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select session from NegotiationSession session where session.id = :id")
	Optional<NegotiationSession> findDetailedByIdForUpdate(@Param("id") UUID id);
}

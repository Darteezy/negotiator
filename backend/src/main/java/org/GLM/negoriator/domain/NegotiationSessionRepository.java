package org.GLM.negoriator.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NegotiationSessionRepository extends JpaRepository<NegotiationSession, UUID> {

	@EntityGraph(attributePaths = {
		"decisions",
		"decisions.supplierOffer",
		"decisions.counterOffer"
	})
	Optional<NegotiationSession> findDetailedById(UUID id);
}

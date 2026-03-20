package org.GLM.negoriator.repository;

import org.GLM.negoriator.model.Offer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    @Query("select max(o.roundNumber) from Offer o where o.negotiation.id = :negotiationId")
    Integer findMaxRoundNumberByNegotiationId(@Param("negotiationId") Long negotiationId);

    List<Offer> findByNegotiationIdAndRoundNumberOrderByIdAsc(Long negotiationId, int roundNumber);

    List<Offer> findByNegotiationIdOrderByRoundNumberAscIdAsc(Long negotiationId);
}

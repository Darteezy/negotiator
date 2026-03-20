package org.GLM.negoriator.repository;

import org.GLM.negoriator.model.Negotiation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NegotiationRepository extends JpaRepository<Negotiation, Long> {

    @Query("""
            select distinct n
            from Negotiation n
            join fetch n.supplier
            left join fetch n.offers
            where n.id = :id
            """)
    Optional<Negotiation> findByIdWithSupplierAndOffers(@Param("id") Long id);
}

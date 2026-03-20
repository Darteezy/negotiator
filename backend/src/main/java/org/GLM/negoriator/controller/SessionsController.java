package org.GLM.negoriator.controller;

import org.GLM.negoriator.model.Negotiation;
import org.GLM.negoriator.model.Offer;
import org.GLM.negoriator.model.Supplier;
import org.GLM.negoriator.repository.NegotiationRepository;
import org.GLM.negoriator.repository.OfferRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class SessionsController {

    private final NegotiationRepository negotiationRepository;
    private final OfferRepository offerRepository;

    public SessionsController(
            NegotiationRepository negotiationRepository,
            OfferRepository offerRepository
    ) {
        this.negotiationRepository = negotiationRepository;
        this.offerRepository = offerRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<NegotiationSummary> getSession(@PathVariable Long id) {
        Negotiation negotiation = negotiationRepository.findByIdWithSupplierAndOffers(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Negotiation not found"));

        Supplier supplier = negotiation.getSupplier();
        int currentRound = negotiation.getOffers().stream()
                .map(Offer::getRoundNumber)
                .max(Comparator.naturalOrder())
                .orElse(0);

        int offersCount = negotiation.getOffers().size();

        NegotiationSummary summary = new NegotiationSummary(
                negotiation.getId(),
                new SupplierSummary(supplier.getId(), supplier.getName(), supplier.getCompany()),
                currentRound,
                offersCount
        );

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/{id}/offers")
    public ResponseEntity<List<OfferResponse>> getCurrentOffers(@PathVariable Long id) {
        ensureNegotiationExists(id);

        Integer maxRound = offerRepository.findMaxRoundNumberByNegotiationId(id);
        if (maxRound == null) {
            return ResponseEntity.ok(List.of());
        }

        List<OfferResponse> offers = offerRepository
                .findByNegotiationIdAndRoundNumberOrderByIdAsc(id, maxRound)
                .stream()
                .map(SessionsController::toOfferResponse)
                .toList();

        return ResponseEntity.ok(offers);
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<OfferResponse>> getOfferHistory(@PathVariable Long id) {
        ensureNegotiationExists(id);

        List<OfferResponse> offers = offerRepository
                .findByNegotiationIdOrderByRoundNumberAscIdAsc(id)
                .stream()
                .map(SessionsController::toOfferResponse)
                .toList();

        return ResponseEntity.ok(offers);
    }

    private void ensureNegotiationExists(Long id) {
        if (!negotiationRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Negotiation not found");
        }
    }

    private static OfferResponse toOfferResponse(Offer offer) {
        return new OfferResponse(
                offer.getId(),
                offer.getRoundNumber(),
                offer.getPrice(),
                offer.getPaymentDays(),
                offer.getDeliveryDays(),
                offer.getContractMonths()
        );
    }

    public record SupplierSummary(Long id, String name, String company) {
    }

    public record NegotiationSummary(
            Long id,
            SupplierSummary supplier,
            int currentRound,
            int offersCount
    ) {
    }

    public record OfferResponse(
            Long id,
            int roundNumber,
            BigDecimal price,
            int paymentDays,
            int deliveryDays,
            int contractMonths
    ) {
    }
}

package org.GLM.negoriator.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;

import org.GLM.negoriator.application.NegotiationSimulationService;
import org.GLM.negoriator.application.NegotiationSimulationService.SimulationConfig;
import org.GLM.negoriator.application.NegotiationSimulationService.SimulationListener;
import org.GLM.negoriator.application.NegotiationSimulationService.SimulationResult;
import org.GLM.negoriator.application.NegotiationSimulationService.SimulationRound;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/simulations")
public class SimulationController {

	private static final Logger log = LoggerFactory.getLogger(SimulationController.class);

	private static final List<String> STRESS_PERSONALITIES = List.of(
		"Professional but firm. You make reasonable concessions but push back on aggressive buyer positions.",
		"Aggressive and greedy. You rarely concede, demand top price and shortest payment terms.",
		"Friendly and accommodating. You are willing to make concessions quickly to close the deal.",
		"Vague and evasive. You often respond without specific numbers, using phrases like 'as low as possible' or 'we'll see'.",
		"Stubborn on price but flexible on other terms. You never budge on price but offer good delivery and payment terms.",
		"Erratic and unpredictable. You change your position dramatically between rounds, sometimes offering much better terms, sometimes much worse."
	);

	private final NegotiationSimulationService simulationService;
	private final AdminApiGuard adminApiGuard;
	private final ExecutorService streamExecutor;
	private final Semaphore activeStreams;
	private final long streamTimeoutMs;
	private final int maxBatchSize;

	public SimulationController(
		NegotiationSimulationService simulationService,
		AdminApiGuard adminApiGuard,
		@Value("${negotiator.simulations.max-concurrent-streams:4}") int maxConcurrentStreams,
		@Value("${negotiator.simulations.stream-timeout-ms:60000}") long streamTimeoutMs,
		@Value("${negotiator.simulations.max-batch-size:6}") int maxBatchSize
	) {
		this.simulationService = simulationService;
		this.adminApiGuard = adminApiGuard;
		int boundedConcurrency = Math.max(1, maxConcurrentStreams);
		this.streamExecutor = Executors.newFixedThreadPool(boundedConcurrency);
		this.activeStreams = new Semaphore(boundedConcurrency);
		this.streamTimeoutMs = Math.max(1_000L, streamTimeoutMs);
		this.maxBatchSize = Math.max(1, maxBatchSize);
	}

	@PreDestroy
	void shutdownExecutor() {
		streamExecutor.shutdown();
	}

	@PostMapping
	public ResponseEntity<SimulationResult> runSimulation(
		@RequestHeader(name = AdminApiGuard.ADMIN_TOKEN_HEADER, required = false) String adminToken,
		@RequestBody(required = false) SimulationRequest request
	) {
		adminApiGuard.assertAuthorized(adminToken);

		NegotiationStrategy strategy = null;
		String personality = null;

		if (request != null) {
			if (request.strategy() != null && !request.strategy().isBlank()) {
				strategy = NegotiationStrategy.valueOf(request.strategy().toUpperCase());
			}
			personality = request.supplierPersonality();
		}

		SimulationConfig config = SimulationConfig.of(
			strategy,
			personality,
			validateMaxRounds(request != null ? request.maxRounds() : null));

		log.info("Starting simulation: strategy={}, personality='{}'", config.strategy(), config.supplierPersonality());

		SimulationResult result = simulationService.runSimulation(config);

		return ResponseEntity.ok(result);
	}

	@GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter streamSimulation(
		@RequestParam(name = "token", required = false) String adminToken,
		@RequestParam(required = false) String strategy,
		@RequestParam(required = false) String supplierPersonality,
		@RequestParam(required = false) Integer maxRounds
	) {
		adminApiGuard.assertAuthorized(adminToken);

		if (!activeStreams.tryAcquire()) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many simulation streams are already active.");
		}

		SimulationConfig config = SimulationConfig.of(
			strategy != null && !strategy.isBlank() ? NegotiationStrategy.valueOf(strategy.toUpperCase()) : null,
			supplierPersonality,
			validateMaxRounds(maxRounds));

		SseEmitter emitter = new SseEmitter(streamTimeoutMs);
		AtomicBoolean streamReleased = new AtomicBoolean(false);
		Runnable releaseStream = () -> {
			if (streamReleased.compareAndSet(false, true)) {
				activeStreams.release();
			}
		};
		emitter.onTimeout(() -> {
			log.warn("Simulation stream timed out");
			releaseStream.run();
			emitter.complete();
		});
		emitter.onCompletion(() -> {
			releaseStream.run();
			log.info("Simulation stream completed");
		});
		emitter.onError(error -> releaseStream.run());

		streamExecutor.submit(() -> {
			try {
				simulationService.runSimulation(config, new StreamingSimulationListener(emitter));
				emitter.complete();
			} catch (Exception e) {
				log.error("Simulation stream failed: {}", e.getMessage(), e);
				sendEvent(emitter, "failed", new FailedEvent(e.getMessage()));
				emitter.completeWithError(e);
			} finally {
				releaseStream.run();
			}
		});

		return emitter;
	}

	@PostMapping("/batch")
	public ResponseEntity<BatchResult> runBatch(
		@RequestHeader(name = AdminApiGuard.ADMIN_TOKEN_HEADER, required = false) String adminToken,
		@RequestBody(required = false) BatchRequest request
	) {
		adminApiGuard.assertAuthorized(adminToken);

		NegotiationStrategy strategy = request != null && request.strategy() != null && !request.strategy().isBlank()
			? NegotiationStrategy.valueOf(request.strategy().toUpperCase())
			: null;

		List<String> personalities = request != null && request.personalities() != null && !request.personalities().isEmpty()
			? request.personalities()
			: STRESS_PERSONALITIES;

		if (personalities.size() > maxBatchSize) {
			throw new IllegalArgumentException("Batch simulations are limited to " + maxBatchSize + " personalities per request.");
		}

		List<SimulationResult> results = new ArrayList<>();
		List<String> allAnomalies = new ArrayList<>();

		for (int i = 0; i < personalities.size(); i++) {
			String personality = personalities.get(i);
			log.info("Batch simulation {}/{}: personality='{}'", i + 1, personalities.size(), personality);

			try {
				SimulationConfig config = SimulationConfig.of(strategy, personality, null);
				SimulationResult result = simulationService.runSimulation(config);
				results.add(result);

				for (String anomaly : result.anomalies()) {
					allAnomalies.add("[Sim " + (i + 1) + "] " + anomaly);
				}
			} catch (Exception e) {
				log.error("Batch simulation {} failed: {}", i + 1, e.getMessage());
				allAnomalies.add("[Sim " + (i + 1) + "] CRASHED: " + e.getMessage());
			}
		}

		return ResponseEntity.ok(new BatchResult(results.size(), allAnomalies.size(), results, allAnomalies));
	}

	record SimulationRequest(
		String strategy,
		String supplierPersonality,
		Integer maxRounds
	) {
	}

	record BatchRequest(
		String strategy,
		List<String> personalities
	) {
	}

	record BatchResult(
		int simulationsRun,
		int totalAnomalies,
		List<SimulationResult> simulations,
		List<String> allAnomalies
	) {
	}

	private final class StreamingSimulationListener implements SimulationListener {
		private final SseEmitter emitter;

		private StreamingSimulationListener(SseEmitter emitter) {
			this.emitter = emitter;
		}

		@Override
		public void onStarted(org.GLM.negoriator.domain.NegotiationSession session) {
			sendEvent(emitter, "started", new StartedEvent(
				session.getId().toString(),
				session.getStrategy().name(),
				session.getMaxRounds()));
		}

		@Override
		public void onRound(org.GLM.negoriator.domain.NegotiationSession session, SimulationRound round) {
			sendEvent(emitter, "round", new RoundEvent(
				session.getId().toString(),
				session.getStatus().name(),
				session.getStrategy().name(),
				session.getCurrentRound(),
				session.isClosed(),
				round));
		}

		@Override
		public void onCompleted(org.GLM.negoriator.domain.NegotiationSession session, SimulationResult result) {
			sendEvent(emitter, "completed", result);
		}
	}

	private void sendEvent(SseEmitter emitter, String name, Object payload) {
		try {
			emitter.send(SseEmitter.event().name(name).data(payload));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	record StartedEvent(String sessionId, String strategy, int maxRounds) {
	}

	record RoundEvent(
		String sessionId,
		String status,
		String strategy,
		int currentRound,
		boolean closed,
		SimulationRound round
	) {
	}

	record FailedEvent(String message) {
	}

	private Integer validateMaxRounds(Integer maxRounds) {
		if (maxRounds == null) {
			return null;
		}

		if (maxRounds <= 0 || maxRounds > 20) {
			throw new IllegalArgumentException("maxRounds must be between 1 and 20.");
		}

		return maxRounds;
	}
}

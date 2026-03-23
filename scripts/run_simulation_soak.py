#!/usr/bin/env python3

import json
import os
import sys
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib import error, request


DEFAULT_PERSONALITIES = [
    "Professional but firm. You make reasonable concessions but push back on aggressive buyer positions.",
    "Aggressive and greedy. You rarely concede, demand top price and shortest payment terms.",
    "Friendly and accommodating. You are willing to make concessions quickly to close the deal.",
    "Vague and evasive. You often respond without specific numbers, using phrases like 'as low as possible' or 'we will see'.",
    "Stubborn on price but flexible on other terms. You never budge on price but offer good delivery and payment terms.",
    "Erratic and unpredictable. You change your position dramatically between rounds, sometimes offering much better terms, sometimes much worse.",
]


def utc_now():
    return datetime.now(timezone.utc)


def iso_now():
    return utc_now().isoformat()


def append_jsonl(path: Path, payload: dict):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(payload, ensure_ascii=True) + "\n")
        handle.flush()
        os.fsync(handle.fileno())


def compact_round(round_payload: dict):
    buyer = round_payload.get("buyer") or {}
    evaluation = buyer.get("evaluation") or {}
    offer = round_payload.get("supplierOffer") or {}

    return {
        "round": round_payload.get("round"),
        "supplierMessage": round_payload.get("supplierMessage"),
        "supplierOffer": {
            "price": offer.get("price"),
            "paymentDays": offer.get("paymentDays"),
            "deliveryDays": offer.get("deliveryDays"),
            "contractMonths": offer.get("contractMonths"),
        } if offer else None,
        "buyerDecision": buyer.get("decision"),
        "buyerUtility": evaluation.get("buyerUtility"),
        "targetUtility": evaluation.get("targetUtility"),
        "estimatedSupplierUtility": evaluation.get("estimatedSupplierUtility"),
        "counterOfferCount": len(buyer.get("counterOffers") or []),
        "explanation": buyer.get("explanation"),
        "error": round_payload.get("error"),
    }


def summarize_result(index: int, personality: str, started_at: str, duration_ms: int, result: dict):
    anomalies = result.get("anomalies") or []
    rounds = result.get("rounds") or []

    return {
        "recordType": "simulation_result",
        "simulationIndex": index,
        "startedAt": started_at,
        "finishedAt": iso_now(),
        "durationMs": duration_ms,
        "personality": personality,
        "sessionId": result.get("sessionId"),
        "finalStatus": result.get("finalStatus"),
        "finalStrategy": result.get("finalStrategy"),
        "roundsPlayed": result.get("roundsPlayed"),
        "anomalyCount": len(anomalies),
        "anomalies": anomalies,
        "learning": [compact_round(item) for item in rounds],
    }


def post_json(url: str, body: dict, timeout_seconds: int):
    payload = json.dumps(body).encode("utf-8")
    req = request.Request(
        url,
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with request.urlopen(req, timeout=timeout_seconds) as response:
        return json.loads(response.read().decode("utf-8"))


def main():
    repo_root = Path(__file__).resolve().parents[1]
    output_dir = repo_root / ".local" / "simulations"
    output_path = output_dir / f"soak-{utc_now().strftime('%Y%m%d-%H%M%S')}.jsonl"

    base_url = os.environ.get("SIMULATION_URL", "http://localhost:8080/api/simulations")
    duration_minutes = int(os.environ.get("SIMULATION_DURATION_MINUTES", "30"))
    request_timeout_seconds = int(os.environ.get("SIMULATION_REQUEST_TIMEOUT_SECONDS", "180"))
    strategy = os.environ.get("SIMULATION_STRATEGY", "MESO")
    max_rounds = int(os.environ.get("SIMULATION_MAX_ROUNDS", "4"))

    deadline = utc_now() + timedelta(minutes=duration_minutes)

    append_jsonl(output_path, {
        "recordType": "soak_started",
        "startedAt": iso_now(),
        "deadline": deadline.isoformat(),
        "url": base_url,
        "requestTimeoutSeconds": request_timeout_seconds,
        "strategy": strategy,
        "maxRounds": max_rounds,
        "personalities": DEFAULT_PERSONALITIES,
    })

    print(f"Writing simulation results to {output_path}")
    print(f"Running until {deadline.isoformat()} with per-request timeout {request_timeout_seconds}s")

    simulation_index = 0
    anomaly_total = 0
    crash_total = 0

    while utc_now() < deadline:
        personality = DEFAULT_PERSONALITIES[simulation_index % len(DEFAULT_PERSONALITIES)]
        started_at = iso_now()
        started_monotonic = time.monotonic()

        append_jsonl(output_path, {
            "recordType": "simulation_started",
            "simulationIndex": simulation_index + 1,
            "startedAt": started_at,
            "personality": personality,
            "strategy": strategy,
        })

        try:
            result = post_json(
                base_url,
                {
                    "strategy": strategy,
                    "supplierPersonality": personality,
                    "maxRounds": max_rounds,
                },
                timeout_seconds=request_timeout_seconds,
            )
            duration_ms = int((time.monotonic() - started_monotonic) * 1000)
            summary = summarize_result(simulation_index + 1, personality, started_at, duration_ms, result)
            anomaly_total += summary["anomalyCount"]
            append_jsonl(output_path, summary)
            print(
                f"[{simulation_index + 1}] status={summary['finalStatus']} strategy={summary['finalStrategy']} "
                f"rounds={summary['roundsPlayed']} anomalies={summary['anomalyCount']} durationMs={duration_ms}"
            )
        except Exception as exc:
            crash_total += 1
            append_jsonl(output_path, {
                "recordType": "simulation_crashed",
                "simulationIndex": simulation_index + 1,
                "startedAt": started_at,
                "finishedAt": iso_now(),
                "personality": personality,
                "strategy": strategy,
                "errorType": type(exc).__name__,
                "error": str(exc),
            })
            print(f"[{simulation_index + 1}] crashed: {type(exc).__name__}: {exc}")

        simulation_index += 1

    append_jsonl(output_path, {
        "recordType": "soak_finished",
        "finishedAt": iso_now(),
        "simulationsAttempted": simulation_index,
        "totalAnomalies": anomaly_total,
        "totalCrashes": crash_total,
    })

    print(f"Finished. attempted={simulation_index} anomalies={anomaly_total} crashes={crash_total}")
    print(output_path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
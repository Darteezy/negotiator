# Negotiator

Buyer-side negotiation system built for the Pactum technical challenge.

The backend acts as the buyer. It evaluates supplier offers across price, payment days, delivery days, and contract length, then accepts, counters, or rejects based on the buyer's goals and limits. The frontend gives a human supplier a live interface to configure a session, negotiate round by round, and inspect the latest buyer response.

The frontend provides a supplier-facing interface for running the negotiation flow. The same backend logic can also support other supplier-facing channels.

## What is in the app

- Rule-based buyer decision engine with four negotiation issues
- Configurable buyer mandate: ideal offer, walk-away limits, weights, rounds, and risk of walkaway
- Five manual strategies: Baseline, Meso, Boulware, Conceder, and Tit-for-Tat
- Session history with structured buyer reasoning and utility snapshots
- AI-assisted supplier message parsing and buyer message generation
- Supplier-facing frontend for session setup and live negotiation

## Prerequisites

For the default Docker path:

- Docker with Compose support
- A reachable AI model endpoint for supplier-message parsing

For local development:

- Java 21
- Node.js 20+
- npm 10+
- PostgreSQL 16

## Getting started

### 1. Create the environment file

```bash
cp .env.example .env
```

Minimum values to check in `.env`:

```env
POSTGRES_DB=postgres
POSTGRES_USER=postgres
POSTGRES_PASSWORD=change-me

AI_PROVIDER=ollama
AI_BASE_URL=http://host.docker.internal:11434
AI_API_KEY=
AI_CHAT_MODEL=qwen3.5:9b
```

Notes:

- Supported AI providers are `ollama` and `openai`.
- If you use Ollama, it must already be running and the model in `AI_CHAT_MODEL` must be available there.
- Set both the AI server address and the model name in `.env` before starting the stack.
- When the backend runs in Docker and Ollama runs on your host machine, use `http://host.docker.internal:11434`.
- When the backend runs outside Docker and Ollama runs locally, `http://localhost:11434` is the default.
- For OpenAI-compatible setups, point `AI_BASE_URL` to the `/v1` root and set `AI_API_KEY` if the provider requires bearer auth.
- `VITE_API_BASE_URL` is only needed when the frontend runs outside Docker.
- The negotiation engine is rule-based, but the supplier-message flow calls `/api/ai/parse-offer`, so the app needs a configured and reachable AI model during normal UI use.

### 2. Start the stack

```bash
docker compose up --build
```

Open:

- Frontend: http://localhost:5173
- Backend API: http://localhost:8080
- PostgreSQL: localhost:5432

### 3. Use the app

1. Open the configuration screen.
2. Pick a buyer strategy.
3. Set the buyer target, limits, weights, and round count.
4. Start the session.
5. Negotiate as the supplier from the main negotiation view.
6. Adjust strategy or session settings during the session if needed.

> Screenshot placeholder: configuration screen

> Screenshot placeholder: negotiation screen

## Local development

Docker is the shortest path. If you want to run the services separately:

```bash
# backend
cd backend
./mvnw spring-boot:run

# frontend
cd ../frontend
npm install
npm run dev
```

The frontend dev server runs on `http://localhost:5173` and proxies `/api` requests to `http://localhost:8080`.

## How it works

Each supplier message goes through the same flow:

1. The frontend sends the supplier message for parsing.
2. The backend turns that message into structured terms.
3. The negotiation engine scores the offer from the buyer's point of view.
4. Hard buyer limits are checked first.
5. The active strategy shapes the buyer's concession curve and counter style.
6. The backend accepts, counters, or rejects and stores the result in the session history.

The buyer does not negotiate on price alone. Every round can trade off:

- price
- payment days
- delivery days
- contract months

## AI role

AI is present, but it does not decide the deal.

- AI parses supplier messages into structured offer terms and hard constraints.
- AI can generate buyer-facing negotiation text.
- The accept, counter, and reject decision stays rule-based in the backend.

That parsing flow combines the model response with backend heuristics for option selection and fallback handling. It is not a free-form system deciding the negotiation.

## Strategies

- Baseline: balanced default with steady concessions
- Meso: sends a small set of buyer-safe alternatives when useful
- Boulware: holds firm longer and concedes later
- Conceder: gives ground earlier to improve close rate
- Tit-for-Tat: reacts more directly to supplier movement

Manual strategy changes are supported from session settings.

## Docs

| Document                                                 | Focus                                                                   |
| -------------------------------------------------------- | ----------------------------------------------------------------------- |
| [docs/README.md](docs/README.md)                         | Docs index and suggested reading order                                  |
| [docs/architecture.md](docs/architecture.md)             | Frontend, backend, database, and AI flow in one place                   |
| [docs/MATH.md](docs/MATH.md)                             | Equations, utility math, decision curves, and code-level engine logic   |
| [docs/negotiation-engine.md](docs/negotiation-engine.md) | Scoring, decision rules, issue tradeoffs, and counteroffer flow         |
| [docs/parsing.md](docs/parsing.md)                       | Supplier-message parsing rules, intent examples, and fallback behavior  |
| [docs/testing.md](docs/testing.md)                       | Test strategy, suite structure, matrix interpretation, and testing gaps |
| [docs/strategies.md](docs/strategies.md)                 | Strategy behavior and intent                                            |
| [docs/api.md](docs/api.md)                               | REST endpoints, payloads, and example requests                          |
| [TASK.md](TASK.md)                                       | Original challenge brief                                                |

## Project layout

```text
negotiator/
├── backend/
│   ├── src/main/java/org/GLM/negoriator/
│   │   ├── ai/
│   │   ├── application/
│   │   ├── controller/
│   │   ├── domain/
│   │   └── negotiation/
│   └── src/test/java/org/GLM/negoriator/
├── docs/
│   ├── architecture.md
│   ├── api.md
│   ├── negotiation-engine.md
│   ├── parsing.md
│   ├── README.md
│   └── strategies.md
├── frontend/
│   └── src/
│       ├── components/
│       ├── lib/
│       └── pages/
├── docker-compose.yml
├── README.md
└── TASK.md
```

## Useful notes

- New sessions start from the configuration page, not directly in the chat.
- Strategy selection is manual. The system does not auto-switch strategies during a live negotiation.
- The frontend is supplier-facing. The backend represents the buyer.
- The web UI is one supplier-facing interface for the negotiation flow.
- The same buyer engine can be used through other supplier-facing channels if needed.
- If you want the deeper algorithm details, use the docs above instead of making the root README longer.

## Roadmap

- [x] Rule-based buyer negotiation engine across four issues
- [x] Configurable buyer mandate and live session settings
- [x] Manual strategy selection with multiple strategies
- [x] Session history with structured buyer reasoning
- [x] Dockerized frontend, backend, and database stack
- [ ] Automatic strategy switching based on negotiation context
- [ ] Stronger supplier context understanding beyond the current AI parsing plus heuristic fallback flow
- [ ] Better explanation of supplier constraints and preference signals across rounds
- [ ] Replay and analytics views for comparing negotiation outcomes
- [ ] Broader simulation and evaluation tooling across strategies

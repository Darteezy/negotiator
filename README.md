# Negotiator

Negotiator is a buyer-side autonomous negotiation application with a supplier-facing interface. The platform allows a human supplier to negotiate with an automated buyer agent through a structured offer and counteroffer flow.

## Overview

The system combines a Spring Boot backend, a React frontend, and PostgreSQL persistence. The buyer agent uses a hybrid negotiation strategy based on rule-based logic and LLM support. Detailed negotiation logic, scoring, and strategy design will be documented separately in a dedicated README.

## Features
- Buyer-side automated negotiation agent.
- Supplier-facing UI for live negotiation sessions.
- Back-and-forth offer and counteroffer workflow.
- Negotiation state tracking across the full session lifecycle.
- Configurable buyer goals, limits, and negotiation preferences.
- Hybrid negotiation strategy using a rule-based algorithm with LLM support.
- Persistent storage for negotiation sessions, offers, and outcomes.
- Extensible architecture for future strategy comparison, analytics, and replay features.

## Tech Stack
- Backend: Java 21 + Spring Boot
- Frontend: React
- Database: PostgreSQL
- Runtime: Node.js
- Containerization: Docker and Docker Compose
- API documentation: Swagger / OpenAPI
- Build tool: Maven

## Getting Started

### Start the backend and database

```bash
cp .env.example .env # Configure environment variables
docker compose up # Starts PostgreSQL + Springboot backend
```

Backend API: `http://localhost:8080`

### Start the frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend: `http://localhost:3000`

## Project Structure

```text
negotiator/
frontend/
backend/
docker-compose.yml
.env.example
```

## Environment Variables

The application should be configured through `.env` values for:
- Backend application configuration
- Database connection settings
- Frontend environment configuration
- External API keys for LLM integration

The final variable list should be documented alongside the implementation configuration.



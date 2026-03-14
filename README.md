### Negotiator 

Automated negotiation agent on behalf of the buyer side and a UI where human can play the supplier and negotiate against it. App has an algorithm that powers the negotiation engine with a back and forth offer/counteroffer flow.
Configurable bot has goals and limits with multiple negotiation strategies.

### Features

### Tech Stack
- Backend - Java21 + Spring Boot
- Frontend - React
- Database - PostGreSQL
- Node.js
- Docker and Docker compose

### Prerequisites
1. Start the backend and database

cp .env.example .env # Configure environment variables
docker compose up # # Starts PostgreSQL + Springboot backend

Backend API: http://localhost:3001

- Start the frontend
- cd frontend
- npm install
- npm run dev

Frontend: http://localhost:3000

### Project Structure
negotiator/
frontend/
backend/
docker-compose.yml
.env.example

### Environment Variables
- list all required env vars. Explain what each one does

### Tech Stack
- Backend: Java SpringBoot
- Frontend: React
- Database: PostgreSQL
- Docker
- API docs: Swagger / OpenAPi
- Build tools: Maven



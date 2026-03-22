# Negotiator Frontend

This frontend is the supplier-facing negotiation workspace for the Negotiator project.

It is built with Vite, React, Tailwind CSS 4, and a small shadcn-style component layer. The UI is intentionally structured-offer first: suppliers submit explicit commercial terms, and the app renders the real buyer response returned by the backend negotiation engine.

## What It Connects To

The frontend talks to the backend negotiation API:

- `GET /api/negotiations/config/defaults`
- `POST /api/negotiations/sessions`
- `GET /api/negotiations/sessions/{id}`
- `POST /api/negotiations/sessions/{id}/offers`

During local development, Vite proxies `/api` to `http://localhost:8080`.

If needed, you can override the API base with `VITE_API_BASE_URL`.

## Run Locally

Start the backend first, then run:

```bash
npm install
npm run dev
```

Default frontend URL:

```text
http://localhost:5173
```

## Available Scripts

- `npm run dev`: start the Vite development server
- `npm run build`: create a production build
- `npm run lint`: run ESLint
- `npm run preview`: preview the production build locally

## Current Product Shape

- The app is designed for the human supplier, not for the buyer.
- The transcript is backed by persisted negotiation rounds from the backend.
- The current interaction model is structured terms, not free-text negotiation chat.
- AI-generated buyer phrasing can be added later without changing the core session and offer contract.

## Related Documentation

- Root overview: `../README.md`
- Architecture: `../docs/architecture.md`
- Negotiation algorithm: `../docs/negotiation-engine.md`

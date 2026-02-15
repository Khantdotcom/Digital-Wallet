# Frontend Demo (Module 11)

A lightweight React client (no build step) that demonstrates:

- register/login with JWT token storage in `localStorage`
- wallet create/deposit/withdraw/transfer actions
- transaction history API integration

## Prerequisites

- Backend API running (default `http://localhost:8080`)
- A static file server for the `frontend/` folder

## Run

From the project root (`Digital-Wallet`):

```bash
python3 -m http.server 4173 -d frontend
```

Then open one of these frontend pages:

- `http://localhost:4173/`
- `http://localhost:4173/auth/login/`
- `http://localhost:4173/auth/register/`

Each page now mounts the same React app directly (no meta-refresh redirect).

## Frontend routes vs backend API endpoints

When using `python3 -m http.server`, URLs such as `/auth/login/` and `/auth/register/` are frontend static pages.

Backend auth API calls still go to your configured API base URL:

- `POST http://localhost:8080/auth/login`
- `POST http://localhost:8080/auth/register`

The frontend chooses which auth tab (login/register) to show based on the current page route.

## If `python3` is not installed

Use one of these alternatives:

### Node.js

```bash
npx serve frontend -l 4173
```

### PHP

```bash
cd frontend
php -S localhost:4173
```

## Backend port already in use

If port `8080` is taken:

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8081"
```

Then in the frontend, set **API Base URL** to `http://localhost:8081` and save it.

# Frontend Demo (Module 11)

A lightweight React client (no build step) that demonstrates:

- login with JWT token storage in `localStorage`
- wallet deposit action
- transaction history API integration

## Prerequisites

- Backend API running (default `http://localhost:8080`)
- A static file server for the `frontend/` folder

## Run (recommended)

From the project root (`Digital-Wallet`):

```bash
python3 -m http.server 4173 -d frontend
```

Then open:

- `http://localhost:4173`

> Note: many Linux distributions do **not** provide `python` command by default.
> Use `python3` instead.

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


## Avoiding `/auth/*` 404 logs on the static frontend server

When using `python3 -m http.server`, URLs like `/auth/login` and `/auth/register` are **frontend file paths**, not backend API endpoints.

- Use the UI (Login form) for auth requests, or call backend directly at your API base (e.g. `http://localhost:8080/auth/login`) with `POST`.
- We added redirect pages at `/auth/login` and `/auth/register` on the frontend server to route you back to `/` instead of showing a 404.

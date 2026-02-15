# Frontend Demo (Module 11)

A lightweight React client (no build step) that demonstrates:

- login with JWT token storage in `localStorage`
- wallet deposit action
- transaction history API integration

## Prerequisites

- Backend API running on `http://localhost:8080`
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

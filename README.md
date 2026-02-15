# Digital Wallet

## Quick Start

### 1) Start PostgreSQL

```bash
docker compose up -d db
```

### 2) Start backend

```bash
cd backend
mvn spring-boot:run
```

Backend API: `http://localhost:8080`

### 3) Start frontend demo

In a new terminal from repo root:

```bash
python3 -m http.server 4173 -d frontend
```

Open `http://localhost:4173`

## Troubleshooting

- If `cd /workspace/Digital-Wallet` fails on your local machine, use your **actual local path** to this repository.
- If `python` command is missing, use `python3`.
- If Maven cannot download dependencies, verify network/proxy and Maven Central access.


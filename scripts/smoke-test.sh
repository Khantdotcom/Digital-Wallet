#!/usr/bin/env bash
set -euo pipefail

API_BASE="${API_BASE:-http://localhost:8080}"
EMAIL="${SMOKE_EMAIL:-smoke-$(date +%s)@example.com}"
PASSWORD="${SMOKE_PASSWORD:-SmokeTest123!}"
WALLET_NAME="${SMOKE_WALLET_NAME:-Smoke Wallet}"
DEPOSIT_CENTS="${SMOKE_DEPOSIT_CENTS:-1000}"

echo "==> Health"
curl -sf "${API_BASE}/health" | grep -q '"status":"ok"'

echo "==> Readiness"
curl -sf "${API_BASE}/ready" | grep -q '"status":"ready"'

echo "==> Register"
REGISTER_RESPONSE="$(curl -sf -X POST "${API_BASE}/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\"}")"
TOKEN="$(echo "${REGISTER_RESPONSE}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
if [[ -z "${TOKEN}" ]]; then
  echo "Register failed: ${REGISTER_RESPONSE}" >&2
  exit 1
fi

echo "==> Create wallet"
WALLET_RESPONSE="$(curl -sf -X POST "${API_BASE}/wallets" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"${WALLET_NAME}\"}")"
WALLET_ID="$(echo "${WALLET_RESPONSE}" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')"
if [[ -z "${WALLET_ID}" ]]; then
  echo "Create wallet failed: ${WALLET_RESPONSE}" >&2
  exit 1
fi

echo "==> Deposit ${DEPOSIT_CENTS} cents"
DEPOSIT_RESPONSE="$(curl -sf -X POST "${API_BASE}/wallets/${WALLET_ID}/deposit" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"amount\":${DEPOSIT_CENTS}}")"
echo "${DEPOSIT_RESPONSE}" | grep -q "\"balance\":${DEPOSIT_CENTS}"

echo "Smoke test passed for ${EMAIL} (wallet ${WALLET_ID}, balance ${DEPOSIT_CENTS} cents)."

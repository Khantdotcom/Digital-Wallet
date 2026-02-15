import React, { useMemo, useState } from "https://esm.sh/react@18.3.1";

const DEFAULT_API_BASE = "http://localhost:8080";

export function App() {
  const [token, setToken] = useState("");
  const [auth, setAuth] = useState(() => localStorage.getItem("wallet_token") ?? "");
  const [apiBase, setApiBase] = useState(() => localStorage.getItem("wallet_api_base") ?? DEFAULT_API_BASE);
  const [walletId, setWalletId] = useState("");
  const [amount, setAmount] = useState("10");
  const [history, setHistory] = useState([]);
  const [status, setStatus] = useState("Ready");

  const headers = useMemo(
    () => ({
      "Content-Type": "application/json",
      ...(auth ? { Authorization: `Bearer ${auth}` } : {}),
    }),
    [auth],
  );

  function saveApiBase(value) {
    const normalized = value.trim().replace(/\/$/, "") || DEFAULT_API_BASE;
    localStorage.setItem("wallet_api_base", normalized);
    setApiBase(normalized);
    setStatus(`API base set to ${normalized}`);
  }

  async function login(e) {
    e.preventDefault();
    setStatus("Logging in...");

    const email = e.target.email.value;
    const password = e.target.password.value;

    const res = await fetch(`${apiBase}/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });

    if (!res.ok) {
      setStatus("Login failed");
      return;
    }

    const data = await res.json();
    localStorage.setItem("wallet_token", data.token);
    setAuth(data.token);
    setToken(data.token);
    setStatus("Logged in");
  }

  async function deposit() {
    const res = await fetch(`${apiBase}/wallets/${walletId}/deposit`, {
      method: "POST",
      headers,
      body: JSON.stringify({ amount: Number(amount), note: "UI deposit" }),
    });
    setStatus(res.ok ? "Deposit success" : "Deposit failed");
  }

  async function loadHistory() {
    const res = await fetch(`${apiBase}/wallets/${walletId}/transactions?page=0&size=10&sort=createdAt,desc`, {
      headers,
    });

    if (!res.ok) {
      setStatus("Failed to load transactions");
      return;
    }

    const data = await res.json();
    setHistory(data.content ?? []);
    setStatus("Transaction history updated");
  }

  return React.createElement(
    "main",
    { className: "container" },
    React.createElement("h1", null, "Digital Wallet Demo"),
    React.createElement(
      "section",
      { className: "card" },
      React.createElement("h2", null, "API Configuration"),
      React.createElement("input", {
        value: apiBase,
        onChange: (e) => setApiBase(e.target.value),
        placeholder: "API Base URL (e.g., http://localhost:8080)",
      }),
      React.createElement("button", { onClick: () => saveApiBase(apiBase) }, "Save API Base"),
    ),
    React.createElement(
      "form",
      { onSubmit: login, className: "card" },
      React.createElement("h2", null, "Login"),
      React.createElement("input", { name: "email", type: "email", placeholder: "Email", required: true }),
      React.createElement("input", { name: "password", type: "password", placeholder: "Password", required: true }),
      React.createElement("button", { type: "submit" }, "Login"),
    ),
    React.createElement(
      "section",
      { className: "card" },
      React.createElement("h2", null, "Wallet Operations"),
      React.createElement("input", {
        value: walletId,
        onChange: (e) => setWalletId(e.target.value),
        placeholder: "Wallet ID",
      }),
      React.createElement("input", {
        value: amount,
        onChange: (e) => setAmount(e.target.value),
        placeholder: "Amount",
        type: "number",
        min: "0.01",
        step: "0.01",
      }),
      React.createElement(
        "div",
        { className: "actions" },
        React.createElement("button", { onClick: deposit, disabled: !auth || !walletId }, "Deposit"),
        React.createElement("button", { onClick: loadHistory, disabled: !auth || !walletId }, "Load History"),
      ),
    ),
    React.createElement(
      "section",
      { className: "card" },
      React.createElement("h2", null, "Recent Transactions"),
      React.createElement(
        "ul",
        null,
        history.map((tx) =>
          React.createElement(
            "li",
            { key: tx.id },
            `${tx.type}: ${tx.amount} (${new Date(tx.createdAt).toLocaleString()})`,
          ),
        ),
      ),
    ),
    React.createElement("p", { className: "status" }, status),
    token && React.createElement("p", { className: "token" }, `Token: ${token.slice(0, 20)}...`),
  );
}

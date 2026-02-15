import React, { useEffect, useMemo, useState } from "https://esm.sh/react@18.3.1";

const DEFAULT_API_BASE = "http://localhost:8080";

export function App({ initialAuthView = "login" }) {
  const [authView, setAuthView] = useState(initialAuthView);
  const [auth, setAuth] = useState(() => localStorage.getItem("wallet_token") ?? "");
  const [tokenPreview, setTokenPreview] = useState(() => localStorage.getItem("wallet_token") ?? "");
  const [userEmail, setUserEmail] = useState(() => localStorage.getItem("wallet_user_email") ?? "");
  const [apiBaseInput, setApiBaseInput] = useState(() => localStorage.getItem("wallet_api_base") ?? DEFAULT_API_BASE);
  const [apiBase, setApiBase] = useState(() => localStorage.getItem("wallet_api_base") ?? DEFAULT_API_BASE);
  const [wallets, setWallets] = useState([]);
  const [selectedWalletId, setSelectedWalletId] = useState("");
  const [walletName, setWalletName] = useState("");
  const [amount, setAmount] = useState("10");
  const [transferTargetId, setTransferTargetId] = useState("");
  const [history, setHistory] = useState([]);
  const [status, setStatus] = useState("Ready");

  const headers = useMemo(
    () => ({
      "Content-Type": "application/json",
      ...(auth ? { Authorization: `Bearer ${auth}` } : {}),
    }),
    [auth],
  );

  useEffect(() => {
    if (!selectedWalletId && wallets.length > 0) {
      setSelectedWalletId(String(wallets[0].id));
    }
  }, [wallets, selectedWalletId]);

  function switchAuthView(nextView) {
    setAuthView(nextView);
    if (nextView === "register") {
      window.history.replaceState({}, "", "/auth/register/");
      return;
    }
    window.history.replaceState({}, "", "/auth/login/");
  }

  function saveApiBase() {
    const normalized = apiBaseInput.trim().replace(/\/$/, "") || DEFAULT_API_BASE;
    localStorage.setItem("wallet_api_base", normalized);
    setApiBase(normalized);
    setApiBaseInput(normalized);
    setStatus(`API base set to ${normalized}`);
  }

  async function submitAuth(e, mode) {
    e.preventDefault();
    setStatus(mode === "login" ? "Logging in..." : "Registering...");

    const email = e.target.email.value;
    const password = e.target.password.value;

    const res = await fetch(`${apiBase}/auth/${mode}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });

    if (!res.ok) {
      setStatus(`${mode === "login" ? "Login" : "Registration"} failed`);
      return;
    }

    const data = await res.json();
    localStorage.setItem("wallet_token", data.accessToken);
    localStorage.setItem("wallet_user_email", data.email ?? email);
    setAuth(data.accessToken);
    setTokenPreview(data.accessToken);
    setUserEmail(data.email ?? email);
    setStatus(mode === "login" ? "Logged in" : "Registered and logged in");
    window.history.replaceState({}, "", "/");
    await loadWallets(data.accessToken);
  }

  async function loadWallets(overrideToken = "") {
    const tokenToUse = overrideToken || auth;
    const res = await fetch(`${apiBase}/wallets`, {
      headers: {
        "Content-Type": "application/json",
        ...(tokenToUse ? { Authorization: `Bearer ${tokenToUse}` } : {}),
      },
    });

    if (!res.ok) {
      setStatus("Failed to load wallets");
      return;
    }

    const data = await res.json();
    setWallets(data);
    setStatus("Wallets refreshed");
  }

  async function createWallet() {
    if (!walletName.trim()) {
      setStatus("Wallet name is required");
      return;
    }

    const res = await fetch(`${apiBase}/wallets`, {
      method: "POST",
      headers,
      body: JSON.stringify({ name: walletName.trim() }),
    });

    if (!res.ok) {
      setStatus("Create wallet failed");
      return;
    }

    setWalletName("");
    setStatus("Wallet created");
    await loadWallets();
  }

  async function moneyAction(type) {
    if (!selectedWalletId) {
      setStatus("Select a wallet first");
      return;
    }

    const res = await fetch(`${apiBase}/wallets/${selectedWalletId}/${type}`, {
      method: "POST",
      headers,
      body: JSON.stringify({ amount: Number(amount), note: `UI ${type}` }),
    });

    if (!res.ok) {
      setStatus(`${type} failed`);
      return;
    }

    setStatus(`${type} success`);
    await loadWallets();
    await loadHistory();
  }

  async function transfer() {
    if (!selectedWalletId || !transferTargetId) {
      setStatus("Source and target wallet IDs are required");
      return;
    }

    const res = await fetch(`${apiBase}/wallets/transfer`, {
      method: "POST",
      headers,
      body: JSON.stringify({
        sourceWalletId: Number(selectedWalletId),
        targetWalletId: Number(transferTargetId),
        amount: Number(amount),
        note: "UI transfer",
      }),
    });

    if (!res.ok) {
      setStatus("Transfer failed");
      return;
    }

    setStatus("Transfer success");
    await loadWallets();
    await loadHistory();
  }

  async function loadHistory() {
    if (!selectedWalletId) {
      setStatus("Select a wallet first");
      return;
    }

    const res = await fetch(`${apiBase}/wallets/${selectedWalletId}/transactions?page=0&size=10&sort=createdAt,desc`, {
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

  function logout() {
    localStorage.removeItem("wallet_token");
    localStorage.removeItem("wallet_user_email");
    setAuth("");
    setTokenPreview("");
    setUserEmail("");
    setWallets([]);
    setHistory([]);
    setSelectedWalletId("");
    setStatus("Logged out");
    switchAuthView("login");
  }

  const selectedWallet = wallets.find((wallet) => String(wallet.id) === String(selectedWalletId));

  return React.createElement(
    "main",
    { className: "container" },
    React.createElement(
      "header",
      { className: "hero" },
      React.createElement("p", { className: "hero-kicker" }, "Digital Wallet Demo"),
      React.createElement("h1", null, "Manage balances with a cleaner workflow"),
      React.createElement("p", null, "Auth, wallet operations, transfer, and history are all available from this page."),
    ),
    React.createElement(
      "section",
      { className: "card" },
      React.createElement("h2", null, "API Configuration"),
      React.createElement("input", {
        value: apiBaseInput,
        onChange: (e) => setApiBaseInput(e.target.value),
        placeholder: "API Base URL (e.g., http://localhost:8080)",
      }),
      React.createElement("button", { onClick: saveApiBase }, "Save API Base"),
    ),
    !auth &&
      React.createElement(
        "section",
        { className: "card" },
        React.createElement(
          "div",
          { className: "tabs" },
          React.createElement(
            "button",
            { className: authView === "login" ? "tab active" : "tab", onClick: () => switchAuthView("login") },
            "Login",
          ),
          React.createElement(
            "button",
            { className: authView === "register" ? "tab active" : "tab", onClick: () => switchAuthView("register") },
            "Register",
          ),
        ),
        React.createElement(
          "form",
          { onSubmit: (e) => submitAuth(e, authView), className: "auth-form" },
          React.createElement("h2", null, authView === "login" ? "Welcome back" : "Create account"),
          React.createElement("input", { name: "email", type: "email", placeholder: "Email", required: true }),
          React.createElement("input", { name: "password", type: "password", placeholder: "Password", required: true }),
          React.createElement("button", { type: "submit" }, authView === "login" ? "Login" : "Register"),
        ),
      ),
    auth &&
      React.createElement(
        React.Fragment,
        null,
        React.createElement(
          "section",
          { className: "card" },
          React.createElement("h2", null, "Wallet State"),
          React.createElement("p", null, `Signed in as ${userEmail || "current user"}`),
          React.createElement(
            "div",
            { className: "actions" },
            React.createElement("button", { onClick: loadWallets }, "Refresh Wallets"),
            React.createElement("button", { onClick: logout, className: "secondary" }, "Logout"),
          ),
          selectedWallet
            ? React.createElement("p", { className: "balance-highlight" }, `Selected balance: ${selectedWallet.balance}`)
            : React.createElement("p", { className: "muted" }, "No wallet selected yet."),
          React.createElement(
            "div",
            { className: "wallet-list" },
            wallets.map((wallet) =>
              React.createElement(
                "button",
                {
                  key: wallet.id,
                  className: String(wallet.id) === String(selectedWalletId) ? "wallet-item active" : "wallet-item",
                  onClick: () => setSelectedWalletId(String(wallet.id)),
                },
                `${wallet.name} (#${wallet.id}) — ${wallet.balance}`,
              ),
            ),
          ),
        ),
        React.createElement(
          "section",
          { className: "card" },
          React.createElement("h2", null, "Wallet Actions"),
          React.createElement(
            "div",
            { className: "split" },
            React.createElement("input", {
              value: walletName,
              onChange: (e) => setWalletName(e.target.value),
              placeholder: "New wallet name",
            }),
            React.createElement("button", { onClick: createWallet }, "Create Wallet"),
          ),
          React.createElement(
            "div",
            { className: "split" },
            React.createElement("input", {
              value: amount,
              onChange: (e) => setAmount(e.target.value),
              type: "number",
              min: "0.01",
              step: "0.01",
              placeholder: "Amount",
            }),
            React.createElement(
              "div",
              { className: "actions" },
              React.createElement("button", { onClick: () => moneyAction("deposit"), disabled: !selectedWalletId }, "Deposit"),
              React.createElement("button", { onClick: () => moneyAction("withdraw"), disabled: !selectedWalletId }, "Withdraw"),
            ),
          ),
          React.createElement(
            "div",
            { className: "split" },
            React.createElement("input", {
              value: transferTargetId,
              onChange: (e) => setTransferTargetId(e.target.value),
              placeholder: "Target wallet ID",
              type: "number",
            }),
            React.createElement(
              "div",
              { className: "actions" },
              React.createElement("button", { onClick: transfer, disabled: !selectedWalletId }, "Transfer"),
              React.createElement("button", { onClick: loadHistory, disabled: !selectedWalletId }, "Load History"),
            ),
          ),
        ),
        React.createElement(
          "section",
          { className: "card" },
          React.createElement("h2", null, "Recent Transactions"),
          React.createElement(
            "ul",
            { className: "tx-list" },
            history.map((tx) =>
              React.createElement(
                "li",
                { key: tx.id },
                `${tx.type}: ${tx.amount} (${new Date(tx.createdAt).toLocaleString()})`,
              ),
            ),
          ),
        ),
      ),
    React.createElement("p", { className: "status" }, status),
    tokenPreview && React.createElement("p", { className: "token" }, `Token: ${tokenPreview.slice(0, 25)}...`),
  );
}

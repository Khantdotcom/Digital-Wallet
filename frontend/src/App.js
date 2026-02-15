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
  const [activePage, setActivePage] = useState(() => window.location.hash.replace("#banking-", "") || "overview");
  const [isSubmittingAuth, setIsSubmittingAuth] = useState(false);

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

  function switchBankingPage(nextPage) {
    setActivePage(nextPage);
    window.location.hash = `banking-${nextPage}`;
  }

  async function requestJson(url, options = {}, fallbackMessage = "Request failed") {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 12000);

    try {
      const res = await fetch(url, { ...options, signal: controller.signal });
      const text = await res.text();
      let data = null;
      if (text) {
        try {
          data = JSON.parse(text);
        } catch {
          if (res.ok) {
            throw new Error("Received an invalid API response. Please check backend logs.");
          }
        }
      }

      if (!res.ok) {
        const message = data?.message || data?.error || fallbackMessage;
        throw new Error(message);
      }

      return data;
    } catch (error) {
      if (error.name === "AbortError") {
        throw new Error("Request timed out. Check API base URL and backend status.");
      }
      if (error.name === "TypeError") {
        throw new Error(`Unable to reach API at ${apiBase}. Check API Base URL, backend status, and CORS settings.`);
      }
      throw error;
    } finally {
      window.clearTimeout(timeout);
    }
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
    if (isSubmittingAuth) {
      return;
    }

    setIsSubmittingAuth(true);
    setStatus(mode === "login" ? "Logging in..." : "Registering...");

    const email = e.target.email.value;
    const password = e.target.password.value;

    try {
      const data = await requestJson(
        `${apiBase}/auth/${mode}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email, password }),
        },
        `${mode === "login" ? "Login" : "Registration"} failed`,
      );

      localStorage.setItem("wallet_token", data.accessToken);
      localStorage.setItem("wallet_user_email", data.email ?? email);
      setAuth(data.accessToken);
      setTokenPreview(data.accessToken);
      setUserEmail(data.email ?? email);
      setStatus(mode === "login" ? "Logged in" : "Registered and logged in");
      window.history.replaceState({}, "", "/");
      await loadWallets(data.accessToken);
    } catch (error) {
      setStatus(error.message || `${mode === "login" ? "Login" : "Registration"} failed`);
    } finally {
      setIsSubmittingAuth(false);
    }
  }

  async function loadWallets(overrideToken = "") {
    const tokenToUse = overrideToken || auth;
    try {
      const data = await requestJson(
        `${apiBase}/wallets`,
        {
          headers: {
            "Content-Type": "application/json",
            ...(tokenToUse ? { Authorization: `Bearer ${tokenToUse}` } : {}),
          },
        },
        "Failed to load wallets",
      );
      setWallets(data ?? []);
      setStatus("Wallets refreshed");
    } catch (error) {
      setStatus(error.message || "Failed to load wallets");
    }
  }

  async function createWallet() {
    if (!walletName.trim()) {
      setStatus("Wallet name is required");
      return;
    }

    try {
      await requestJson(
        `${apiBase}/wallets`,
        {
          method: "POST",
          headers,
          body: JSON.stringify({ name: walletName.trim() }),
        },
        "Create wallet failed",
      );

      setWalletName("");
      setStatus("Wallet created");
      await loadWallets();
    } catch (error) {
      setStatus(error.message || "Create wallet failed");
    }
  }

  async function moneyAction(type) {
    if (!selectedWalletId) {
      setStatus("Select a wallet first");
      return;
    }

    try {
      await requestJson(
        `${apiBase}/wallets/${selectedWalletId}/${type}`,
        {
          method: "POST",
          headers,
          body: JSON.stringify({ amount: Number(amount), note: `UI ${type}` }),
        },
        `${type} failed`,
      );

      setStatus(`${type} success`);
      await loadWallets();
      await loadHistory();
    } catch (error) {
      setStatus(error.message || `${type} failed`);
    }
  }

  async function transfer() {
    if (!selectedWalletId || !transferTargetId) {
      setStatus("Source and target wallet IDs are required");
      return;
    }

    try {
      await requestJson(
        `${apiBase}/wallets/transfer`,
        {
          method: "POST",
          headers,
          body: JSON.stringify({
            sourceWalletId: Number(selectedWalletId),
            targetWalletId: Number(transferTargetId),
            amount: Number(amount),
            note: "UI transfer",
          }),
        },
        "Transfer failed",
      );

      setStatus("Transfer success");
      await loadWallets();
      await loadHistory();
    } catch (error) {
      setStatus(error.message || "Transfer failed");
    }
  }

  async function loadHistory() {
    if (!selectedWalletId) {
      setStatus("Select a wallet first");
      return;
    }

    try {
      const data = await requestJson(
        `${apiBase}/wallets/${selectedWalletId}/transactions?page=0&size=10&sort=createdAt,desc`,
        { headers },
        "Failed to load transactions",
      );
      setHistory(data?.content ?? []);
      setStatus("Transaction history updated");
    } catch (error) {
      setStatus(error.message || "Failed to load transactions");
    }
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
    setActivePage("overview");
    setStatus("Logged out");
    switchAuthView("login");
  }

  const selectedWallet = wallets.find((wallet) => String(wallet.id) === String(selectedWalletId));
  const bankingPages = [
    { id: "overview", label: "Overview" },
    { id: "wallets", label: "Wallets" },
    { id: "transfer", label: "Transfers" },
    { id: "history", label: "History" },
  ];

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
          React.createElement(
            "button",
            { type: "submit", disabled: isSubmittingAuth },
            isSubmittingAuth ? "Please wait..." : authView === "login" ? "Login" : "Register",
          ),
        ),
      ),
    auth &&
      React.createElement(
        React.Fragment,
        null,
        React.createElement(
          "section",
          { className: "card" },
          React.createElement("h2", null, "Banking Pages"),
          React.createElement(
            "div",
            { className: "tabs" },
            bankingPages.map((page) =>
              React.createElement(
                "button",
                {
                  key: page.id,
                  className: activePage === page.id ? "tab active" : "tab",
                  onClick: () => switchBankingPage(page.id),
                },
                page.label,
              ),
            ),
          ),
        ),
        (activePage === "overview" || activePage === "wallets") &&
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
        (activePage === "overview" || activePage === "wallets" || activePage === "transfer") &&
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
        (activePage === "overview" || activePage === "history") &&
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

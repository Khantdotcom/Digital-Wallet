import React from "https://esm.sh/react@18.3.1";
import { createRoot } from "https://esm.sh/react-dom@18.3.1/client";
import { App } from "./App.js";
import "./styles.css";

function getInitialAuthView() {
  const path = window.location.pathname.replace(/\/+$/, "");
  if (path === "/auth/register") {
    return "register";
  }
  if (path === "/auth/login") {
    return "login";
  }
  return "login";
}

createRoot(document.getElementById("root")).render(
  React.createElement(App, { initialAuthView: getInitialAuthView() }),
);

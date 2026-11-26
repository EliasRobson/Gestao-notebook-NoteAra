const API = "http://localhost:8080/api";
const form = document.getElementById("loginForm");
const message = document.getElementById("loginMessage");

function showMessage(text) {
  message.textContent = text;
  message.hidden = !text;
}

async function api(path, options = {}) {
  const response = await fetch(`${API}${path}`, {
    ...options,
    headers: { "Content-Type": "application/json", ...(options.headers || {}) }
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.detail || data.message || "Não foi possível conectar ao sistema.");
  return data;
}

form.addEventListener("submit", async event => {
  event.preventDefault();
  const button = form.querySelector("button[type='submit']");
  button.disabled = true;
  showMessage("");

  try {
    const result = await api("/auth/login", {
      method: "POST",
      body: JSON.stringify({
        email: document.getElementById("loginEmail").value.trim(),
        password: document.getElementById("loginPassword").value
      })
    });
    sessionStorage.setItem("noteAraAccessToken", result.accessToken);
    window.location.replace("admin.html");
  } catch (error) {
    showMessage(error.message);
  } finally {
    button.disabled = false;
  }
});

const savedToken = sessionStorage.getItem("noteAraAccessToken");
if (savedToken) {
  api("/auth/me", { headers: { Authorization: `Bearer ${savedToken}` } })
    .then(() => window.location.replace("admin.html"))
    .catch(() => sessionStorage.removeItem("noteAraAccessToken"));
}

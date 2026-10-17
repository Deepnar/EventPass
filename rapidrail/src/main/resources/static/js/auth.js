// LOGIN + REGISTER page
// NOTE: common.js already defines `const API = location.origin + '/api'`
// We must NOT redeclare it – just use it with the right path.

// Already logged in? -> jump to dashboard
if (localStorage.getItem("userId")) {
    window.location.href = "dashboard.html";
}

function toggleAuth() {
    const login = document.getElementById("loginSection");
    const reg = document.getElementById("registerSection");
    if (login.style.display === "none") {
        login.style.display = "block";
        reg.style.display = "none";
    } else {
        login.style.display = "none";
        reg.style.display = "block";
    }
}

document.getElementById("loginForm").addEventListener("submit", async (e) => {
    e.preventDefault();
    const username = document.getElementById("loginUser").value.trim();
    const password = document.getElementById("loginPass").value;
    try {
        const res = await fetch(`${API}/auth/login`, {   // <-- changed
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username, password })
        });
        const data = await res.json();
        if (res.ok) {
            localStorage.setItem("userId", data.id);
            localStorage.setItem("username", data.username);
            window.location.href = "dashboard.html";
        } else {
            alert(data.error || "Invalid credentials");
        }
    } catch (err) {
        alert("Server not reachable. Is the backend running on port 8080?");
    }
});

document.getElementById("registerForm").addEventListener("submit", async (e) => {
    e.preventDefault();
    const username = document.getElementById("regUser").value.trim();
    const password = document.getElementById("regPass").value;
    const email = document.getElementById("regEmail").value.trim();
    try {
        const res = await fetch(`${API}/auth/register`, {   // <-- changed
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username, password, email })
        });
        const data = await res.json();
        if (res.ok) {
            // Auto‑login after registration
            localStorage.setItem("userId", data.id);
            localStorage.setItem("username", data.username);
            window.location.href = "dashboard.html";
        } else {
            alert("Error: " + (data.error || "Registration failed"));
        }
    } catch (err) {
        alert("Server not reachable.");
    }
});
// Shared helpers used by every page
const API = `${window.location.origin}/api`;

function logout() {
    localStorage.removeItem("userId");
    localStorage.removeItem("username");
    window.location.href = "index.html";
}

function organizerLogout() {
    localStorage.removeItem("organizerId");
    localStorage.removeItem("organizerName");
    localStorage.removeItem("organizerCode");
    window.location.reload();
}

function organizerHeaders() {
    const id = localStorage.getItem("organizerId");
    const code = localStorage.getItem("organizerCode");
    if (!id || !code) return null;
    return { "X-Organizer-Id": id, "X-Organizer-Code": code };
}

function showAlert(msg, type = "info") {
    const div = document.createElement("div");
    div.className = `alert alert-${type}`;
    div.style.cssText = "position:fixed;top:20px;right:20px;z-index:9999;min-width:280px;";
    div.innerHTML = `<div style="display:flex;justify-content:space-between;align-items:center;">
        <span>${msg}</span>
        <button onclick="this.parentElement.parentElement.remove()" style="background:none;border:none;font-size:1.2rem;cursor:pointer;">&times;</button>
    </div>`;
    document.body.appendChild(div);
    setTimeout(() => div.remove(), 5000);
}

function requireLogin() {
    const userId = localStorage.getItem("userId");
    if (!userId) {
        window.location.href = "index.html";
        return null;
    }
    return userId;
}

function formatDateTime(iso) {
    if (!iso) return "";
    const d = new Date(iso);
    return d.toLocaleString();
}

function formatDate(iso) {
    if (!iso) return "";
    return new Date(iso).toLocaleDateString();
}

function escapeHtml(s) {
    if (s == null) return "";
    return String(s)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

function money(n) {
    if (n == null) return "0.00";
    return Number(n).toFixed(2);
}

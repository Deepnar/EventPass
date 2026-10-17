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

// Inline SVG icons (stroke style, avoids emoji-as-icon)
const ICONS = {
    ticket: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 9V7a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v2a2.5 2.5 0 0 0 0 5v2a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-2a2.5 2.5 0 0 0 0-5z"/><path d="M13 5v2m0 4v2m0 4v2"/></svg>',
    calendar: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/></svg>',
    pin: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0z"/><circle cx="12" cy="10" r="3"/></svg>',
    tent: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3 2 20h20L12 3z"/><path d="M12 12v8"/></svg>',
    wallet: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M20 7H5a2 2 0 0 1 0-4h14v4"/><path d="M20 7a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5"/><circle cx="17" cy="14" r="1" fill="currentColor"/></svg>',
    check: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>',
    clock: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>'
};

// Poster block: real image on top, gradient + initial underneath as fallback.
function posterHtml(ev) {
    const letter = escapeHtml(((ev && ev.title) || "?").charAt(0).toUpperCase());
    const title = escapeHtml((ev && ev.title) || "Event");
    const img = ev && ev.imageUrl
        ? `<img src="${escapeHtml(ev.imageUrl)}" alt="${title} poster" loading="lazy" onerror="this.remove()">`
        : "";
    return `<div class="poster" aria-hidden="true"><span class="poster-letter">${letter}</span>${img}</div>`;
}

function seatNoteHtml(ev) {
    const left = ev.availableSeats || 0;
    const past = ev.eventDateTime && new Date(ev.eventDateTime) < new Date();
    if (past) return `<div class="seat-note out">Event ended</div>`;
    if (left <= 0) return `<div class="seat-note out">Sold out</div>`;
    if (left <= 10) return `<div class="seat-note low">Almost full — only ${left} left</div>`;
    return `<div class="seat-note ok">${left} seats left</div>`;
}

// ORGANIZER panel logic (server-enforced organizer auth; no hard-coded PIN)
let myEvents = [];

document.addEventListener("DOMContentLoaded", () => {
    const orgId = localStorage.getItem("organizerId");
    const code = localStorage.getItem("organizerCode");
    if (orgId && code) {
        showPanel();
    } else {
        document.getElementById("loginOverlay").style.display = "flex";
    }
    document.getElementById("eventForm").addEventListener("submit", createEvent);
});

function showPanel() {
    document.getElementById("loginOverlay").style.display = "none";
    document.getElementById("organizerPanel").style.display = "block";
    document.getElementById("orgBadge").innerText =
        `${localStorage.getItem("organizerName") || ""} (#${localStorage.getItem("organizerId")})`;
    loadAll();
}

async function organizerLogin() {
    const email = document.getElementById("orgEmail").value.trim();
    const code = document.getElementById("orgCode").value;
    if (!email || !code) return alert("Email and access code are required");
    try {
        const listRes = await fetch(`${API}/organizers`);
        const orgs = await listRes.json();
        const org = orgs.find(o => (o.email || "").toLowerCase() === email.toLowerCase());
        if (!org) {
            document.getElementById("loginError").style.display = "block";
            return;
        }
        const res = await fetch(`${API}/organizers/${org.id}/verify`, {
            method: "POST", headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ accessCode: code })
        });
        if (!res.ok) {
            document.getElementById("loginError").style.display = "block";
            return;
        }
        const verified = await res.json();
        localStorage.setItem("organizerId", verified.id);
        localStorage.setItem("organizerName", verified.organizationName || verified.name);
        localStorage.setItem("organizerCode", code);
        document.getElementById("loginError").style.display = "none";
        showPanel();
    } catch (e) { alert("Server not reachable."); }
}

async function organizerRegister() {
    const name = document.getElementById("newName").value.trim();
    const org = document.getElementById("newOrg").value.trim();
    const email = document.getElementById("newEmail").value.trim();
    const code = document.getElementById("newCode").value;
    if (!name || !org || !email || !code) return alert("All organizer fields are required");
    const res = await fetch(`${API}/organizers`, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name, organizationName: org, email, accessCode: code })
    });
    const data = await res.json();
    if (!res.ok) return alert("Registration failed: " + (data.error || "?"));
    localStorage.setItem("organizerId", data.id);
    localStorage.setItem("organizerName", data.organizationName);
    localStorage.setItem("organizerCode", code);
    showAlert("Organizer registered!", "success");
    showPanel();
}

function switchTab(tab) {
    document.querySelectorAll(".tab-bar button").forEach(b => b.classList.remove("active"));
    document.querySelector(`[data-tab="${tab}"]`).classList.add("active");
    ["events", "checkin", "bookings", "tickets", "history", "organizers"].forEach(t => {
        document.getElementById(t + "Tab").style.display = (t === tab) ? "block" : "none";
    });
    if (tab === "bookings") loadEventBookings();
    if (tab === "tickets") loadEventTickets();
    if (tab === "history") loadHistory();
    if (tab === "organizers") loadOrganizers();
}

function loadAll() {
    loadEvents();
    loadOrganizers();
}

function authHeaders() {
    const h = organizerHeaders();
    return { "Content-Type": "application/json", ...(h || {}) };
}

// ---------- EVENTS ----------
async function loadEvents() {
    const orgId = localStorage.getItem("organizerId");
    const res = await fetch(`${API}/events?organizerId=${orgId}`);
    myEvents = await res.json();
    myEvents.sort((a, b) => new Date(a.eventDateTime) - new Date(b.eventDateTime));

    document.getElementById("eventRows").innerHTML = myEvents.map(ev => `
        <tr>
            <td>${ev.id}</td>
            <td>${escapeHtml(ev.title)}</td>
            <td>${escapeHtml(ev.category)}</td>
            <td>${escapeHtml(ev.venue)}</td>
            <td>${formatDateTime(ev.eventDateTime)}</td>
            <td>₹${money(ev.ticketPrice)}</td>
            <td>${ev.availableSeats}/${ev.totalCapacity}</td>
            <td style="white-space:nowrap;">
                <button class="btn-secondary-custom" style="padding:4px 10px;font-size:0.85rem;" onclick="editEvent(${ev.id})">Edit</button>
                <button class="btn-danger-custom" style="padding:4px 10px;font-size:0.85rem;" onclick="deleteEvent(${ev.id})">Delete</button>
            </td>
        </tr>
    `).join("") || '<tr><td colspan="8" class="text-muted">No events yet. Add your first event above.</td></tr>';

    const opts = myEvents.map(ev => `<option value="${ev.id}">${escapeHtml(ev.title)} (#${ev.id})</option>`).join("");
    ["checkinEvent", "bookingEvent", "ticketEvent", "historyEvent"].forEach(id => {
        const sel = document.getElementById(id);
        sel.innerHTML = opts || '<option value="">No events</option>';
    });
    if (myEvents.length) { loadEventBookings(); loadEventTickets(); loadHistory(); }
}

async function createEvent(e) {
    e.preventDefault();
    const body = {
        title: document.getElementById("eTitle").value,
        category: document.getElementById("eCategory").value,
        venue: document.getElementById("eVenue").value,
        eventDateTime: document.getElementById("eTime").value,
        ticketPrice: parseFloat(document.getElementById("ePrice").value),
        totalCapacity: parseInt(document.getElementById("eCapacity").value),
        description: document.getElementById("eDesc").value
    };
    const res = await fetch(`${API}/events`, { method: "POST", headers: authHeaders(), body: JSON.stringify(body) });
    if (res.ok) { e.target.reset(); loadEvents(); showAlert("Event created", "success"); }
    else { const err = await res.json(); alert("Error: " + (err.error || "?")); }
}

async function editEvent(id) {
    const ev = myEvents.find(x => x.id === id);
    if (!ev) return;
    const title = prompt("Title:", ev.title);
    if (title === null) return;
    const venue = prompt("Venue:", ev.venue);
    if (venue === null) return;
    const price = prompt("Ticket price (₹):", ev.ticketPrice);
    if (price === null) return;
    const capacity = prompt("Total capacity:", ev.totalCapacity);
    if (capacity === null) return;
    const res = await fetch(`${API}/events/${id}`, {
        method: "PUT", headers: authHeaders(),
        body: JSON.stringify({ title, venue, ticketPrice: parseFloat(price), totalCapacity: parseInt(capacity) })
    });
    if (res.ok) { loadEvents(); showAlert("Event updated", "success"); }
    else { const err = await res.json(); alert("Update failed: " + (err.error || "?")); }
}

async function deleteEvent(id) {
    if (!confirm("Delete this event? Blocked while CONFIRMED bookings exist.")) return;
    const res = await fetch(`${API}/events/${id}`, { method: "DELETE", headers: authHeaders() });
    if (res.ok) { loadEvents(); showAlert("Event deleted", "success"); }
    else { const err = await res.json(); alert("Delete failed: " + (err.error || "?")); }
}

// ---------- CHECK-IN ----------
function selectedEvent(id) {
    const sel = document.getElementById(id);
    return sel && sel.value ? sel.value : null;
}

async function doCheckIn() {
    const eventId = selectedEvent("checkinEvent");
    const qrToken = document.getElementById("checkinInput").value.trim();
    if (!eventId) return alert("Create an event first");
    if (!qrToken) return alert("Paste a QR token");
    const box = document.getElementById("checkinResult");
    box.innerHTML = '<div class="alert alert-info">Checking...</div>';
    try {
        const res = await fetch(`${API}/checkin`, {
            method: "POST", headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                qrToken, eventId: parseInt(eventId),
                organizerId: parseInt(localStorage.getItem("organizerId")),
                organizerCode: localStorage.getItem("organizerCode")
            })
        });
        const data = await res.json();
        renderCheckInResult(data.result || (res.ok ? "VALID" : "INVALID"), data, false);
        loadHistory();
    } catch (e) {
        box.innerHTML = '<div class="alert alert-danger">Network error</div>';
    }
}

async function previewToken() {
    const eventId = selectedEvent("checkinEvent");
    const qrToken = document.getElementById("checkinInput").value.trim();
    if (!qrToken) return alert("Paste a QR token");
    const box = document.getElementById("checkinResult");
    try {
        const url = `${API}/checkin/preview?qrToken=${encodeURIComponent(qrToken)}${eventId ? `&eventId=${eventId}` : ""}`;
        const res = await fetch(url);
        const data = await res.json();
        renderCheckInResult(data.result || "INVALID", data, true);
    } catch (e) {
        box.innerHTML = '<div class="alert alert-danger">Network error</div>';
    }
}

function renderCheckInResult(result, data, isPreview) {
    const box = document.getElementById("checkinResult");
    const tag = isPreview ? " (preview — not consumed)" : "";
    const styles = {
        VALID: ["result-valid", "✅ VALID — Entry permitted"],
        ALREADY_USED: ["result-used", "⚠️ ALREADY USED — Entry denied"],
        CANCELLED: ["result-denied", "⛔ CANCELLED — Entry denied"],
        WRONG_EVENT: ["result-denied", "🚫 WRONG EVENT — Entry denied"],
        INVALID: ["result-denied", "❌ INVALID — Entry denied"]
    };
    const [cls, title] = styles[result] || styles.INVALID;
    box.innerHTML = `<div class="alert ${cls}">
        <strong>${title}${tag}</strong><br>
        ${data.message ? escapeHtml(data.message) + "<br>" : ""}
        ${data.checkIn ? `Checked in at ${formatDateTime(data.checkIn.checkedInAt)}<br>` : ""}
    </div>`;
}

// ---------- BOOKINGS / TICKETS / HISTORY ----------
async function loadEventBookings() {
    const eventId = selectedEvent("bookingEvent");
    if (!eventId) return;
    const res = await fetch(`${API}/bookings/event/${eventId}`);
    const bookings = await res.json();
    const div = document.getElementById("eventBookings");
    if (!bookings.length) { div.innerHTML = '<p class="text-muted">No bookings for this event yet.</p>'; return; }
    div.innerHTML = `<table><thead><tr><th>Booking</th><th>User</th><th>Qty</th><th>Amount</th><th>Time</th><th>Status</th></tr></thead>
        <tbody>${bookings.map(b => `<tr>
            <td><code style="font-size:0.75rem;">${b.bookingId.substring(0, 8)}…</code></td>
            <td>${escapeHtml(b.user ? b.user.username : "?")}</td>
            <td>${b.quantity}</td><td>₹${money(b.totalAmount)}</td>
            <td>${formatDateTime(b.bookingTime)}</td><td>${b.status}</td>
        </tr>`).join("")}</tbody></table>`;
}

async function loadEventTickets() {
    const eventId = selectedEvent("ticketEvent");
    if (!eventId) return;
    const res = await fetch(`${API}/tickets/event/${eventId}`);
    const tickets = await res.json();
    const div = document.getElementById("eventTickets");
    if (!tickets.length) { div.innerHTML = '<p class="text-muted">No tickets issued for this event yet.</p>'; return; }
    div.innerHTML = `<table><thead><tr><th>Ticket</th><th>QR Token</th><th>Booking</th><th>Status</th></tr></thead>
        <tbody>${tickets.map(t => `<tr>
            <td><code style="font-size:0.75rem;">${t.ticketId.substring(0, 8)}…</code></td>
            <td><code style="font-size:0.75rem;">${escapeHtml((t.qrToken || "").substring(0, 18))}…</code></td>
            <td><code style="font-size:0.75rem;">${(t.booking ? t.booking.bookingId : "").substring(0, 8)}…</code></td>
            <td>${t.status}</td>
        </tr>`).join("")}</tbody></table>`;
}

async function loadHistory() {
    const eventId = selectedEvent("historyEvent");
    if (!eventId) return;
    const res = await fetch(`${API}/checkin/event/${eventId}`);
    const rows = await res.json();
    const div = document.getElementById("checkinHistory");
    if (!rows.length) { div.innerHTML = '<p class="text-muted">No check-ins recorded for this event yet.</p>'; return; }
    div.innerHTML = `<table><thead><tr><th>#</th><th>Ticket</th><th>Checked In At</th></tr></thead>
        <tbody>${rows.map(r => `<tr>
            <td>${r.id}</td>
            <td><code style="font-size:0.75rem;">${escapeHtml(r.ticket ? r.ticket.qrToken : "?")}</code></td>
            <td>${formatDateTime(r.checkedInAt)}</td>
        </tr>`).join("")}</tbody></table>`;
}

// ---------- ORGANIZERS ----------
async function loadOrganizers() {
    const res = await fetch(`${API}/organizers`);
    const orgs = await res.json();
    const me = localStorage.getItem("organizerId");
    document.getElementById("organizerRows").innerHTML = orgs.map(o => `
        <tr>
            <td>${o.id}</td>
            <td>${escapeHtml(o.name)}</td>
            <td>${escapeHtml(o.email)}</td>
            <td>${escapeHtml(o.organizationName)}</td>
            <td>${String(o.id) === String(me)
                ? `<button class="btn-secondary-custom" style="padding:4px 10px;font-size:0.85rem;" onclick="editMyProfile(${o.id})">Edit</button>
                   <button class="btn-danger-custom" style="padding:4px 10px;font-size:0.85rem;" onclick="deleteMyProfile(${o.id})">Delete</button>`
                : '<span class="text-muted" style="font-size:0.8rem;">other organizer</span>'}</td>
        </tr>
    `).join("");
}

async function editMyProfile(id) {
    const name = prompt("Your name:");
    if (name === null) return;
    const org = prompt("Organization name:");
    if (org === null) return;
    const res = await fetch(`${API}/organizers/${id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json", "X-Organizer-Code": localStorage.getItem("organizerCode") },
        body: JSON.stringify({ name, organizationName: org })
    });
    if (res.ok) { loadOrganizers(); showAlert("Profile updated", "success"); }
    else { const err = await res.json(); alert("Update failed: " + (err.error || "?")); }
}

async function deleteMyProfile(id) {
    if (!confirm("Delete your organizer account? Blocked while you own events.")) return;
    const res = await fetch(`${API}/organizers/${id}`, {
        method: "DELETE",
        headers: { "X-Organizer-Code": localStorage.getItem("organizerCode") }
    });
    if (res.ok) { organizerLogout(); }
    else { const err = await res.json(); alert("Delete failed: " + (err.error || "?")); }
}

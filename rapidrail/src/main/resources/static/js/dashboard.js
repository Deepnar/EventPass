// ATTENDEE dashboard: browse events, book quantity, my bookings, wallet
const userId = requireLogin();

let allEvents = [];
let recommended = [];
let activeCategory = "";
let currentEvent = null;

if (!userId) {
    // requireLogin already redirected
} else {
    document.getElementById("welcomeUser").innerText = "Hi, " + (localStorage.getItem("username") || "user");
    updateWallet();
    loadEvents();
    loadRecommended();
    loadBookings();
}

function switchTab(tab) {
    document.querySelectorAll(".tab-bar button").forEach(b => b.classList.remove("active"));
    const btn = document.querySelector(`[data-tab="${tab}"]`);
    if (btn) btn.classList.add("active");
    document.getElementById("browseTab").style.display = tab === "browse" ? "block" : "none";
    document.getElementById("bookingsTab").style.display = tab === "bookings" ? "block" : "none";
    if (tab === "bookings") loadBookings();
}

async function updateWallet() {
    try {
        const res = await fetch(`${API}/users/${userId}`);
        if (res.ok) {
            const user = await res.json();
            document.getElementById("walletBalance").innerText = money(user.balance);
        }
    } catch (e) { console.error(e); }
}

// rechargePrompt() lives in payment.js (modal with Razorpay + demo top-up).

async function loadEvents() {
    try {
        const res = await fetch(`${API}/events?upcoming=true`);
        allEvents = await res.json();
        // soonest first
        allEvents.sort((a, b) => new Date(a.eventDateTime) - new Date(b.eventDateTime));
        renderPills();
        renderEvents();
    } catch (e) {
        console.error(e);
        document.getElementById("eventGrid").innerHTML = '<div class="alert alert-danger">Failed to load events. Is the backend running?</div>';
    }
}

async function loadRecommended() {
    try {
        const res = await fetch(`${API}/events/recommended?userId=${userId}`);
        recommended = await res.json();
        const section = document.getElementById("recommendedSection");
        if (!recommended.length) { section.style.display = "none"; return; }
        section.style.display = "block";
        document.getElementById("recommendedStrip").innerHTML = recommended.slice(0, 6).map(ev => `
            <div class="event-card js-event-card" data-event-id="${ev.id}" style="min-width:230px;max-width:230px;cursor:pointer;">
                <div class="poster" style="height:120px;font-size:2rem;">${escapeHtml((ev.title || "?").charAt(0).toUpperCase())}</div>
                <div class="body">
                    <h5>${escapeHtml(ev.title)}</h5>
                    <div class="meta"><span class="badge-genre">${escapeHtml(ev.category)}</span></div>
                    <div class="meta" style="font-size:0.8rem;">📅 ${formatDateTime(ev.eventDateTime)}</div>
                    <div class="price">₹${money(ev.ticketPrice)}</div>
                </div>
            </div>`).join("");
        document.querySelectorAll("#recommendedStrip .js-event-card").forEach(card => {
            card.addEventListener("click", () => openBookingById(card.dataset.eventId));
        });
    } catch (e) { console.error(e); }
}

function renderPills() {
    const cats = ["", "Concert", "Workshop", "Seminar", "Other"];
    const counts = {};
    allEvents.forEach(ev => { counts[ev.category] = (counts[ev.category] || 0) + 1; });
    document.getElementById("categoryPills").innerHTML = cats.map(c => {
        const n = c === "" ? allEvents.length : (counts[c] || 0);
        const label = c === "" ? "All" : c;
        const active = activeCategory === c;
        return `<button class="${active ? "btn-primary-custom" : "btn-secondary-custom"} js-pill"
            data-cat="${c}" style="padding:6px 16px;font-size:0.85rem;">${label} (${n})</button>`;
    }).join("");
    document.querySelectorAll(".js-pill").forEach(btn => {
        btn.addEventListener("click", () => { activeCategory = btn.dataset.cat; renderPills(); renderEvents(); });
    });
}

function renderEvents() {
    const search = document.getElementById("searchBox").value.toLowerCase();
    const filtered = allEvents.filter(ev =>
        (!search || (ev.title || "").toLowerCase().includes(search) || (ev.venue || "").toLowerCase().includes(search)) &&
        (!activeCategory || ev.category === activeCategory)
    );

    const grid = document.getElementById("eventGrid");
    if (filtered.length === 0) {
        grid.innerHTML = '<div class="alert alert-info">No events found.</div>';
        return;
    }
    grid.innerHTML = filtered.map(ev => {
        const soldOut = (ev.availableSeats || 0) <= 0;
        const past = ev.eventDateTime && new Date(ev.eventDateTime) < new Date();
        const urgent = !soldOut && !past && ev.availableSeats <= 10;
        return `
        <div class="event-card js-event-card" data-event-id="${ev.id}">
            <div class="poster">${escapeHtml((ev.title || "?").charAt(0).toUpperCase())}</div>
            <div class="body">
                <h5>${escapeHtml(ev.title)}</h5>
                <div class="meta">
                    <span class="badge-genre">${escapeHtml(ev.category)}</span>
                    <span>${escapeHtml(ev.venue)}</span>
                </div>
                <div class="meta" style="font-size:0.8rem;">📅 ${formatDateTime(ev.eventDateTime)}</div>
                <div class="meta" style="font-size:0.8rem;">🎪 ${escapeHtml(ev.organizer ? ev.organizer.organizationName : "")}</div>
                <div class="price">₹${money(ev.ticketPrice)} <span style="font-size:0.8rem;font-weight:400;color:var(--text-muted);">/ ticket</span></div>
                <div style="font-size:0.85rem;margin-top:4px;color:${soldOut || past ? "#b91c1c" : (urgent ? "#e65100" : "#065f46")};font-weight:600;">
                    ${past ? "Event ended" : (soldOut ? "Sold out" : (urgent ? `🔥 Only ${ev.availableSeats} left!` : ev.availableSeats + " seats left"))}
                </div>
            </div>
        </div>`;
    }).join("");

    grid.querySelectorAll('.js-event-card').forEach(card => {
        card.addEventListener('click', () => openBookingById(card.dataset.eventId));
    });
}

function openBookingById(eventId) {
    const ev = allEvents.find(x => x.id == eventId);
    if (ev) openBooking(ev);
}

function openBooking(ev) {
    currentEvent = ev;
    const past = ev.eventDateTime && new Date(ev.eventDateTime) < new Date();
    document.getElementById("modalEventInfo").innerHTML = `
        <strong>${escapeHtml(ev.title)}</strong>
        <div class="text-muted" style="font-size:0.85rem;">${escapeHtml(ev.category)} · ${escapeHtml(ev.venue)}</div>
        <div class="text-muted" style="font-size:0.85rem;">📅 ${formatDateTime(ev.eventDateTime)}</div>
        <div class="text-muted" style="font-size:0.85rem;">${escapeHtml(ev.description || "")}</div>
        <div style="font-weight:600;color:var(--primary);margin-top:6px;">₹${money(ev.ticketPrice)} per ticket · ${ev.availableSeats} left</div>
        ${past ? '<div style="color:#b91c1c;font-weight:600;">This event has ended.</div>' : ''}
    `;
    document.getElementById("qtyInput").value = 1;
    updateModalPrice();
    document.getElementById("bookingModal").classList.add("show");
}

function updateModalPrice() {
    const qty = parseInt(document.getElementById("qtyInput").value || 0);
    if (currentEvent) {
        const total = Number(currentEvent.ticketPrice) * qty;
        document.getElementById("modalPrice").innerText = `Total: ₹${total.toFixed(2)}`;
    }
}
document.addEventListener("input", e => { if (e.target.id === "qtyInput") updateModalPrice(); });

function closeBookingModal() { document.getElementById("bookingModal").classList.remove("show"); }
function closeTicketModal() { document.getElementById("ticketModal").classList.remove("show"); }

async function confirmBooking() {
    const qty = parseInt(document.getElementById("qtyInput").value || 0);
    if (!currentEvent) return;
    if (!qty || qty < 1) return alert("Please enter a valid quantity");
    try {
        const res = await fetch(`${API}/bookings`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ userId: parseInt(userId), eventId: currentEvent.id, quantity: qty })
        });
        const data = await res.json();
        if (!res.ok) {
            alert("Booking failed: " + (data.error || "Unknown error"));
            return;
        }
        closeBookingModal();
        showTickets(data.booking, data.tickets, data.qrCodes);
        updateWallet();
        loadEvents();
        loadBookings();
    } catch (e) {
        alert("Network error");
    }
}

function showTickets(booking, tickets, qrCodes) {
    const ev = booking.event || currentEvent || {};
    document.getElementById("ticketDetails").innerHTML = `
        <div><strong>Booking ID:</strong> <code style="font-size:0.75rem;">${escapeHtml(booking.bookingId)}</code></div>
        <div style="margin-top:8px;"><strong>Event:</strong> ${escapeHtml(ev.title || "")}</div>
        <div><strong>Tickets:</strong> ${booking.quantity} · <strong>Total:</strong> ₹${money(booking.totalAmount)}</div>
        <div style="margin-top:4px;color:var(--text-muted);font-size:0.85rem;">Show this QR at the gate. One QR = one entry.</div>
    `;
    const list = document.getElementById("ticketQrList");
    list.innerHTML = (tickets || []).map((t, i) => `
        <div style="border:1px solid var(--border-color);border-radius:8px;padding:10px;">
            <div style="font-size:0.8rem;font-weight:600;margin-bottom:6px;">Ticket ${i + 1} · ${escapeHtml(t.status)}</div>
            <img src="data:image/png;base64,${qrCodes[t.ticketId]}" style="width:140px;height:140px;border:1px solid var(--border-color);border-radius:6px;" alt="QR">
            <div style="font-size:0.65rem;color:var(--text-muted);word-break:break-all;margin-top:6px;"><code>${escapeHtml(t.qrToken)}</code></div>
        </div>
    `).join("");
    document.getElementById("ticketModal").classList.add("show");
}

async function loadBookings() {
    try {
        const res = await fetch(`${API}/bookings/user/${userId}`);
        const bookings = await res.json();
        const div = document.getElementById("bookingsList");
        if (!bookings.length) {
            div.innerHTML = '<p class="text-muted">No bookings yet. Browse events to book your first ticket!</p>';
            return;
        }
        let html = `<table>
            <thead><tr><th>Event</th><th>Date</th><th>Qty</th><th>Amount</th><th>Status</th><th></th></tr></thead>
            <tbody>`;
        for (const b of bookings) {
            const ev = b.event || {};
            const isConfirmed = b.status === 'CONFIRMED';
            html += `<tr>
                <td>${escapeHtml(ev.title || "?")}<br><span class="text-muted" style="font-size:0.8rem;">${escapeHtml(ev.venue || "")}</span></td>
                <td>${formatDateTime(ev.eventDateTime)}</td>
                <td>${b.quantity}</td>
                <td>₹${money(b.totalAmount)}</td>
                <td><span class="badge-genre" style="background:${isConfirmed ? '#d1fae5' : '#fee2e2'};color:${isConfirmed ? '#065f46' : '#991b1b'};">${b.status}</span></td>
                <td style="white-space:nowrap;">
                    <button class="btn-secondary-custom js-view-qr" style="padding:4px 10px;font-size:0.85rem;" data-bid="${b.bookingId}">Tickets</button>
                    ${isConfirmed ? `<button class="btn-danger-custom js-cancel-booking" style="padding:4px 10px;font-size:0.85rem;" data-bid="${b.bookingId}">Cancel</button>` : ''}
                </td>
            </tr>`;
        }
        html += '</tbody></table>';
        div.innerHTML = html;

        div.querySelectorAll('.js-view-qr').forEach(btn => {
            btn.addEventListener('click', () => viewTickets(btn.dataset.bid));
        });
        div.querySelectorAll('.js-cancel-booking').forEach(btn => {
            btn.addEventListener('click', () => cancelBooking(btn.dataset.bid));
        });
    } catch (e) {
        console.error(e);
    }
}

async function viewTickets(bookingId) {
    try {
        const res = await fetch(`${API}/bookings/${bookingId}`);
        const data = await res.json();
        if (!res.ok) return alert("Failed to load booking");
        currentEvent = data.booking.event;
        showTickets(data.booking, data.tickets, data.qrCodes);
    } catch (e) { alert("Network error"); }
}

async function cancelBooking(bookingId) {
    if (!confirm("Cancel this booking? Seats will be released and amount refunded.")) return;
    try {
        const res = await fetch(`${API}/bookings/${bookingId}/cancel`, { method: "POST" });
        const data = await res.json();
        if (!res.ok) return alert("Cancel failed: " + (data.error || "Unknown error"));
        showAlert("Booking cancelled. Refund credited.", "success");
        updateWallet();
        loadEvents();
        loadBookings();
    } catch (e) { alert("Network error"); }
}

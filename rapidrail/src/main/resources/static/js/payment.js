// Razorpay wallet top-up (test mode). Adapted from the rapidrail project's
// payment.js. If the backend has no keys configured, the Razorpay button stays
// hidden and only instant demo top-up is offered.
let razorpayEnabled = false;
let razorpayKeyId = null;

document.addEventListener("DOMContentLoaded", async () => {
    try {
        const res = await fetch(`${API}/payments/config`);
        const cfg = await res.json();
        razorpayEnabled = !!cfg.enabled;
        razorpayKeyId = cfg.keyId || null;
        if (razorpayEnabled) {
            await loadRazorpayScript();
            document.getElementById("razorpayRow").style.display = "block";
        }
    } catch (e) { console.error("payments config unavailable", e); }
});

function loadRazorpayScript() {
    return new Promise((resolve, reject) => {
        if (window.Razorpay) return resolve();
        const s = document.createElement("script");
        s.src = "https://checkout.razorpay.com/v1/checkout.js";
        s.onload = resolve;
        s.onerror = () => reject(new Error("Could not load Razorpay checkout.js"));
        document.head.appendChild(s);
    });
}

async function openRechargeModal() {
    document.getElementById("rechargeModal").classList.add("show");
    loadTopupHistory();
}

function closeRechargeModal() {
    document.getElementById("rechargeModal").classList.remove("show");
}

// Fallback when Razorpay is not configured (or for quick demos).
async function demoTopUp() {
    const amount = parseFloat(document.getElementById("rechargeAmount").value);
    if (!amount || amount <= 0) return alert("Invalid amount");
    await rechargeWallet(amount);
}

async function rechargeWallet(amount) {
    try {
        const res = await fetch(`${API}/users/${userId}/recharge`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ amount })
        });
        const data = await res.json();
        if (!res.ok) return alert("Recharge failed: " + (data.error || "?"));
        document.getElementById("walletBalance").innerText = money(data.balance);
        showAlert(`₹${amount} added to wallet!`, "success");
        loadTopupHistory();
    } catch (e) { alert("Network error"); }
}

async function payWithRazorpay() {
    const amount = parseFloat(document.getElementById("rechargeAmount").value);
    if (!amount || amount <= 0) return alert("Invalid amount");
    try {
        const initRes = await fetch(`${API}/payments/initiate`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ userId: parseInt(userId), amount })
        });
        const init = await initRes.json();
        if (!initRes.ok || !init.success) {
            return alert("Could not start payment: " + (init.message || "?"));
        }
        const rzp = new Razorpay({
            key: init.keyId || razorpayKeyId,
            amount: Math.round(amount * 100),
            currency: "INR",
            name: "EventPass",
            description: `Add ₹${amount} to wallet`,
            order_id: init.orderId,
            theme: { color: "#000000" },
            handler: (resp) => verifyRazorpay(resp, amount),
            modal: { ondismiss: () => showAlert("Payment cancelled", "info") }
        });
        rzp.on("payment.failed", (resp) => {
            fetch(`${API}/payments/failure`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ orderId: resp.error.metadata.order_id, failureReason: resp.error.description })
            });
            alert("Payment failed: " + resp.error.description);
        });
        rzp.open();
    } catch (e) { alert("Network error: " + e.message); }
}

async function verifyRazorpay(resp, amount) {
    try {
        const res = await fetch(`${API}/payments/verify`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                orderId: resp.razorpay_order_id,
                paymentId: resp.razorpay_payment_id,
                signature: resp.razorpay_signature
            })
        });
        const data = await res.json();
        if (!res.ok || !data.success) return alert("Verification failed: " + (data.message || "?"));
        showAlert(`✓ Payment successful! ₹${amount} added to wallet`, "success");
        closeRechargeModal();
        updateWallet();
    } catch (e) { alert("Verification error. Check top-up history before retrying."); }
}

async function loadTopupHistory() {
    const div = document.getElementById("topupHistory");
    try {
        const res = await fetch(`${API}/payments/history/${userId}`);
        const data = await res.json();
        const rows = data.payments || [];
        if (!rows.length) { div.innerHTML = "No Razorpay top-ups yet."; return; }
        div.innerHTML = rows.slice(0, 5).map(p =>
            `<div>₹${money(p.amount)} · ${p.status} · ${formatDateTime(p.createdAt)}</div>`).join("");
    } catch (e) { div.innerHTML = "History unavailable."; }
}

// Navbar "+ Add Money" now opens the modal instead of prompt().
async function rechargePrompt() {
    openRechargeModal();
}

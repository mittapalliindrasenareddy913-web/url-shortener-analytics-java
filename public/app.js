// Global state
let currentUrls = [];
let currentQrUrl = "";

// DOM Elements
const linksTableBody = document.getElementById("linksTableBody");
const totalLinksCount = document.getElementById("totalLinksCount");
const totalClicksCount = document.getElementById("totalClicksCount");
const avgClicksCount = document.getElementById("avgClicksCount");
const alertMsg = document.getElementById("alertMsg");
const shortenBtn = document.getElementById("shortenBtn");

window.addEventListener("DOMContentLoaded", () => {
  loadUrls();
});

// --- Load all URLs ---
async function loadUrls() {
  try {
    const res = await fetch("/api/urls");
    const data = await res.json();
    currentUrls = data;
    renderTable(data);
    updateGlobalStats(data);
  } catch (err) {
    console.error("Failed to load URLs", err);
  }
}

// --- Render Table ---
function renderTable(urls) {
  linksTableBody.innerHTML = "";
  if (!urls || urls.length === 0) {
    linksTableBody.innerHTML = `
      <tr>
        <td colspan="5" style="text-align:center; padding: 32px; color: #9ca3af;">
          No links shortened yet. Paste a link above to get started!
        </td>
      </tr>
    `;
    return;
  }

  urls.forEach(u => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>
        <div class="link-title">${escapeHtml(u.title || 'Untitled Link')}</div>
        <div class="original-link" title="${escapeHtml(u.originalUrl)}">🔗 ${escapeHtml(u.originalUrl)}</div>
      </td>
      <td>
        <a href="${u.shortUrl}" target="_blank" class="short-link-badge">
          ${escapeHtml(u.shortUrl)}
        </a>
      </td>
      <td>
        <span class="clicks-badge">${u.clicksCount} clicks</span>
      </td>
      <td style="color:#9ca3af; font-size:12px;">
        ${u.createdAt}
      </td>
      <td>
        <div class="action-btns">
          <button class="action-btn" onclick="copyToClipboard('${u.shortUrl}', this)">📋 Copy</button>
          <button class="action-btn" onclick="openQrModal('${u.shortUrl}')">📱 QR</button>
          <button class="action-btn" onclick="openAnalyticsModal('${u.customAlias || u.shortCode}', '${escapeHtml(u.title || u.shortCode)}', '${u.shortUrl}')">📊 Stats</button>
        </div>
      </td>
    `;
    linksTableBody.appendChild(tr);
  });
}

// --- Global Stats ---
function updateGlobalStats(urls) {
  const totalLinks = urls.length;
  let totalClicks = 0;
  urls.forEach(u => totalClicks += u.clicksCount);

  totalLinksCount.innerText = totalLinks;
  totalClicksCount.innerText = totalClicks;
  avgClicksCount.innerText = totalLinks > 0 ? (totalClicks / totalLinks).toFixed(1) : "0.0";
}

// --- Shorten Form Handler ---
async function handleShorten(e) {
  e.preventDefault();
  const originalUrl = document.getElementById("originalUrl").value.trim();
  const customAlias = document.getElementById("customAlias").value.trim();
  const title = document.getElementById("linkTitle").value.trim();

  if (!originalUrl) return;

  showAlert("", ""); // hide
  shortenBtn.disabled = true;
  shortenBtn.innerText = "Shortening... ⏳";

  try {
    const res = await fetch("/api/shorten", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ originalUrl, customAlias, title })
    });
    const data = await res.json();

    if (data.success) {
      showAlert(`🎉 Success! Short link created: <a href="${data.shortUrl}" target="_blank" style="color:#38bdf8; font-weight:700;">${data.shortUrl}</a>`, "success");
      document.getElementById("originalUrl").value = "";
      document.getElementById("customAlias").value = "";
      document.getElementById("linkTitle").value = "";
      await loadUrls();
    } else {
      showAlert(data.message || "Failed to shorten URL", "error");
    }
  } catch (err) {
    showAlert("Server connection failed. Is the server running?", "error");
  } finally {
    shortenBtn.disabled = false;
    shortenBtn.innerText = "Shorten URL 🚀";
  }
}

// --- Copy to clipboard ---
function copyToClipboard(text, btn) {
  navigator.clipboard.writeText(text).then(() => {
    const orig = btn.innerText;
    btn.innerText = "✅ Copied!";
    setTimeout(() => btn.innerText = orig, 1800);
  });
}

// --- QR Code Modal ---
function openQrModal(url) {
  currentQrUrl = url;
  document.getElementById("qrLinkText").innerText = url;
  // Free reliable QR API for instant preview
  document.getElementById("qrImage").src = `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(url)}`;
  document.getElementById("qrModal").style.display = "flex";
}

function closeQrModal() {
  document.getElementById("qrModal").style.display = "none";
}

function downloadQr() {
  window.open(document.getElementById("qrImage").src, "_blank");
}

// --- Analytics Modal ---
async function openAnalyticsModal(code, title, shortUrl) {
  document.getElementById("modalLinkTitle").innerText = `${title} (Analytics)`;
  document.getElementById("modalShortUrl").innerText = shortUrl;
  document.getElementById("analyticsModal").style.display = "flex";

  // Reset bodies
  document.getElementById("browserBreakdown").innerHTML = "Loading...";
  document.getElementById("osBreakdown").innerHTML = "Loading...";
  document.getElementById("refBreakdown").innerHTML = "Loading...";
  document.getElementById("recentVisitsBody").innerHTML = "<tr><td colspan='5'>Loading...</td></tr>";

  try {
    const res = await fetch(`/api/analytics?code=${encodeURIComponent(code)}`);
    const data = await res.json();

    // Render Breakdowns
    renderBreakdown("browserBreakdown", data.browsers);
    renderBreakdown("osBreakdown", data.os);
    renderBreakdown("refBreakdown", data.referrers);

    // Render Recent Visits Table
    const tbody = document.getElementById("recentVisitsBody");
    tbody.innerHTML = "";
    if (data.recentVisits && data.recentVisits.length > 0) {
      data.recentVisits.forEach(v => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
          <td>${v.clickedAt}</td>
          <td><b>${v.browser}</b></td>
          <td>${v.os}</td>
          <td>${escapeHtml(v.referrer)}</td>
          <td>🇮🇳 ${v.country}</td>
        `;
        tbody.appendChild(tr);
      });
    } else {
      tbody.innerHTML = `<tr><td colspan="5" style="text-align:center; color:#9ca3af; padding:16px;">No visits recorded yet. Click the short link to generate analytics!</td></tr>`;
    }
  } catch (err) {
    console.error(err);
  }
}

function renderBreakdown(containerId, map) {
  const container = document.getElementById(containerId);
  container.innerHTML = "";
  if (!map || Object.keys(map).length === 0) {
    container.innerHTML = "<div style='color:#9ca3af; font-size:12px;'>No data yet</div>";
    return;
  }

  for (const [key, count] of Object.entries(map)) {
    const row = document.createElement("div");
    row.className = "bar-row";
    row.innerHTML = `
      <span>${key}</span>
      <span class="bar-val">${count} clicks</span>
    `;
    container.appendChild(row);
  }
}

function closeAnalyticsModal() {
  document.getElementById("analyticsModal").style.display = "none";
}

// Helpers
function showAlert(html, type) {
  if (!html) {
    alertMsg.style.display = "none";
    return;
  }
  alertMsg.className = `alert-box ${type}`;
  alertMsg.innerHTML = html;
  alertMsg.style.display = "block";
}

function escapeHtml(str) {
  const d = document.createElement("div");
  d.innerText = str || "";
  return d.innerHTML;
}

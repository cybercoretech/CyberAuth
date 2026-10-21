const root = document.documentElement;
const picker = document.querySelector("#themeSelect");
const button = document.querySelector("#themeButton");
const menu = document.querySelector("#themeMenu");
const labels = { "cybercore-tech": "CYBERCORE TECH / MASTER MIX", cyberdyne: "CYBERDYNE", cyberpunk: "CYBERPUNK", default: "DEFAULT / CLASSICS", dystopian: "DYSTOPIAN", neosynth: "NEOSYNTH", "omarchy-live": "OMARCHY LIVE", synthwave: "SYNTHWAVE" };
const display = (name) => name.replaceAll("-", " ").toUpperCase();
const cache = new Map();
const catalogUrl = "https://cybercoretech.github.io/data/cybergrid.json";
const variables = { bg: "--bg", white: "--text", acid_green: "--acid", hot_pink: "--pink", purple: "--violet", cyan: "--cyan", orange: "--orange", red: "--red", panel: "--panel", line: "--line", muted: "--muted" };

async function palette(family, name) {
  const key = `${family}/${name}`;
  if (!cache.has(key)) cache.set(key, fetch(`https://cybercoretech.github.io/data/themes/${encodeURIComponent(family)}/${encodeURIComponent(name)}.json`).then(response => { if (!response.ok) throw new Error("Theme not found"); return response.json(); }));
  return cache.get(key);
}
function apply(name, colors) {
  for (const [key, property] of Object.entries(variables)) if (colors[key]) root.style.setProperty(property, `#${colors[key].replace(/^#/, "")}`);
  root.dataset.theme = name;
  try { localStorage.setItem("cyberauth-theme", name); } catch { /* optional preference */ }
  button.textContent = picker.selectedOptions[0]?.textContent || name.toUpperCase();
  menu.querySelectorAll(".theme-option").forEach(option => option.setAttribute("aria-selected", String(option.dataset.value === name)));
}
function closeMenu() { menu.hidden = true; button.setAttribute("aria-expanded", "false"); }
function buildMenu() {
  menu.replaceChildren();
  for (const group of picker.querySelectorAll("optgroup")) {
    const heading = document.createElement("div"); heading.className = "theme-group-label"; heading.textContent = group.label; menu.append(heading);
    for (const source of group.querySelectorAll("option")) {
      const option = document.createElement("button"); option.type = "button"; option.className = "theme-option"; option.setAttribute("role", "option"); option.dataset.value = source.value; option.textContent = source.textContent;
      option.addEventListener("click", async () => { picker.value = source.value; const colors = await palette(source.dataset.family, source.value); apply(source.value, colors); closeMenu(); history.replaceState(null, "", `?theme=${encodeURIComponent(source.value)}`); }); menu.append(option);
    }
  }
}
button.addEventListener("click", () => { menu.hidden = !menu.hidden; button.setAttribute("aria-expanded", String(!menu.hidden)); });
document.addEventListener("click", event => { if (!event.target.closest(".theme-picker")) closeMenu(); });
document.addEventListener("keydown", event => { if (event.key === "Escape") closeMenu(); });
document.querySelectorAll(".copy-button").forEach(copyButton => copyButton.addEventListener("click", async () => {
  const command = copyButton.dataset.copy;
  if (!command) return;
  try {
    await navigator.clipboard.writeText(command);
    copyButton.textContent = "COPIED";
    window.setTimeout(() => { copyButton.textContent = "COPY"; }, 1600);
  } catch {
    copyButton.textContent = "SELECT COMMAND";
  }
}));
const privacyDialog = document.querySelector(".privacy-dialog");
const privacyDetails = {
  codes: { number: "01", title: "Local code generation", body: "Time-based sign-in codes are calculated on this device from the account secrets you add. The website illustration is decorative; it does not generate or display real authentication codes." },
  storage: { number: "02", title: "Encrypted device storage", body: "CyberAuth encrypts saved account data with AES-256-GCM and an Android Keystore key. Portable backups use a separate passphrase. Keep that passphrase safe: it cannot be recovered if lost." },
  tracking: { number: "03", title: "No tracking or account", body: "The Android app has no Internet permission, analytics, sign-in, or remote account database. The public website loads its optional theme catalog and shared contact component from Cybercore GitHub Pages." }
};
document.querySelectorAll("[data-privacy]").forEach(trigger => trigger.addEventListener("click", () => {
  const detail = privacyDetails[trigger.dataset.privacy];
  if (!detail || !privacyDialog) return;
  document.querySelector("#privacyEyebrow").textContent = `PRIVACY SIGNAL / ${detail.number}`;
  document.querySelector("#privacyTitle").textContent = detail.title;
  document.querySelector("#privacyBody").textContent = detail.body;
  privacyDialog.showModal();
}));
privacyDialog?.addEventListener("click", event => { if (event.target === privacyDialog) privacyDialog.close(); });
(async () => {
  try {
    const response = await fetch(catalogUrl); if (!response.ok) throw new Error("Theme catalog unavailable");
    const catalog = await response.json();
    for (const [family, names] of Object.entries(catalog.families)) { const group = document.createElement("optgroup"); group.label = labels[family] || family.toUpperCase(); for (const name of names) { const option = document.createElement("option"); option.value = name; option.textContent = display(name); option.dataset.family = family; group.append(option); } picker.append(group); }
    buildMenu();
    const requested = new URLSearchParams(location.search).get("theme"); let saved = null; try { saved = localStorage.getItem("cyberauth-theme"); } catch { /* default below */ }
    const preferred = requested || saved || catalog.active; const match = Object.entries(catalog.families).find(([, names]) => names.includes(preferred)); const family = match?.[0] || "cybercore-tech"; const name = match?.[1].includes(preferred) ? preferred : catalog.active;
    picker.value = name; apply(name, await palette(family, name));
  } catch {
    button.textContent = "CYBERCORE TECH";
    button.title = "Theme catalog unavailable; the default theme is active.";
  }
})();

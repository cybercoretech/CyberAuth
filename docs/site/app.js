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

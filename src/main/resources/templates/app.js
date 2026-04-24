let recordId = null;
const serverURL = "https://test.zentime.co.in";

function showSection(id) {
  document.getElementById(id).classList.remove("hidden");
}

function hideSection(id) {
  document.getElementById(id).classList.add("hidden");
}

function showResponse(msg) {
  document.getElementById("response").innerText = msg;
}

async function startDay() {
  const employeeId = document.getElementById("employeeId").value;

  const formData = new URLSearchParams();
  formData.append("employeeId", employeeId);

  const res = await fetch(`${serverURL}/start-day`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: formData
  });

  const text = await res.text();
  showResponse(text);

  if (res.ok) {
    // Simulate recordId = 1 (or fetch it from backend in real case)
    recordId = 1;
    showSection("statusSection");
    hideSection("dayStartSection");
  }
}

async function markPresent() {
  showSection("timeInSection");
  hideSection("statusSection");
}

async function markAbsent() {
  const employeeId = document.getElementById("employeeId").value;

  const formData = new URLSearchParams();
  formData.append("employeeId", employeeId);

  const res = await fetch(`${serverURL}/mark-absent`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: formData
  });

  const text = await res.text();
  showResponse(text);

  if (res.ok) {
    hideSection("statusSection");
    showSection("closeSection");
  }
}

async function markTimeIn() {
  const image = document.getElementById("imageIn").files[0];
  const formData = new FormData();
  formData.append("recordId", recordId);
  formData.append("imageIn", image);

  const res = await fetch(`${serverURL}/mark-time-in`, {
    method: "POST",
    body: formData
  });

  const text = await res.text();
  showResponse(text);

  if (res.ok) {
    hideSection("timeInSection");
    showSection("timeOutSection");
  }
}

async function markTimeOut() {
  const image = document.getElementById("imageOut").files[0];
  const formData = new FormData();
  formData.append("recordId", recordId);
  formData.append("imageOut", image);

  const res = await fetch(`${serverURL}/mark-time-out`, {
    method: "POST",
    body: formData
  });

  const text = await res.text();
  showResponse(text);

  if (res.ok) {
    hideSection("timeOutSection");
    showSection("closeSection");
  }
}

function resetAll() {
  recordId = null;
  document.getElementById("employeeId").value = "";
  document.getElementById("imageIn").value = "";
  document.getElementById("imageOut").value = "";
  document.getElementById("response").innerText = "";

  showSection("dayStartSection");
  hideSection("statusSection");
  hideSection("timeInSection");
  hideSection("timeOutSection");
  hideSection("closeSection");
}

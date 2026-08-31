const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080";

export type Severity = "SEV1" | "SEV2" | "SEV3" | "SEV4";
export type IncidentStatus =
  | "DETECTED"
  | "INVESTIGATING"
  | "IDENTIFIED"
  | "MITIGATING"
  | "RESOLVED"
  | "POSTMORTEM";

export interface Incident {
  id: string;
  title: string;
  description: string | null;
  serviceName: string | null;
  environment: string;
  severity: Severity;
  status: IncidentStatus;
  detectedBy: string | null;
  startedAt: string | null;
  createdAt: string;
  resolvedAt: string | null;
}

export async function listIncidents(): Promise<Incident[]> {
  const res = await fetch(`${API_BASE}/api/incidents`, { cache: "no-store" });
  if (!res.ok) throw new Error("Failed to load incidents");
  return res.json();
}

export async function getIncident(id: string): Promise<Incident> {
  const res = await fetch(`${API_BASE}/api/incidents/${id}`, { cache: "no-store" });
  if (!res.ok) throw new Error("Failed to load incident");
  return res.json();
}

export async function createIncident(payload: {
  title: string;
  description?: string;
  serviceName?: string;
  environment: string;
  severity: Severity;
  detectedBy?: string;
}): Promise<Incident> {
  const res = await fetch(`${API_BASE}/api/incidents`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!res.ok) throw new Error("Failed to create incident");
  return res.json();
}

export async function updateIncidentStatus(
  id: string,
  status: IncidentStatus
): Promise<Incident> {
  const res = await fetch(`${API_BASE}/api/incidents/${id}/status`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ status }),
  });
  if (!res.ok) throw new Error("Failed to update status");
  return res.json();
}

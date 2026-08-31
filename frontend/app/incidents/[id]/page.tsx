"use client";

import { useEffect, useState } from "react";
import { getIncident, updateIncidentStatus, Incident, IncidentStatus } from "@/lib/api";

const FLOW: IncidentStatus[] = [
  "DETECTED",
  "INVESTIGATING",
  "IDENTIFIED",
  "MITIGATING",
  "RESOLVED",
  "POSTMORTEM",
];

export default function IncidentDetailPage({ params }: { params: { id: string } }) {
  const [incident, setIncident] = useState<Incident | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [updating, setUpdating] = useState(false);
  const [investigating, setInvestigating] = useState(false);
  const [investigationResult, setInvestigationResult] = useState<string | null>(null);

  async function load() {
    try {
      const data = await getIncident(params.id);
      setIncident(data);
    } catch {
      setError("Could not load this incident.");
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params.id]);

  async function advance() {
    if (!incident) return;
    const currentIndex = FLOW.indexOf(incident.status);
    const next = FLOW[currentIndex + 1];
    if (!next) return;
    setUpdating(true);
    try {
      const updated = await updateIncidentStatus(incident.id, next);
      setIncident(updated);
    } catch {
      setError("Failed to update status.");
    } finally {
      setUpdating(false);
    }
  }

  async function investigate() {
    if (!incident) return;
    setInvestigating(true);
    setInvestigationResult(null);
    try {
      const res = await fetch(
        `${process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080"}/api/incidents/${incident.id}/investigate`,
        { method: "POST" }
      );
      if (!res.ok) throw new Error();
      const data = await res.json();
      setInvestigationResult(data.investigation);
    } catch {
      setInvestigationResult(
        "Investigation failed - check that an LLM provider and the github-mcp-server are running."
      );
    } finally {
      setInvestigating(false);
    }
  }

  if (error) return <p style={{ color: "#e5484d" }}>{error}</p>;
  if (!incident) return <p>Loading...</p>;

  const currentIndex = FLOW.indexOf(incident.status);
  const nextStatus = FLOW[currentIndex + 1];

  return (
    <div style={{ maxWidth: 640 }}>
      <h1>{incident.title}</h1>
      <p style={{ color: "#999" }}>{incident.description}</p>

      <dl style={{ display: "grid", gridTemplateColumns: "160px 1fr", rowGap: "0.5rem" }}>
        <dt>Service</dt>
        <dd>{incident.serviceName ?? "-"}</dd>
        <dt>Environment</dt>
        <dd>{incident.environment}</dd>
        <dt>Severity</dt>
        <dd>{incident.severity}</dd>
        <dt>Status</dt>
        <dd>{incident.status}</dd>
        <dt>Started</dt>
        <dd>{incident.startedAt ? new Date(incident.startedAt).toLocaleString() : "-"}</dd>
      </dl>

      <div style={{ marginTop: "1.5rem" }}>
        <strong>Lifecycle: </strong>
        {FLOW.map((s, i) => (
          <span key={s} style={{ opacity: i <= currentIndex ? 1 : 0.4 }}>
            {s}{i < FLOW.length - 1 ? " → " : ""}
          </span>
        ))}
      </div>

      {nextStatus && (
        <button style={{ marginTop: "1rem" }} onClick={advance} disabled={updating}>
          {updating ? "Updating..." : `Move to ${nextStatus}`}
        </button>
      )}

      <div style={{ marginTop: "2rem" }}>
        <button onClick={investigate} disabled={investigating}>
          {investigating ? "Investigating..." : "🔍 Investigate (RAG + MCP)"}
        </button>
        <p style={{ color: "#666", fontSize: "0.85rem", marginTop: "0.5rem" }}>
          Baseline one-shot investigation: RAG-retrieves relevant knowledge and lets the
          model call MCP tools (currently GitHub) if it needs live evidence. This is a
          starting point, not the full multi-step orchestrator from the design doc.
        </p>
      </div>

      {investigationResult && (
        <div
          style={{
            marginTop: "1rem",
            padding: "1rem",
            background: "#1a1b1e",
            borderRadius: 8,
            whiteSpace: "pre-wrap",
          }}
        >
          {investigationResult}
        </div>
      )}
    </div>
  );
}

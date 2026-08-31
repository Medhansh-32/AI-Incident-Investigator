"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { createIncident, Severity } from "@/lib/api";

export default function NewIncidentPage() {
  const router = useRouter();
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [serviceName, setServiceName] = useState("");
  const [environment, setEnvironment] = useState("production");
  const [severity, setSeverity] = useState<Severity>("SEV2");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const incident = await createIncident({
        title,
        description,
        serviceName,
        environment,
        severity,
        detectedBy: "manual",
      });
      router.push(`/incidents/${incident.id}`);
    } catch (err) {
      setError("Failed to create incident. Is the backend running?");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div style={{ maxWidth: 480 }}>
      <h1>New Incident</h1>
      <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
        <label>
          Title
          <input value={title} onChange={(e) => setTitle(e.target.value)} required style={{ width: "100%" }} />
        </label>

        <label>
          Description
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={3}
            style={{ width: "100%" }}
          />
        </label>

        <label>
          Service name
          <input
            value={serviceName}
            onChange={(e) => setServiceName(e.target.value)}
            placeholder="payment-service"
            style={{ width: "100%" }}
          />
        </label>

        <label>
          Environment
          <input value={environment} onChange={(e) => setEnvironment(e.target.value)} style={{ width: "100%" }} />
        </label>

        <label>
          Severity
          <select value={severity} onChange={(e) => setSeverity(e.target.value as Severity)} style={{ width: "100%" }}>
            <option value="SEV1">SEV1 - Critical</option>
            <option value="SEV2">SEV2 - High</option>
            <option value="SEV3">SEV3 - Medium</option>
            <option value="SEV4">SEV4 - Low</option>
          </select>
        </label>

        {error && <p style={{ color: "#e5484d" }}>{error}</p>}

        <button type="submit" disabled={submitting}>
          {submitting ? "Creating..." : "Create Incident"}
        </button>
      </form>
    </div>
  );
}

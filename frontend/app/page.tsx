import Link from "next/link";
import { listIncidents } from "@/lib/api";

const severityColor: Record<string, string> = {
  SEV1: "#e5484d",
  SEV2: "#f5a623",
  SEV3: "#f8e71c",
  SEV4: "#7ed321",
};

export default async function DashboardPage() {
  let incidents = [];
  let error: string | null = null;

  try {
    incidents = await listIncidents();
  } catch (e) {
    error = "Could not reach the backend at http://localhost:8080 - is it running?";
  }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <h1>Incidents</h1>
        <Link href="/incidents/new">
          <button>+ New Incident</button>
        </Link>
      </div>

      {error && <p style={{ color: "#e5484d" }}>{error}</p>}

      {!error && incidents.length === 0 && <p>No incidents yet.</p>}

      {!error && incidents.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Title</th>
              <th>Service</th>
              <th>Severity</th>
              <th>Status</th>
              <th>Started</th>
            </tr>
          </thead>
          <tbody>
            {incidents.map((incident: any) => (
              <tr key={incident.id}>
                <td>
                  <Link href={`/incidents/${incident.id}`}>{incident.title}</Link>
                </td>
                <td>{incident.serviceName ?? "-"}</td>
                <td>
                  <span
                    className="badge"
                    style={{ background: severityColor[incident.severity], color: "#000" }}
                  >
                    {incident.severity}
                  </span>
                </td>
                <td>{incident.status}</td>
                <td>{incident.startedAt ? new Date(incident.startedAt).toLocaleString() : "-"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

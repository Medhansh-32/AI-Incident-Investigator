# AI Incident Investigator — Starter Scaffold

This is a **working starting point**, not the finished 10-phase system from the design doc.
It implements:

- **Phase 1 — Foundation**: full incident CRUD + lifecycle state machine, Spring Boot + Postgres, Next.js dashboard.
- **Phase 2 — RAG (skeleton)**: document ingestion endpoint, chunking, pgvector storage, and a semantic search endpoint using Spring AI. Wire in a real embedding model (Gemini/OpenAI/Ollama) to make it fully functional — see `application.yml`.
- **Phase 4 — MCP (one real server)**: a standalone `github-mcp-server` module exposing three real, working tools (`getRecentCommits`, `getCommitDiff`, `getPullRequest`) over MCP's SSE transport, wired to the actual GitHub REST API. The main backend connects to it as an MCP client.
- **Phase 5 — baseline investigation (not the full agent)**: a single `/api/incidents/{id}/investigate` endpoint that combines RAG retrieval and MCP tool-calling in one `ChatClient` call. This is intentionally the *simplest possible version* — a good baseline to measure the real multi-step orchestrator against later, not the finished agent loop.

Remediation, postmortems, and the Kubernetes/Monitoring/Jira MCP servers are **not** included — build those on top of this foundation, phase by phase, per the roadmap.

### ⚠️ About the MCP dependency names
Spring AI's MCP module is young and its Maven artifact IDs and config property names have
shifted across milestone releases. The `pom.xml` files and `application.yml` configs here
are written against the currently-documented shape (`spring-ai-mcp-server-webmvc-spring-boot-starter`,
`spring-ai-mcp-client-spring-boot-starter`) — **check these against
https://docs.spring.io/spring-ai/reference/api/mcp/ for whichever `spring-ai.version` you
pin** before assuming a build failure is your code's fault.

## Structure

```
ai-incident-investigator/
├── docker-compose.yml            # Postgres + pgvector
├── backend/                      # Spring Boot 3.x - main API
│   └── src/main/java/com/aii/
│       ├── domain/                # JPA entities
│       ├── repository/            # Spring Data JPA repos
│       ├── controller/            # REST endpoints (incidents, knowledge, mcp, investigate)
│       ├── service/                # incident lifecycle, RAG, investigation agent
│       ├── dto/                     # request/response records
│       └── config/                  # CORS, Spring AI config
├── mcp-servers/
│   └── github-mcp-server/         # standalone MCP server - GitHub read tools
│       └── src/main/java/com/aii/mcp/github/
└── frontend/                     # Next.js dashboard (list + create + detail + investigate)
```

## Running it

### 1. Start Postgres + pgvector
```bash
docker compose up -d
```

### 2. Configure an LLM/embedding provider
Edit `backend/src/main/resources/application.yml` and set your API key
(Gemini, OpenAI, or point `spring.ai.ollama.base-url` at a local Ollama instance).
The app will start and the incident CRUD will work even without a key —
only the `/api/knowledge/**` (RAG) endpoints need it.

### 3. Run the backend
The Maven wrapper isn't included in this zip - either generate it or use a local Maven install:
```bash
cd backend
mvn -N io.takari:maven:wrapper   # one-time: generates ./mvnw (needs Maven installed)
./mvnw spring-boot:run
# or, if you'd rather just use your local Maven install directly:
mvn spring-boot:run
```
API available at `http://localhost:8080`. Check `http://localhost:8080/api/health` first.

### 4. Run the GitHub MCP server (separate process, separate module)
```bash
cd mcp-servers/github-mcp-server
export GITHUB_TOKEN=ghp_your_token_here   # a public-repo read token is enough
mvn spring-boot:run
```
Runs on `http://localhost:8081`. Point it at any repo when calling its tools -
try it against a repo you own, or any public one, to sanity-check the wiring.

### 5. Run the frontend
```bash
cd frontend
npm install
npm run dev
```
UI available at `http://localhost:3000`.

### 6. Try it end to end
1. Create an incident on the dashboard.
2. Open it, click **"🔍 Investigate (RAG + MCP)"**.
3. Watch the backend logs — you should see a call out to your embedding
   model (RAG retrieval) and, if the model decides it needs live evidence,
   a call to the github-mcp-server's tools.

You can also test the MCP wiring directly without the UI:
```bash
curl http://localhost:8080/api/mcp/tools          # lists discovered MCP tools
```

## What to build next (per the roadmap)

1. **Phase 3** — incident simulator (fake services generating logs/metrics/deployments).
2. **Phase 4 (continued)** — Kubernetes MCP server and Monitoring MCP server, same pattern
   as `github-mcp-server`: a `@Tool`-annotated class + a `ToolCallbackProvider` bean.
   Add each as a new connection under `spring.ai.mcp.client.sse.connections` in the
   backend's `application.yml`.
3. **Phase 5 (the real orchestrator)** — replace `InvestigationAgentService`'s single
   LLM call with the actual multi-step state machine from the design doc (Analyze →
   Retrieve → Generate Hypotheses → Gather Evidence loop → Evaluate → Recommend),
   persisting each step into `Investigation` / `Hypothesis` / `Evidence` (already
   scaffolded). Keep the current one-shot version around as your "baseline" for the
   Evaluation Framework comparison in the design doc.
4. **Phase 7** — remediation: add a write-capable MCP tool (e.g. `rollbackDeployment`
   on a Kubernetes MCP server) and enforce the approval gate described in the design
   doc *in code*, not just via prompting - a `WriteToolGuard` that checks a
   `Recommendation.status == APPROVED` record before allowing the call through.
5. **Phase 6/8+** — evidence graph UI, postmortem generation + RAG re-ingestion loop.

See the full design doc (AI-Incident-Investigator-Design.md) for the complete data model,
API spec, and flow diagrams — the entities and endpoints here already match that schema
so later phases plug straight in.

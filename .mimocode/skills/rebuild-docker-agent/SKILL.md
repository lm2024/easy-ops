---
name: rebuild-docker-agent
description: Rebuild the Agent jar, build Docker image, deploy container(s), and verify heartbeat. Covers the full agent code→docker→deploy→verify cycle.
---

# Rebuild Docker Agent

Rebuild agent jar → copy to docker dir → build image → deploy container(s) → verify heartbeat. Use this during the edit→build→test loop when developing Agent features.

## When to Use

User says variations of: "重启agent", "重新构建agent", "rebuild agent", "restart agent docker", "agent docker部署".

Also use automatically after completing an Agent-side code change that needs Docker verification.

## Procedure

### Step 1 — Rebuild Agent Jar

```bash
cd <project>/easy-ops/backend/agent && mvn clean package -DskipTests -q 2>&1 | tail -5
```

Verify jar exists:

```bash
ls -la <project>/easy-ops/backend/agent/target/easy-ops-agent-1.0.0-SNAPSHOT.jar
```

### Step 2 — Copy Jar to Docker Build Context

```bash
cp <project>/easy-ops/backend/agent/target/easy-ops-agent-1.0.0-SNAPSHOT.jar \
   <project>/easy-ops/backend/agent/docker/
```

### Step 3 — Deploy Container

**Option A: Single test container** (for quick agent testing):

```bash
docker stop ops-agent-test 2>/dev/null; docker rm ops-agent-test 2>/dev/null

docker run -d \
  --name ops-agent-test \
  -p 2123:2123 \
  -e AGENT_TOKEN=agent-token-1 \
  -e AGENT_NODE_NAME=agent-test \
  -e AGENT_SERVER_URL=http://host.docker.internal:8081/api \
  -e AGENT_HOST_IP=127.0.0.1 \
  <project>/easy-ops/backend/agent/docker
```

**Option B: docker-compose multi-agent** (for full environment):

```bash
cd <project>/easy-ops/backend
docker-compose build agent-1 agent-2 agent-3 2>&1 | tail -5
docker-compose up -d agent-1 agent-2 agent-3 2>&1 | tail -5
```

### Step 4 — Verify

```bash
# Wait for startup
sleep 5

# Check container status
docker ps --filter "name=ops-agent" --format "{{.Names}} {{.Status}}"

# Check agent logs
docker logs ops-agent-test 2>&1 | tail -15

# Check heartbeat via Server API (server must be running)
sleep 15
curl -s http://localhost:8081/api/nodes -H "X-Token: agent-token-1" 2>/dev/null \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print('OK, nodes:', d['data']['total'])" 2>/dev/null
```

### Step 5 — Report

| Item | Status |
|------|--------|
| Agent jar built | Success / Failed |
| Docker image built | Success / Failed |
| Container(s) running | ops-agent-test: Up / Failed |
| Heartbeat | OK (N nodes) / Pending / Failed |

## Notes

- Agent default port: **2123**
- Agent token: `agent-token-1` (test) — matches `AGENT_TOKEN` env var
- Server must be running on port 8081 for heartbeat to work
- `host.docker.internal` works on Docker Desktop (Windows/Mac); Linux needs `--add-host=host.docker.internal:host-gateway`
- Dockerfile is at `backend/agent/docker/Dockerfile`
- entrypoint.sh copies jar from build context to `/app/data/` on first run only; after image rebuild, container restart is needed
- If agent shows "offline" after restart, wait 30s for heartbeat cycle
- Docker agents need `procps` installed for process monitoring (see AGENTS.md known issues)

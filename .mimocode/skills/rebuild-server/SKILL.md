---
name: rebuild-server
description: Kill the running Server process on Windows, rebuild with Maven, start, and verify health. Covers the full edit→build→restart→test cycle used during feature development.
---

# Rebuild Server

Kill, rebuild, start, and verify the easy-ops Server in one shot. Use this during the edit→build→test loop when developing Server features.

## When to Use

User says variations of: "重启server", "重新构建server", "重启后端", "rebuild server", "restart server", "server重启一下".

Also use automatically after completing a Server-side code change that needs verification.

## Procedure

### Step 1 — Kill Existing Server

```bash
# Find and kill process on port 8081
netstat -ano | grep ":8081 " | grep LISTENING | awk '{print $5}' | head -1
```

If a PID is found, kill it:

```bash
taskkill //F //PID <PID>
sleep 2
```

If no process found, skip.

### Step 2 — Rebuild

```bash
cd <project>/easy-ops/backend && mvn clean package -DskipTests -pl server -am -q 2>&1 | tail -10
```

If build fails, show the ERROR lines:

```bash
mvn clean package -DskipTests -pl server -am 2>&1 | grep -B2 "ERROR" | head -20
```

### Step 3 — Start Server

```bash
cd <project>/easy-ops/backend && java -jar server/target/ops-platform-server-1.0.0-SNAPSHOT.jar > /dev/null 2>&1 &
sleep 10
```

### Step 4 — Verify Health

```bash
# Basic health check
curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/api/nodes 2>/dev/null

# Login and check with auth
TOKEN=$(curl -s -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin123!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('token',''))" 2>/dev/null)

curl -s http://localhost:8081/api/nodes -H "Authorization: Bearer $TOKEN" 2>/dev/null \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print('OK, nodes:', d['data']['total'])" 2>/dev/null
```

### Step 5 — Report

| Item | Status |
|------|--------|
| Old process killed | PID xxx / Not running |
| Maven build | Success / Failed (N errors) |
| Server started | port 8081 / Failed |
| API health | OK (N nodes) / Failed |

## Notes

- Default Server port is **8081** (not 8080).
- Admin credentials: `admin / Admin123!`
- Auth header: `Authorization: Bearer <token>` or `X-Token: <agent-token>`
- On Windows use `taskkill //F //PID` (double slash).
- Maven `-pl server -am` builds only server + dependencies (faster than full build).
- If port 8081 is not in use after start, check logs: `tail -50 <project>/easy-ops/backend/server/target/*.log` or check startup output.

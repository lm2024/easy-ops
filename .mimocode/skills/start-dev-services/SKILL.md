---
name: start-dev-services
description: Discover and start both backend (Spring Boot/Maven) and frontend (npm/vite) dev services for a Java+Vue project, handle port conflicts, and verify health.
---

# Start Dev Services

Automatically discover, start, and verify frontend + backend dev services for a Spring Boot + Vue project.

## When to Use

User says variations of: "启动前后端", "启动前后端服务", "start frontend and backend", "start dev services", "启动服务".

## Procedure

### Step 1 — Discover Project Structure

```bash
# Find backend entry point
ls <project>/backend/pom.xml 2>/dev/null && echo "Maven backend found"
ls <project>/backend/src/main/resources/application.yml 2>/dev/null

# Find frontend entry point
ls <project>/frontend/package.json 2>/dev/null && echo "Vue frontend found"
cat <project>/frontend/package.json | grep -E '"dev"|"serve"|"start"'
```

Record:
- Backend port from `application.yml` (default: 8080)
- Frontend port from `vite.config` or `package.json` scripts (default: 3000/3001)

### Step 2 — Check Port Conflicts

```bash
# Windows
netstat -ano | grep ":<backend_port> " | grep LISTENING
netstat -ano | grep ":<frontend_port> " | grep LISTENING
```

If a port is already in use, report the existing process and skip starting that service.

### Step 3 — Start Services (Background)

```bash
# Backend (Maven Spring Boot)
cd <project>/backend && mvn spring-boot:run

# Frontend (npm/vite)
cd <project>/frontend && npm run dev
```

Run both in background. Wait 8-15 seconds for startup.

### Step 4 — Verify Health

```bash
# Check backend
curl -s -o /dev/null -w "%{http_code}" http://localhost:<backend_port> 2>/dev/null

# Check frontend
curl -s -o /dev/null -w "%{http_code}" http://localhost:<frontend_port> 2>/dev/null
```

### Step 5 — Report

Present a summary table:

| Service | URL | Status |
|---------|-----|--------|
| Backend (Spring Boot) | `http://localhost:<port>` | Running / Already running / Failed |
| Frontend (Vite) | `http://localhost:<port>` | Running / Already running / Failed |

If startup fails, read the last 30 lines of the background task output and diagnose (typically port conflict or missing dependency).

## Notes

- On Windows, use `netstat -ano` (not `ss` or `lsof`).
- Spring Boot default port is typically 8080; Vue/Vite default varies (3000, 3001, 5173).
- If Docker is involved (e.g., MySQL), check `docker ps` first and only start app services.
- When port is taken, do NOT kill the existing process — just report it's already running.

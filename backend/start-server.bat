@echo off
set JWT_SECRET=easyops-jwt-secret-key-2026-opsplatform
cd /d "%~dp0server"
java -jar target\ops-platform-server-1.0.0-SNAPSHOT.jar

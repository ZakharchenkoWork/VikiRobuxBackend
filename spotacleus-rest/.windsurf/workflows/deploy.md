---
description: Deploy the Ktor backend to Raspberry Pi via PowerShell script (non-blocking step auto-runnable)
---

# Deploy workflow (/deploy)

Follow these steps to build the fat JAR and deploy to Raspberry Pi using the existing PowerShell script `deployToPi_v3.ps1`.

- Prerequisites
  - Windows host with PowerShell.
  - SSH key present at `%USERPROFILE%\keyProSever` (or adjust in the script).
  - File `.env.server3` exists in repo root and contains MONGODB_URI and MONGODB_DB.
  - Script variables in `deployToPi_v3.ps1` are correct: `$PI_USER`, `$PI_HOST`, `$PI_PATH`, `$PORT`, `$CONTAINER_NAME`.

1) From project root (`spotacleus-rest/`), ensure you can run PowerShell scripts:
   - Start VS Code/IDE terminal as a normal user is fine (script uses SSH keys).

// turbo
2) Build and deploy (non-blocking). This step will:
   - run `shadowJar`
   - upload JAR and `.env` via scp
   - restart Docker container `ktor3` on port 8282

   Command to run:
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\deployToPi_v3.ps1
   ```

3) Verify container on Raspberry Pi (status and ports):
   ```powershell
   ssh -i $env:USERPROFILE\keyProSever konstantyn@192.168.68.67 "docker ps --filter name=ktor3 --format '{{.Names}}|{{.Ports}}|{{.Status}}'"
   ```

4) Smoke tests on Raspberry Pi (localhost:8282):
   - Training exercises (expect 200):
     ```powershell
     ssh -i $env:USERPROFILE\keyProSever konstantyn@192.168.68.67 "curl -s -o /dev/null -w '%{http_code}\n' -H 'X-User-Id: health' http://127.0.0.1:8282/api/training/exercises"
     ```
   - Food meals create (expect 201, or 400 INVALID_JSON on bad body):
     ```powershell
     ssh -i $env:USERPROFILE\keyProSever konstantyn@192.168.68.67 "curl -s -o /dev/null -w '%{http_code}\n' -H 'Content-Type: application/json' -H 'X-User-Id: health' -d '{\"name\":\"Meal\",\"grams\":200,\"calories\":400}' http://127.0.0.1:8282/api/food/meals"
     ```
   - Macros goals (expect 204 or 200):
     ```powershell
     ssh -i $env:USERPROFILE\keyProSever konstantyn@192.168.68.67 "curl -s -o /dev/null -w '%{http_code}\n' -H 'X-User-Id: health' http://127.0.0.1:8282/api/goals/macros"
     ```

5) (Optional) Apply Nginx config and reload (82 -> 8282):
   ```powershell
   scp -i $env:USERPROFILE\keyProSever .deploy\nginx-sportacleus.conf root@192.168.68.67:/etc/nginx/sites-available/sportacleus
   ssh -i $env:USERPROFILE\keyProSever root@192.168.68.67 "ln -sf /etc/nginx/sites-available/sportacleus /etc/nginx/sites-enabled/sportacleus && nginx -t && systemctl reload nginx"
   ```

6) Troubleshooting
   - View logs: `ssh -i %USERPROFILE%\keyProSever konstantyn@192.168.68.67 "docker logs --tail=200 -f ktor3"`
   - Stop Gradle Daemon locally if stuck: `./gradlew --stop`
   - Ensure firewall allows 82/tcp if using Nginx externally.

# StockSense — Spring Boot Backend (Phase 1)

## 1. Extract the zip

Unzip `stocksense-springboot.zip` anywhere on your machine, e.g.:

```
C:\Users\<you>\Projects\stocksense-springboot
```

You should see this exact structure after extracting:

```
stocksense-springboot/
├── pom.xml
├── .gitignore
└── src/
    └── main/
        ├── java/com/stocksense/
        │   ├── StockSenseApplication.java
        │   ├── config/
        │   ├── controller/
        │   ├── dto/
        │   ├── entity/
        │   ├── enums/
        │   ├── exception/
        │   ├── repository/
        │   ├── service/
        │   └── util/
        └── resources/
            ├── application.yml
            └── db/migration/   (V1–V5 SQL files)
```

## 2. Open in VS Code

- VS Code → File → Open Folder → select the `stocksense-springboot` folder itself (the one containing `pom.xml`). Do NOT open a parent folder or a subfolder.
- Install the **Extension Pack for Java** (by Microsoft) and **Spring Boot Extension Pack** if you don't have them — VS Code will prompt you automatically when it detects `pom.xml`.
- Wait for the bottom-right "Java: Loading projects..." to finish. This builds the Maven dependency tree — first time takes 2-5 minutes.

## 3. Set environment variables locally

Create a `.env` file is NOT how Spring Boot reads config — instead, create a file `src/main/resources/application-local.yml` (this filename is already gitignored) with:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://<your-neon-host>/neondb?sslmode=require
    username: <your-neon-username>
    password: <your-neon-password>

jwt:
  secret: <run: openssl rand -base64 48>

ml:
  service:
    base-url: http://localhost:8000
```

Then run with the `local` profile active (see step 4).

## 4. Run it

In VS Code terminal (View → Terminal), from the project root:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

On Windows:
```bash
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

**Important:** this repo does not include the `mvnw` wrapper scripts or `.mvn/wrapper/` folder (zip size). Before the above command works, run this once in the project root:

```bash
mvn -N io.takari:maven:wrapper -Dmaven=3.9.6
```

This requires Maven installed globally once (`mvn -v` to check, or install via https://maven.apache.org/install.html). After this command generates `mvnw`/`mvnw.cmd`/`.mvn/`, you never need global Maven again — the wrapper handles it.

Alternatively, skip the wrapper entirely and just run `mvn spring-boot:run -Dspring-boot.run.profiles=local` directly if you have Maven installed globally.

## 5. Verify it's running

Visit `http://localhost:8080/actuator/health` — should return `{"status":"UP"}`.

Flyway will auto-run all 5 migrations against your Neon DB on first startup. Check the console log for `Successfully applied 5 migrations`.

## 6. Test an endpoint

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Shree","email":"shree@test.com","password":"Test1234"}'
```

Should return a JSON body with `accessToken`, `refreshToken`, and `user`.

## Known dependency note

`pom.xml` includes `hypersistence-utils-hibernate-63` for JSONB column mapping (used by `Prediction.topFeatures` and `AuditLog.metadata`). This is already wired in — no manual step needed.

## Next phase

The Python FastAPI ML service (`ml.service.base-url`) is Phase 2 — not yet built. Predictions will fail with a 503 until that service exists and is running on port 8000 locally (or deployed on Render).

# TalentLens

AI-powered resume matching for faster, more explainable shortlists.

## Local development

### Backend

```powershell
cd backend
mvn spring-boot:run
```

The backend requires Java 21 and Maven. Interactive API docs are available at `http://localhost:8000/swagger-ui/index.html`.

### Frontend

```powershell
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. Use **Load sample workspace** to try the full flow without preparing documents.

On first launch, create an account from the sign-in page. Accounts and sessions are stored in the local SQLite database at `backend/talentlens.db` (or the mounted Docker data directory). Sign out from the initials button in the workspace header.

## Docker

```powershell
docker compose up --build
```

Open `http://localhost:3000`.

The Java matching engine combines TF-IDF semantic similarity with a normalized skills lexicon. Its API response is deliberately provider-neutral, so a sentence-transformer or OpenAI embedding provider can be added later without changing the frontend contract.

## Render

The root `render.yaml` defines a free Java API web service and a free React static site. Create a Blueprint from the repository in the Render Dashboard. The free API has an ephemeral filesystem and may sleep when idle, so SQLite accounts and sessions can be lost on restart or redeploy. Existing local accounts are not copied to Render.

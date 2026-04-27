# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Development mode with hot reload
./mvnw quarkus:dev

# Build executable uber-JAR (for deployment)
./mvnw clean package -Dquarkus.package.type=uber-jar

# Standard build
./mvnw clean package
```

Tests are required for new features and bug fixes. Always work in TDD: write the tests first, verify they fail, then implement the feature until the tests pass. Never use `-DskipTests`.

## Deployment

See `DEPLOY.md` for full instructions. Key steps:

```bash
./mvnw clean package -Dquarkus.package.type=uber-jar
# Copy JAR to deploy-staging/, then:
gcloud beta run deploy comic-trip --source target/deploy-staging/ --region europe-west9 \
  --no-build --base-image google-22/java21 --command java \
  --args="-jar,comic-trip-agent-1.0-SNAPSHOT-runner.jar"
```

The Gemini API key must be configured in Google Cloud Secret Manager for production.

## Architecture

This is a Quarkus (Java 21) web app that transforms travel photos into pop-art comic strips using Google's Agent Development Kit (ADK) with Gemini models.

### Request Flow

`POST /api/mission-control/upload` (multipart) → `MissionControlResource` → virtual threads (one per image) → `ComicTripAnalyzer` → ADK multi-agent pipeline → results saved to Firestore + GCS → returned to caller

The web UI at `/` and `/trips/{tripId}` renders results via Qute templates, reading from Firestore via `TripService`.

### ADK Multi-Agent Pipeline (`ComicTripAnalyzer`)

The core AI pipeline is a `SequentialAgent` (`main_flow`):

1. **Picture Analyzer** (`LlmAgent`, `gemini-2.5-flash`) — analyzes the image, outputs `{"description": "...", "location": "..."}`
2. **`poi_and_comic_flow`** (`ParallelAgent`) — runs these two agents concurrently:
   - **Comic Illustrator** (`LlmAgent`, `gemini-2.0-flash-preview-image-generation`) — generates a pop-art comic image; a `BeforeModelCallback` intercepts the image bytes, saves locally, and uploads to GCS via `GcsArtifactService`
   - **Points of Interest** (`LlmAgent`, `gemini-2.5-flash` + `GoogleMapsTool`) — queries nearby POIs within 1km

Session state flows between agents: the picture analyzer's JSON output is read by both parallel agents. The ADK `App` is initialized once (`@ApplicationScoped`) with `InMemorySessionService` and `InMemoryRunner`.

### Storage

- **Google Cloud Storage**: Comic PNG images, path `gs://{bucket}/comic_trip_app/comic_trip_user/{tripId}/{imageId}.png/0`
- **Firestore**: Trip metadata in database `comic-trip`, collection `trips`. Documents contain tripId, title, and an array of picture entries (description, location, imageUrl, pointsOfInterest).

### Key Classes

| Class | Role |
|---|---|
| `ComicTripAnalyzer` | ADK agent graph construction and execution |
| `MissionControlResource` | REST upload endpoint, virtual-thread parallelism, trip title generation |
| `TripService` | Firestore read/write |
| `WebResource` | Qute template routing |
| `ComicOutput` | DTO records (Image, Details) |

### Configuration (`application.properties`)

- `quarkus.http.limits.max-body-size=100M` — allows large image uploads
- `quarkus.http.read-timeout=300S` — long timeout for AI processing
- `gcs.bucket` — GCS bucket name (injected into `ComicTripAnalyzer`)

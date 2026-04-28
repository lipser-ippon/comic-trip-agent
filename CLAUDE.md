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

This is a Quarkus (Java 21) web app that transforms travel photos into pop-art comic strips using Google's Agent Development Kit (ADK) with Gemini models. It follows **hexagonal architecture**.

### Request Flow

`POST /api/upload` (multipart) → `MissionControlResource` → virtual threads (one per image) → `ImageAnalysisPort` (`AdkComicAnalyzer`) → ADK multi-agent pipeline → results saved to Firestore + GCS → returned to caller

The web UI at `/` and `/trips/{tripId}` renders results via Qute templates, reading from Firestore via `GetTripUseCase` / `ListTripsUseCase`.

### Package Structure

```
comictrip/
  domain/
    model/        Trip, Picture, AnalysisResult, UploadedImage, ImageAnalysis
    port/in/      CreateTripUseCase, GetTripUseCase, ListTripsUseCase, DeleteTripUseCase
    port/out/     TripRepository, ImageAnalysisPort, TitleGenerationPort, ImageStoragePort
    service/      TripDomainService  (implements all 4 use cases)
    exception/    TripPersistenceException
  adapter/
    in/rest/      MissionControlResource, WebResource, GcsProxyResource, TripPersistenceExceptionMapper
    out/gcp/      FirestoreTripRepository, GcsImageStorage, AdkComicAnalyzer, GeminiTitleGenerator
  infrastructure/ AdkConfig, GoogleCloudConfig, IdGenerator, AdkConstants
```

### ADK Multi-Agent Pipeline (`AdkComicAnalyzer`)

The core AI pipeline is a `SequentialAgent` (`main_flow`) configured in `AdkConfig`:

1. **Picture Analyzer** (`LlmAgent`, `gemini-2.5-flash`) — analyzes the image, outputs `{"description": "...", "location": "..."}`
2. **`poi_and_comic_flow`** (`ParallelAgent`) — runs these two agents concurrently:
   - **Comic Illustrator** (`LlmAgent`, `gemini-2.5-flash-image`) — generates a pop-art comic image; an `afterModelCallback` intercepts the generated image bytes and uploads them to GCS via `GcsArtifactService`
   - **Points of Interest** (`LlmAgent`, `gemini-2.5-flash`) — lists nearby POIs within 1km

Session state flows between agents via `outputKey`. The ADK `App`, `Runner`, and `InMemorySessionService` are `@ApplicationScoped` CDI beans produced by `AdkConfig`.

### Storage

- **Google Cloud Storage**: Comic PNG images, path `gs://{bucket}/comic_trip_app/comic_trip_user/{tripId}/{imageId}.png/0`
- **Firestore**: Trip metadata in database `comic-trip`, collection `trips`. Documents contain title and an array of picture entries (fileName, description, location, mimeType, imageUrl, pointsOfInterest).

### Key Classes

| Class | Role |
|---|---|
| `AdkComicAnalyzer` | Implements `ImageAnalysisPort` — runs the ADK pipeline, returns `AnalysisResult` |
| `MissionControlResource` | REST upload endpoint, virtual-thread parallelism |
| `TripDomainService` | Orchestrates create/get/list/delete use cases |
| `FirestoreTripRepository` | Implements `TripRepository` — Firestore read/write |
| `GcsImageStorage` | Implements `ImageStoragePort` — GCS image deletion |
| `GeminiTitleGenerator` | Implements `TitleGenerationPort` — generates trip titles via Gemini |
| `WebResource` | Qute template routing |
| `AdkConfig` | CDI producers for ADK App, Runner, agents |
| `GoogleCloudConfig` | CDI producers for Firestore, Genai Client, Sqids |

### Configuration (`application.properties`)

- `quarkus.http.limits.max-body-size=100M` — allows large image uploads
- `quarkus.http.read-timeout=300s` — long timeout for AI processing
- `comic-trip.picture.bucket` — GCS bucket name
- `picture_analyzer_agent_model` (default: `gemini-2.5-flash`)
- `comic_illustrator_agent_model` (default: `gemini-2.5-flash-image`)
- `point_of_interest_agent_model` (default: `gemini-2.5-flash`)

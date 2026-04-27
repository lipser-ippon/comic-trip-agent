# Deployment Guide: Comic Trip

This guide details the process for deploying the Comic Trip Quarkus & ADK Java application to Google Cloud Run 
[directly from source](https://docs.cloud.google.com/run/docs/deploying-source-code#deploy_without_build), 
bypassing Cloud Build (`--no-build` flag), running a self-executable JAR 
on a [base Java image](https://docs.cloud.google.com/run/docs/configuring/services/runtime-base-images#java),
and utilizing Secret Manager for the Gemini API key.

## Prerequisites

Before starting, ensure you have the following installed and authenticated:
- **Java 21**
- **Maven Wrapper** (`mvnw` - included in the repository)
- **Google Cloud CLI** (`gcloud`)

Ensure you are authenticated and your active project is set correctly:
```bash
gcloud auth login
gcloud config set project <YOUR_PROJECT_ID>
```

## 1. Build the Application

Since we are deploying without Cloud Build, we need to compile the Quarkus application locally 
into an executable "uber-jar" that includes all necessary dependencies.

```bash
# Build the executable runner JAR
./mvnw clean package -Dquarkus.package.type=uber-jar -DskipTests
```
This command generates the `target/comic-trip-agent-1.0-SNAPSHOT-runner.jar` file.

## 2. Prepare the Staging Directory

Cloud Run's source deploy feature has an upload size limit of 250MB. 
To stay well under this limit and avoid uploading unnecessary files (like target classes or build analytics), 
we isolate the JAR into a clean staging directory.

```bash
# Create a staging directory
mkdir -p target/deploy-staging

# Copy only the executable JAR to the staging directory
cp target/comic-trip-agent-1.0-SNAPSHOT-runner.jar target/deploy-staging/
```

## 3. Deploy to Cloud Run

We deploy the application directly from the staging directory using the `gcloud beta run deploy` command. 

> **Note:** The `--no-build` flag currently requires the `beta` release track of the gcloud CLI.

```bash
gcloud beta run deploy comic-trip \
  --source target/deploy-staging/ \
  --region europe-west1 \
  --no-build \
  --base-image google-22/java21 \
  --command java \
  --args="-jar,comic-trip-agent-1.0-SNAPSHOT-runner.jar" \
  --set-env-vars="GOOGLE_CLOUD_LOCATION=europe-west1,GOOGLE_CLOUD_PROJECT=prj-s-sandbox-lipser-5013,GOOGLE_GENAI_USE_VERTEXAI=True" \
  --allow-unauthenticated
```

**Flag Breakdown:**
- `--source target/deploy-staging/`: Points to the directory containing our JAR.
- `--no-build`: Bypasses Cloud Build, significantly speeding up the deployment process.
- `--base-image google-22/java21`: Tells Cloud Run to use the official Java 21 base image managed by Google.
- `--command java` and `--args="-jar,..."`: Instructs the container how to execute our application.

## 4. Configure Secrets (Gemini API Key)

The application requires a Gemini API key to interact with Google's GenAI models. 
We store this securely in Google Cloud Secret Manager.

### Create the Secret (If not already created)
```bash
# Assuming you named the secret 'comic-trip-gemini-key'
echo -n "YOUR_ACTUAL_API_KEY" | gcloud secrets create comic-trip-gemini-key \
    --data-file=- \
    --replication-policy="automatic"
```

### Grant Access to the Cloud Run Service Account
The default Cloud Run service account must be granted permission to read the secret. 

Replace `PROJECT_NUMBER` below with your actual Google Cloud Project Number 
(found in the console or via `gcloud projects describe <PROJECT_ID>`).

```bash
gcloud secrets add-iam-policy-binding comic-trip-gemini-key \
    --member="serviceAccount:PROJECT_NUMBER-compute@developer.gserviceaccount.com" \
    --role="roles/secretmanager.secretAccessor"
```

### Expose the Secret as an Environment Variable
Finally, update the Cloud Run service to map the Secret Manager secret 
to the `GEMINI_API_KEY` environment variable expected by the application.

```bash
gcloud run services update comic-trip \
    --region europe-west1 \
    --update-secrets=GEMINI_API_KEY=comic-trip-gemini-key:latest
```

## 5. Verify Deployment

Your application is now live. `gcloud` will output the service URL (e.g., `https://comic-trip-has.europe-west1.run.app`). 
Visit this URL in your browser to verify the Comic Trip application is successfully running.

## In a nutshell
```bash
./mvnw clean package -Dquarkus.package.type=uber-jar -DskipTests
mkdir -p target/deploy-staging
cp target/comic-trip-agent-1.0-SNAPSHOT-runner.jar target/deploy-staging/
gcloud beta run deploy comic-trip \
  --source target/deploy-staging/ \
  --region europe-west1 \
  --no-build \
  --base-image google-22/java21 \
  --command java \
  --args="-jar,comic-trip-agent-1.0-SNAPSHOT-runner.jar" \
  --set-env-vars="GOOGLE_CLOUD_LOCATION=europe-west1,GOOGLE_CLOUD_PROJECT=prj-s-sandbox-lipser-5013,GOOGLE_GENAI_USE_VERTEXAI=True" \
  --allow-unauthenticated
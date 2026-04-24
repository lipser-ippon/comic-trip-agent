Architecture : Proxy GCS Sécurisé via Cloud Run (Quarkus)
Ce guide détaille comment exposer des assets privés sans rendre le bucket public.

1. Préparation de l'Infrastructure (CLI gcloud)
   Nous allons définir les variables d'environnement pour automatiser la création des ressources.

Bash
# Variables à adapter
export PROJECT_ID=$(gcloud config get-value project)
export REGION="europe-west1"
export BUCKET_NAME="mon-bucket-images-prive"
export SERVICE_ACCOUNT_NAME="quarkus-gcs-proxy"
export SERVICE_ACCOUNT_EMAIL="${SERVICE_ACCOUNT_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
Étape A : Création de l'identité (IAM)
On crée un Service Account dédié avec le principe du moindre privilège.

Bash
# 1. Créer le Service Account
gcloud iam service-accounts create $SERVICE_ACCOUNT_NAME \
--display-name="SA Proxy Quarkus Cloud Storage"

# 2. Accorder le rôle de lecture seule sur le bucket spécifique
gcloud storage buckets add-iam-policy-binding gs://$BUCKET_NAME \
--member="serviceAccount:$SERVICE_ACCOUNT_EMAIL" \
--role="roles/storage.objectViewer"
2. Développement de l'Application Quarkus
   Configuration Maven (pom.xml)
   Ajoutez l'extension native Google Cloud Storage.

XML
<dependency>
<groupId>io.quarkus</groupId>
<artifactId>quarkus-google-cloud-storage</artifactId>
</dependency>
Configuration Application (src/main/resources/application.properties)
Properties
# Nom du bucket injecté dans le code
bucket.name=mon-bucket-images-prive
# Quarkus détecte auto le SA en prod, pas besoin de clés locales
Code du Resource (GcsProxyResource.java)
Nous utilisons un flux (stream) pour éviter de saturer la mémoire vive du conteneur.

Java
package org.acme;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.OutputStream;

@Path("/media")
public class GcsProxyResource {

    @Inject
    Storage storage;

    @ConfigProperty(name = "bucket.name")
    String bucketName;

    @GET
    @Path("/{imageName}")
    public Uni<Response> getImage(@PathParam("imageName") String imageName) {
        return Uni.createFrom().item(() -> {
            Blob blob = storage.get(bucketName, imageName);

            if (blob == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            // Streaming direct du contenu vers la réponse HTTP
            Response.ResponseBuilder response = Response.ok((OutputStream output) -> {
                blob.downloadTo(output);
            });

            return response
                .type(blob.getContentType())
                .header("Cache-Control", "public, max-age=31536000") // Cache 1 an
                .build();
        });
    }
}
3. Build et Déploiement
   Étape B : Containerisation et Push
   Utilisez Google Cloud Builds pour créer l'image sans avoir Docker installé localement.

Bash
# Build de l'image (le flag -Dnative est optionnel mais recommandé pour Cloud Run)
./mvnw package -Dquarkus.container-image.build=true \
-Dquarkus.container-image.group=gcr.io/$PROJECT_ID \
-Dquarkus.container-image.name=front-proxy-gcs

# Ou via gcloud build direct
gcloud builds submit --tag gcr.io/$PROJECT_ID/front-proxy-gcs .
Étape C : Déploiement sur Cloud Run
C'est ici que l'on lie l'application à son identité sécurisée.

Bash
gcloud run deploy front-proxy-service \
--image gcr.io/$PROJECT_ID/front-proxy-gcs \
--service-account $SERVICE_ACCOUNT_EMAIL \
--region $REGION \
--allow-unauthenticated \
--memory 256Mi \
--cpu 1
4. Validation et Monitoring
   Test d'accès
   Une fois déployé, récupérez l'URL du service :

Bash
SERVICE_URL=$(gcloud run services describe front-proxy-service --region $REGION --format='value(status.url)')
echo "Accès à l'image : ${SERVICE_URL}/media/test.png"
Pourquoi c'est la meilleure solution ?
Sécurité : Le bucket reste en Public Access Prevention. Seul le Service Account du Cloud Run a "la clé".

Performance : En ajoutant un header Cache-Control, les requêtes suivantes seront servies par le cache du navigateur ou les GFE (Google Front Ends).

Coût : Quarkus en mode réactif consomme très peu de CPU pendant le stream, ce qui permet de réduire les instances Cloud Run au minimum.

Note DevOps : Pour une montée en charge massive, n'hésite pas à placer un Cloud CDN devant cette URL Cloud Run pour mettre en cache les images au plus proche de tes utilisateurs.
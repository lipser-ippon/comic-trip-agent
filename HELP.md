---

# 📘 Documentation : Maîtriser le Google ADK en Java (Mode Sécurisé)

Bienvenue, collègue. Ce document détaille l'implémentation de l'**Agent Development Kit (ADK)** de Google en environnement Java, avec une priorité absolue sur la sécurité **"Keyless"** (sans clé JSON).

---

## 🏗️ 1. Concepts Fondamentaux

Avant de coder, accordons nos violons sur la terminologie ADK :

| Concept | Description | Analogie |
| :--- | :--- | :--- |
| **Tool** | Une fonction atomique et typée que l'IA peut appeler. | Un tournevis. |
| **Skill** | Un regroupement logique d'outils traitant un domaine métier. | Une caisse à outils de menuisier. |
| **Agent** | L'entité dotée d'une personnalité et d'un but (System Instruction). | Le menuisier lui-même. |
| **App** | Le runtime qui gère la session et l'orchestration finale. | L'atelier de construction. |

---

## 🔐 2. Configuration & Authentification (Zero-Trust)

Dans ton organisation, les clés JSON sont interdites. Nous utilisons donc les **Application Default Credentials (ADC)**.

### Prérequis Système
1. **Google Cloud CLI** installé.
2. **Authentification locale** :
   ```bash
   gcloud auth application-default login
   ```
3. **IAM Roles** : Ton identité (ou le Service Account en prod) doit avoir le rôle `roles/aiplatform.user`.

### Dépendances Maven (`pom.xml`)
```xml
<dependencies>
    <dependency>
        <groupId>com.google.cloud</groupId>
        <artifactId>google-cloud-adk</artifactId>
        <version>1.0.0</version>
    </dependency>
    <dependency>
        <groupId>com.google.cloud</groupId>
        <artifactId>google-cloud-vertexai</artifactId>
        <version>1.7.0</version>
    </dependency>
</dependencies>
```

---

## 🛠️ 3. Architecture du Code

### Étape 1 : Définition des Tools
Un outil doit être simple. Utilisez l'annotation `@Description` pour aider le LLM à comprendre le *quand*.

```java
import com.google.adk.core.Tool;
import com.google.adk.core.annotations.Description;

public class SupportTools {
    @Tool
    @Description("Récupère le statut d'une commande via son ID.")
    public String getOrderStatus(@Description("L'ID de la commande (ex: ORD-123)") String orderId) {
        // Logique d'appel API ou DB ici
        return "La commande " + orderId + " est en cours de livraison.";
    }
}
```

### Étape 2 : Implémentation de la Skill
La Skill orchestre la logique métier et expose les outils.

```java
import com.google.adk.core.Skill;
import java.util.List;

public class CustomerServiceSkill implements Skill {
    @Override
    public List<Object> getTools() {
        return List.of(new SupportTools());
    }
}
```

### Étape 3 : Instanciation de l'Agent & App
C'est ici que nous configurons l'accès à Vertex AI sans clé.

```java
import com.google.cloud.vertexai.VertexAI;
import com.google.adk.core.Agent;
import com.google.adk.core.App;

public class MainAgentApp {
    public static void main(String[] args) {
        // L'absence de credentials explicites force l'usage de l'ADC (Sécurisé)
        try (VertexAI vertexAI = new VertexAI.Builder()
                .setProjectId("votre-id-projet")
                .setLocation("us-central1")
                .build()) {

            Agent supportAgent = Agent.builder()
                .setName("Support-Bot")
                .setVertexAi(vertexAI)
                .setSystemInstruction("Tu es un agent de support technique. Sois bref et poli.")
                .addSkill(new CustomerServiceSkill())
                .build();

            App app = App.builder().registerAgent(supportAgent).build();
            
            System.out.println(app.chat("Où en est ma commande ORD-99 ?"));
        }
    }
}
```

---

## 🧠 4. Le Guide du Prompt Engineering (System Instruction)

Pour l'ADK, l'instruction système n'est pas une simple phrase, c'est le **garde-fou** de ton agent.

**Structure recommandée :**
1. **Rôle :** "Tu es un [Métier] spécialisé en [Domaine]."
2. **Capacités :** "Tu as accès à des outils pour vérifier [Action]."
3. **Contraintes (Grounding) :** "Si une information est manquante, demande-la. Ne jamais inventer de données."
4. **Ton :** "Réponds de manière professionnelle et concise."

---

## 🚀 5. Bonnes Pratiques "Agentic"

> **Note du Mentor :** Un agent n'est pas un simple chatbot, c'est un système autonome.

* **Stateless vs Stateful :** L'ADK gère l'historique pour toi via l'objet `App`. Ne tente pas de stocker manuellement le contexte dans tes outils.
* **Safety :** Toujours valider les entrées utilisateur dans tes Tools avant de requêter tes bases de données (prévention d'injection).
* **Observabilité :** Utilise les logs de Vertex AI pour surveiller les "Tool Calls" et ajuster tes descriptions si l'IA se trompe d'outil.

---

## 🧪 6. Résolution des problèmes fréquents

* **Erreur 403 (Permission Denied) :** Ton identité n'a pas le rôle `aiplatform.user` sur le projet spécifié.
* **L'IA n'appelle pas le Tool :** Ta `@Description` est trop vague. Sois plus spécifique sur les paramètres attendus.
* **Problème de Quota :** Vertex AI a des limites par minute. Implémente un mécanisme de retry si nécessaire.

---
*Document généré par ADK Master - 2026. Garde ton code propre et tes agents sous contrôle.*
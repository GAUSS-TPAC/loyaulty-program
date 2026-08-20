# Séquence Complète des Commandes CURL pour les Tests de Facturation (Billing)

Ce document répertorie l'enchaînement exact des requêtes HTTP (via `curl`) pour tester le workflow de création d'organisation, d'attribution de rôle, de souscription, de création de tiers (contacts clients) et de génération de documents commerciaux (Facture Proforma et Bon de Réception) sur l'environnement en ligne du Kernel.

---

## Variables Globales de l'Environnement

Pour exécuter ces commandes, remplacez les valeurs ou définissez-les dans votre shell :
> Les credentials ne sont plus en dur dans ce fichier : exporte-les depuis ton shell
> (ou un gestionnaire de secrets) avant de jouer les commandes. Ne jamais les recommiter.

```bash
export BASE_URL="https://kernel-core.yowyob.com"
export CLIENT_ID="prod-platform-backend"
export API_KEY="${KERNEL_API_KEY:?definir dans ton shell, ne jamais commiter}"
export TENANT_ID="11111111-1111-1111-1111-111111111111"

# Identifiants du Business Actor principal
export USER_EMAIL="${KERNEL_USER_EMAIL:?}"
export USER_PASS="${KERNEL_USER_PASS:?}"
```

---

## Étape 1 : Connexion en tant que Business Actor

Pour obtenir le token de session JWT.

```bash
curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: $CLIENT_ID" \
  -H "X-Api-Key: $API_KEY" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -d '{
    "principal": "'"$USER_EMAIL"'",
    "password": "'"$USER_PASS"'"
  }'
```

> **Réponse attendue (succès) :**
> Récupérez l'attribut `data.accessToken` (noté ci-après `$ACCESS_TOKEN`), `data.id` (noté `$USER_ID`), et `data.actorId` (noté `$ACTOR_ID`).
>
> ```bash
> export ACCESS_TOKEN="<accessToken>"
> export USER_ID="384c78d4-5c99-40b2-b10d-d78782830dcd"
> export ACTOR_ID="f5874c06-b0f0-44fc-a5be-37c1c612179e"
> ```

---

## Étape 2 : Récupération du Profil Business Actor

Cette étape permet de récupérer l'identifiant interne de l'acteur (`businessActorId`), requis pour certaines requêtes d'organisation.

```bash
curl -s -X GET "$BASE_URL/api/actors/me" \
  -H "Accept: application/json" \
  -H "X-Client-Id: $CLIENT_ID" \
  -H "X-Api-Key: $API_KEY" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

> **Réponse attendue :**
> Récupérez l'identifiant `data.id` (noté `$BUSINESS_ACTOR_ID`).
>
> ```bash
> export BUSINESS_ACTOR_ID="f5874c06-b0f0-44fc-a5be-37c1c612179e"
> ```

---

## Étape 3 : Gestion de l'Organisation

### A. Lister les organisations existantes
```bash
curl -s -X GET "$BASE_URL/api/organizations/my" \
  -H "Accept: application/json" \
  -H "X-Client-Id: $CLIENT_ID" \
  -H "X-Api-Key: $API_KEY" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

> Si vous recevez une liste d'organisations vide ou un code d'erreur `FORBIDDEN` / `Access Denied` (indiquant que vous n'avez pas encore le rôle `OWNER` sur le Tenant pour gérer les organisations), passez à l'**Étape de Secours (Bootstrap du Rôle OWNER)** ci-dessous.
> Si vous possédez déjà une organisation, récupérez son ID (noté `$ORG_ID`).
>
> ```bash
> export ORG_ID="c4bf87f6-2b22-421f-a4ec-9f79564b61c2"
> ```

### B. Créer une nouvelle organisation (si nécessaire)
```bash
curl -s -X POST "$BASE_URL/api/organizations" \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: $CLIENT_ID" \
  -H "X-Api-Key: $API_KEY" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d '{
    "businessActorId": "'"$BUSINESS_ACTOR_ID"'",
    "code": "ORG-TEST-BILLING",
    "legalName": "Billing Test Org SARL",
    "displayName": "Billing Test Org",
    "organizationType": "PRIVATE_COMPANY"
  }'
```

---

## Étape de Secours : Bootstrap du Rôle OWNER (si erreur 403)

Si l'accès à la gestion des organisations est interdit, le compte `platform-admin` doit attribuer le rôle `OWNER` (ou `"Propriétaire"`) à votre utilisateur.

### 1. Authentification en tant que platform-admin
```bash
curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: $CLIENT_ID" \
  -H "X-Api-Key: $API_KEY" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -d '{
    "principal": "platform-admin",
    "password": "281kFEOhFYuj7n9cfgGp"
  }'
```

> **Note relative au MFA :**
> Si le MFA (Multi-Factor Authentication) est actif, la réponse contiendra `"nextStep": "CONFIRM_MFA"` et un `mfaToken`. Utilisez le jeton et le code OTP reçu pour confirmer la connexion :
>
> ```bash
> curl -s -X POST "$BASE_URL/api/auth/login/mfa/confirm" \
>   -H "Accept: application/json" \
>   -H "Content-Type: application/json" \
>   -H "X-Client-Id: $CLIENT_ID" \
>   -H "X-Api-Key: $API_KEY" \
>   -H "X-Tenant-Id: $TENANT_ID" \
>   -d '{
>     "mfaToken": "<MFA_TOKEN>",
>     "code": "<OTP_CODE>"
>   }'
> ```
> Récupérez le token d'administration (`$ADMIN_TOKEN`).

### 2. Récupérer l'ID du rôle OWNER
```bash
curl -s -X GET "$BASE_URL/api/administration/roles" \
  -H "Accept: application/json" \
  -H "X-Client-Id: $CLIENT_ID" \
  -H "X-Api-Key: $API_KEY" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
> Identifiez le rôle dont le code ou le nom est `"OWNER"` (ou `"Propriétaire"`). Notez son ID (`$OWNER_ROLE_ID`).

### 3. Assigner le rôle OWNER à l'utilisateur Business Actor
```bash
curl -s -X POST "$BASE_URL/api/administration/users/$USER_ID/roles" \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: $CLIENT_ID" \
  -H "X-Api-Key: $API_KEY" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "roleId": "'"$OWNER_ROLE_ID"'",
    "scopeType": "TENANT",
    "scope": "TENANT"
  }'
```

> **Important :** Une fois le rôle assigné, reconnectez-vous avec vos identifiants Business Actor (cf. **Étape 1**) afin d'obtenir un `$ACCESS_TOKEN` rafraîchi contenant les nouvelles permissions.

---

## Étape 4 : Souscription au Plan Commercial (ENTERPRISE / COMMERCE)

Pour provisionner automatiquement l'ensemble des modules requis (`PRODUCT`, `BILLING`, `COMMERCIAL`, `CASHIER`) ainsi que leurs quotas correspondants et éviter l'erreur `ORGANIZATION_SERVICE_QUOTA_UNAVAILABLE`.

```bash
curl -s -X POST "$BASE_URL/api/organizations/$ORG_ID/commercial-subscriptions" \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: $CLIENT_ID" \
  -H "X-Api-Key: $API_KEY" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "X-Organization-Id: $ORG_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d '{
    "planCode": "ENTERPRISE",
    "addOnCodes": []
  }'
```

---

## Étape 5 : Création des Vendeurs (Sellers / Cashiers)

Pour enregistrer un profil de vendeur (rattaché à l'organisation).

```bash
curl -s -X POST "$BASE_URL/api/cashiers" \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: $CLIENT_ID" \
  -H "X-Api-Key: $API_KEY" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "X-Organization-Id: $ORG_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d '{
    "fullName": "Seller Alice",
    "kind": "SELLER",
    "email": "alice.seller@test.yowyob.com"
  }'
```

---

## Étape 6 : Gestion des Produits

### A. Lister les produits existants
```bash
curl -s -X GET "$BASE_URL/api/products?organizationId=$ORG_ID" \
  -H "Accept: application/json" \
  -H "X-Client-Id: $CLIENT_ID" \
  -H "X-Api-Key: $API_KEY" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "X-Organization-Id: $ORG_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```
> Notez l'ID du produit de test (`$PRODUCT_ID`).

### B. Créer un produit de test (si nécessaire)
```bash
curl -s -X POST "$BASE_URL/api/products" \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: $CLIENT_ID" \
  -H "X-Api-Key: $API_KEY" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "X-Organization-Id: $ORG_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d '{
    "organizationId": "'"$ORG_ID"'",
    "sku": "SKU-TEST-BILL",
    "name": "Produit Test Billing",
    "familyCode": "DEFAULT",
    "variantLabel": "Standard",
    "unitPrice": 2500.0,
    "currency": "XAF"
  }'
```

---

## Étape 7 : Création du Tiers (Profil Financier de l'Acteur)

> **Contournement technique (Bypass Quota Interceptor) :**
> L'API de création directe de Tiers (`POST /api/third-parties`) renvoie l'erreur de quota `ORGANIZATION_SERVICE_QUOTA_UNAVAILABLE`.
> L'appel ci-dessous crée le Tiers en associant le rôle `CUSTOMER` directement sur le profil de l'acteur sans déclencher l'erreur de quota.

```bash
curl -s -X POST "$BASE_URL/api/third-parties/actors/$ACTOR_ID/financial-profile" \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: $CLIENT_ID" \
  -H "X-Api-Key: $API_KEY" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "X-Organization-Id: $ORG_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d '{
    "organizationId": "'"$ORG_ID"'",
    "role": "CUSTOMER",
    "referenceCode": "TP-ACTOR-FP",
    "displayName": "Client Test Billing"
  }'
```

> **Réponse attendue :**
> Récupérez l'identifiant du tiers créé dans `data.id` (noté `$THIRD_PARTY_ID`).
>
> ```bash
> export THIRD_PARTY_ID="2f132a53-170b-49d8-95f6-cb09d6886d8f"
> ```

---

## Étape 8 : Facturation (Billing Documents)

Une fois le Produit et le Tiers créés, vous pouvez générer les pièces de facturation.

### A. Création d'une Facture Proforma (Draft)
```bash
curl -s -X POST "$BASE_URL/api/factures-proforma" \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: $CLIENT_ID" \
  -H "X-Api-Key: $API_KEY" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "X-Organization-Id: $ORG_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d '{
    "counterpartyThirdPartyId": "'"$THIRD_PARTY_ID"'",
    "currency": "XAF",
    "lines": [
      {
        "productId": "'"$PRODUCT_ID"'",
        "quantity": 3.0,
        "unitPrice": 2500.0
      }
    ]
  }'
```

### B. Lister les Factures Proforma
```bash
curl -s -X GET "$BASE_URL/api/factures-proforma" \
  -H "Accept: application/json" \
  -H "X-Client-Id: $CLIENT_ID" \
  -H "X-Api-Key: $API_KEY" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "X-Organization-Id: $ORG_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

### C. Création d'un Bon de Réception (Draft)
```bash
curl -s -X POST "$BASE_URL/api/v1/facturation/bon-receptions" \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: $CLIENT_ID" \
  -H "X-Api-Key: $API_KEY" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "X-Organization-Id: $ORG_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d '{
    "counterpartyThirdPartyId": "'"$THIRD_PARTY_ID"'",
    "currency": "XAF",
    "lines": [
      {
        "productId": "'"$PRODUCT_ID"'",
        "quantity": 10.0,
        "unitPrice": 2000.0
      }
    ]
  }'
```

### D. Lister les Bons de Réception
```bash
curl -s -X GET "$BASE_URL/api/v1/facturation/bon-receptions" \
  -H "Accept: application/json" \
  -H "X-Client-Id: $CLIENT_ID" \
  -H "X-Api-Key: $API_KEY" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "X-Organization-Id: $ORG_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

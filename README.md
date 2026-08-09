# BuildFlow ERP — Backend API Reference

> **Version:** 0.1.0 · **Base URL:** `http://localhost:8080` · **Swagger UI:** [/swagger-ui.html](http://localhost:8080/swagger-ui.html)

---

## 1. Project Overview

BuildFlow ERP is a construction industry (BTP) enterprise resource planning system built for Moroccan construction companies. It manages the complete lifecycle of construction projects, from master data (suppliers, employees, sites) through purchasing, inventory, payroll, subcontractor management, and treasury.

**Key Features:**
- JWT-based authentication with role-based access control (9 roles)
- 8 business domains with cross-domain side-effects
- Moroccan financial specifics (20% TVA, CNSS/IR deductions, MAD currency)
- Immutable ledger patterns for financial traceability
- Multi-step workflow state machines (purchase orders, payroll, subcontractor payments)

---

## 2. Tech Stack

| Component | Technology |
|-----------|------------|
| Framework | Spring Boot 4.1.0 (Spring Framework 7.x) |
| Language | Java 25 |
| Database | PostgreSQL 16 |
| ORM | Hibernate 7.4 / Spring Data JPA |
| Migrations | Liquibase |
| Auth | JWT (jjwt 0.12.x) + BCrypt (12 rounds) |
| Mapping | MapStruct 1.6.3 + Lombok |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Container | Docker (multi-stage build, Alpine JRE) |

---

## 3. Architecture Overview

### Modular Monolith

```
com.buildflow.erp
├── common/              # Shared kernel (BaseEntity, ApiResponse, exceptions)
├── config/              # SecurityConfig, OpenApiConfig, JpaAuditingConfig
├── security/            # JWT filter, JwtService, UserPrincipal
└── domain/
    ├── auth/            # Authentication (register, login, JWT)
    ├── referentiel/     # Master data (6 resources)
    ├── achats/          # Purchase orders (4-stage workflow)
    ├── stock/           # Inventory tracking (auto-provisioned)
    ├── tresorerie/      # Cash registers (caisses + transaction ledger)
    ├── salaires/        # Payroll (3-step workflow)
    └── soustraitance/   # Subcontractor contracts + payments
```

Each domain follows the same structure:
```
domain/<name>/
├── controller/    # REST endpoints
├── dto/
│   ├── request/   # Inbound records (validated)
│   └── response/  # Outbound records
├── entity/        # JPA entities + enums
├── mapper/        # MapStruct interfaces
├── repository/    # Spring Data JPA interfaces
└── service/       # Business logic (interface + impl)
```

### Data Flow

```
Client Request
  → JwtAuthenticationFilter (extracts token, sets SecurityContext)
  → Controller (validates @RequestBody, delegates to Service)
  → Service (business rules, cross-domain calls)
  → Repository (JPA → PostgreSQL)
  → MapStruct Mapper (Entity → Response DTO)
  → ApiResponse wrapper
  → JSON response
```

### Cross-Domain Side Effects

```
Achats.validateBL()       → StockService.approvisionnerDepuisAchat()
Achats.validatePaiement() → TresorerieService.debiterPourAchat()
Salaires.payer()          → TresorerieService.debiterPourAchat()
SousTraitance.payer()     → TresorerieService.debiterPourAchat()
SousTraitance.create()    → SousTraitant.nombreContratsActifs++
SousTraitance.payer()     → SousTraitant.montantTotalPaye += montant
```

---

## 4. Setup & Installation

### Prerequisites
- Docker & Docker Compose
- (Optional) Java 25 JDK for local development

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_URL` | JDBC connection string | `jdbc:postgresql://localhost:5433/buildflow` |
| `DB_USERNAME` | PostgreSQL user | `buildflow_dev` |
| `DB_PASSWORD` | PostgreSQL password | `devpassword123` |
| `JWT_SECRET` | HMAC key for JWT signing (min 32 chars) | *(required)* |

### Quick Start (Docker)

```bash
# 1. Clone the repo
git clone <repo-url> && cd buildflow-erp

# 2. Create .env file
cat > .env << EOF
DB_USERNAME=buildflow_dev
DB_PASSWORD=devpassword123
JWT_SECRET=your-super-secret-key-at-least-32-characters-long
EOF

# 3. Launch
docker compose up --build -d

# 4. Verify
curl http://localhost:8080/actuator/health
```

The app starts on **port 8080**, Postgres on **port 5433**.

---

## 5. API Reference

### Response Envelope

All successful responses use this wrapper:

```json
{
  "status": "success",
  "data": { ... },
  "message": null
}
```

### Paginated Responses

Endpoints returning paginated data use `?page=0&size=20&sort=field,asc`:

```json
{
  "status": "success",
  "data": {
    "content": [ ... ],
    "page": 0,
    "size": 20,
    "totalElements": 42,
    "totalPages": 3
  }
}
```

### Money and precision

| Kind | Type | Why |
|------|------|-----|
| Unit prices — `prix_unitaire`, `prix_achat_ref`, `pu_ht` | `DOUBLE PRECISION` | A rate may need more than two decimals (per kg, per m³, per ml) |
| Everything else — line totals, HT/TVA/TTC, caisse balances, salaries, contract amounts | `DECIMAL(15,2)` | These are invoiced, paid and reconciled, so they must stay exact |

A unit price feeds a total that is still rounded HALF_UP to two decimals, so the
extra precision is used in the calculation without leaking sub-centime values
into the ledger.

> `DOUBLE PRECISION` is binary floating point: `0.1` is stored approximately and
> repeated sums drift. Do not compare prices for exact equality, and do not
> extend the type to columns that are summed into a balance.

### Entity codes

Codes, références and matricules are **assigned by the server** on create.
Clients do not send them — the field is absent from every `Create…Request` — and
they never change afterwards, so `PUT` ignores them too.

| Entity | Format | Counter resets |
|--------|--------|----------------|
| Fournisseur | `FRN-001` | never |
| Sous-traitant | `ST-001` | never |
| Employé (matricule) | `EMP-001` | never |
| Article | `ART-001` | never |
| Catégorie article | `CAT-001` | never |
| Chantier | `CH-2026-001` | each year |
| Achat (ref) | `ACH-2026-001` | each year |
| Contrat sous-traitance | `CST-2026-001` | each year |
| Paiement sous-traitant | `PAI-2026-001` | each year |
| Attachement | `ATT-2026-001` | each year |
| Fiche de paie | `FDP-2026-07-001` | each payroll period |
| Caisse | `CAISSE-<code chantier>` | derived from the chantier |

Counters live in the `code_sequences` table, one row per scope, allocated with a
single atomic `INSERT … ON CONFLICT DO UPDATE … RETURNING`. Allocation joins the
caller's transaction, so a failed create releases its number instead of burning
it. Migration `032` seeds every counter above the highest number already in use,
so generated codes cannot collide with records that predate the feature.

> **Exception — `bpu_lignes.ref` stays hand-entered.** Those refs (`1.1`,
> `1.1.a`, …) are transcribed from the client's tender document and are how a
> line is reconciled against it; `BpuExcelParser` exists specifically to preserve
> them on import.

---

### 5.1 Authentication — `/api/v1/auth`

#### `POST /api/v1/auth/register` — Register a new user
**Public**

```json
{ "email": "admin@buildflow.ma", "password": "securepass123", "role": "ADMIN" }
```

Response `201`: `{ "accessToken": "eyJ...", "email": "admin@buildflow.ma", "role": "ADMIN" }`

Roles: `ADMIN`, `DIRECTEUR`, `CHEF_CHANTIER`, `MAGASINIER`, `RH`, `FINANCE`, `PM`, `ACHAT`, `VIEWER`

#### `POST /api/v1/auth/login` — Authenticate
**Public** — Same request/response format.

#### `GET /api/v1/auth/me` — Get current user
**Authenticated** — Returns `{ "accessToken": null, "email": "...", "role": "..." }`

---

### 5.2 Categories Articles — `/api/v1/categories-articles`

| Method | Endpoint | Roles | Description |
|--------|----------|-------|-------------|
| `POST` | `/` | `ADMIN`, `ACHAT` | Create category |
| `GET` | `/{id}` | Authenticated | Get by ID |
| `GET` | `/` | Authenticated | List (paginated) |

**Create Request:**
```json
{ "libelle": "Matériaux de construction", "parentId": null }
```

**Response:** `{ "id": "uuid", "code": "CAT-001", "libelle": "...", "parentId": null }` — `code` is server-assigned.

---

### 5.3 Articles — `/api/v1/articles`

| Method | Endpoint | Roles | Description |
|--------|----------|-------|-------------|
| `POST` | `/` | `ADMIN`, `ACHAT` | Create article |
| `GET` | `/{id}` | Authenticated | Get by ID |
| `GET` | `/` | Authenticated | List (paginated) |

**Create Request:**
```json
{
  "designation": "Ciment CPJ 45 - Sac 50kg",
  "description": "Ciment Portland composé", "categorieId": "uuid",
  "unite": "SAC", "prixAchatRef": 75.00, "tvaRate": 20.00,
  "fournisseursPreferentiels": ["LAFARGE", "CIMAR"]
}
```

**Response:** `id`, `code`, `designation`, `description`, `categorieId`, `categorieLibelle`, `unite`, `prixAchatRef`, `tvaRate`, `actif`, `fournisseursPreferentiels`

---

### 5.4 Fournisseurs — `/api/v1/fournisseurs`

| Method | Endpoint | Roles | Description |
|--------|----------|-------|-------------|
| `POST` | `/` | `ADMIN`, `ACHAT` | Create supplier |
| `GET` | `/{id}` | Authenticated | Get by ID |
| `GET` | `/` | Authenticated | List all |

**Create Request:**
```json
{
  "code": "FRN-001", "raisonSociale": "Lafarge Maroc", "ice": "001234567000012",
  "contact": "Ahmed Benali", "telephone": "+212 522 123456",
  "email": "contact@lafarge.ma", "ville": "Casablanca",
  "adresse": "Zone industrielle Ain Sebaa", "rib": "007 780 0001234567890 12",
  "banque": "Attijariwafa Bank", "statut": "ACTIF",
  "categorieArticles": ["Ciment", "Béton"]
}
```

Statuts: `ACTIF`, `INACTIF`, `BLACKLISTE`

---

### 5.5 Chantiers — `/api/v1/chantiers`

| Method | Endpoint | Roles | Description |
|--------|----------|-------|-------------|
| `POST` | `/` | `ADMIN`, `DIRECTEUR`, `PM`, `CHEF_CHANTIER` | Create site (auto-provisions its Caisse) |
| `PUT` | `/{id}` | `ADMIN`, `DIRECTEUR`, `PM`, `CHEF_CHANTIER` | Update site |
| `PATCH` | `/{id}/demarrer` | `ADMIN`, `DIRECTEUR`, `PM`, `CHEF_CHANTIER` | `EN_PREPARATION` → `EN_COURS` |
| `DELETE` | `/{id}` | `ADMIN`, `DIRECTEUR` | Delete site (see rules below) |
| `GET` | `/{id}` | Authenticated | Get by ID |
| `GET` | `/` | Authenticated | List all |

**Create Request:** (`code` is server-assigned)
```json
{
  "nom": "Résidence Al Firdaws", "client": "Groupe Addoha",
  "adresse": "Lot 23, Zone Tamaris", "ville": "Casablanca", "statut": "EN_COURS",
  "dateDebut": "2026-01-15", "dateFin": "2027-06-30", "budgetHt": 15000000.00,
  "chefProjetNom": "Karim Alaoui", "soustraitantsActifs": ["ElectroPro"],
  "jalons": [{ "libelle": "Fondations terminées", "datePrevue": "2026-04-30", "statut": "TERMINE" }]
}
```

Statuts: `EN_PREPARATION`, `EN_COURS`, `EN_PAUSE`, `TERMINE`, `ANNULE`
Jalon Statuts: `A_FAIRE`, `EN_COURS`, `TERMINE`, `EN_RETARD`

#### Deletion rules

`POST /` auto-provisions a Caisse for each new chantier, so a chantier always has
at least one child row. `DELETE /{id}` therefore applies explicit rules rather
than relying on the database:

| Related data | Behaviour |
|--------------|-----------|
| Jalons, lignes BPU | Deleted with the chantier |
| Caisse with **no** operations | Deleted with the chantier |
| Employés (`chantierActuel`) | Unlinked (set to `NULL`) |
| Achats, opérations de caisse, fiches de paie, contrats de sous-traitance, attachements, lignes de stock | **Blocked** — `409` naming what to remove first |

The `409` body is an RFC 7807 `ProblemDetail` whose `detail` lists the blocking
records, e.g. *"Ce chantier ne peut pas être supprimé : il est encore référencé
par 3 commandes d'achat, 1 opération de caisse. Supprimez ou réaffectez ces
éléments avant de réessayer."*

---

### 5.6 Employés — `/api/v1/employes`

| Method | Endpoint | Roles | Description |
|--------|----------|-------|-------------|
| `POST` | `/` | `ADMIN`, `RH`, `DIRECTEUR` | Create employee |
| `GET` | `/{id}` | `ADMIN`, `RH`, `FINANCE`, `PM`, `DIRECTEUR` | Get by ID |
| `GET` | `/` | Same as above | List all |

**Create Request:**
```json
{
  "nom": "Benjelloun", "prenom": "Omar",
  "role": "CHEF_EQUIPE", "poste": "Chef d'équipe maçonnerie",
  "departement": "Production", "telephone": "+212 661 234567",
  "email": "omar.b@buildflow.ma", "dateEmbauche": "2024-03-15",
  "chantierActuelId": "uuid", "statut": "ACTIF", "salaireBrut": 8500.00,
  "typeContrat": "CDI"
}
```

EmployeRole: `ADMIN`, `RH`, `FINANCE`, `PM`, `ACHAT`, `CONDUCTEUR_TRAVAUX`, `CHEF_EQUIPE`, `OUVRIER`
TypeContrat: `CDI`, `CDD`, `ANAPEC`, `JOURNALIER`

---

### 5.7 Sous-Traitants (Master Data) — `/api/v1/sous-traitants`

| Method | Endpoint | Roles | Description |
|--------|----------|-------|-------------|
| `POST` | `/` | `ADMIN`, `PM`, `ACHAT` | Create subcontractor |
| `GET` | `/{id}` | Authenticated | Get by ID |
| `GET` | `/` | Authenticated | List all |

**Create Request:**
```json
{
  "raisonSociale": "ElectroPro SARL", "ice": "002345678000015",
  "specialite": "Electricité BT/HT", "contact": "Youssef Tahiri",
  "telephone": "+212 661 987654", "email": "contact@electropro.ma",
  "ville": "Rabat", "adresse": "Avenue Mohammed V", "statut": "ACTIF"
}
```

Response includes auto-updated: `nombreContratsActifs`, `montantTotalPaye`

---

### 5.8 Achats (Purchase Orders) — `/api/v1/achats`

| Method | Endpoint | Roles | Description |
|--------|----------|-------|-------------|
| `POST` | `/` | `ADMIN`, `ACHAT` | Create purchase order |
| `GET` | `/{id}` | Authenticated | Get by ID |
| `GET` | `/` | Authenticated | List all |
| `PATCH` | `/{id}/validate-bl?bonLivraisonRef=BL-001` | `ADMIN`, `ACHAT`, `PM` | Confirm delivery → Stock provisioned |
| `PATCH` | `/{id}/validate-facture?factureRef=FA-001` | `ADMIN`, `FINANCE` | Record invoice |
| `PATCH` | `/{id}/validate-paiement` | `ADMIN`, `FINANCE` | Pay → Caisse debited |
| `PATCH` | `/{id}/indicateurs` | `ADMIN`, `ACHAT`, `FINANCE` | Toggle the billing indicators |
| `PATCH` | `/{id}/lignes/{ligneId}/prix` | `ADMIN`, `ACHAT`, `FINANCE` | Re-price one order line |

**Create Request:** (`ref` is server-assigned — see [Entity codes](#entity-codes))
```json
{
  "fournisseurId": "uuid", "chantierId": "uuid",
  "dateCommande": "2026-07-01", "dateLivraisonPrevue": "2026-07-10",
  "lignes": [{ "articleId": "uuid", "quantite": 100.000, "prixUnitaire": 75.00 }],
  "impactAnalytiqueChantier": true,
  "impactComptableFiscal": false
}
```

#### Re-pricing a line

```json
PATCH /api/v1/achats/{id}/lignes/{ligneId}/prix
{ "prixUnitaire": 90.50 }
```

The line total and the order's HT/TVA/TTC are recomputed and the whole updated
order is returned. Permitted at **every** statut; the envelope's `message` says
what that leaves out of step downstream:

| Statut | `message` |
|--------|-----------|
| `EN_COURS`, `LIVRE` | `null` — nothing downstream has happened |
| `FACTURE` | the recorded invoice no longer matches the order |
| `PAYE` | the caisse was debited for the **old** TTC; post an adjusting entry for the difference |

Stock is unaffected either way — provisioning keys off quantity, not price.

**Response includes:** `ht`, `tva` (20%), `ttc` (server-computed), `lignes[]` with snapshots of article data, `bonLivraisonRef`, `factureRef`, `impactAnalytiqueChantier`, `impactComptableFiscal`

#### Operational billing indicators

Both flags are optional on create and default to `false`.

| Field | Question shown in the UI | Meaning |
|-------|--------------------------|---------|
| `impactAnalytiqueChantier` | *L'achat a-t-il réellement servi au chantier ?* | The spend belongs in the site's analytic cost |
| `impactComptableFiscal` | *Y a-t-il une facture officielle à déclarer ?* | An official invoice exists and must be declared |

`PATCH /{id}/indicateurs` takes a partial body — omitting a field leaves it unchanged:

```json
{ "impactComptableFiscal": true }
```

The same two fields exist on cash operations (see Trésorerie). When an achat is paid,
the generated caisse debit inherits the achat's two flags.

#### Statut flow — strictly sequential, no step may be skipped

`EN_COURS` → `LIVRE` → `FACTURE` → `PAYE`

| Transition | Roles | Data required | Automatic effect |
|------------|-------|---------------|------------------|
| `EN_COURS` → `LIVRE` | `ADMIN`, `ACHAT`, `PM` | bon de livraison ref | Provisions the chantier's stock (one `ENTREE` per line, traced on the order ref) |
| `LIVRE` → `FACTURE` | `ADMIN`, `FINANCE` | facture ref | None |
| `FACTURE` → `PAYE` | `ADMIN`, `FINANCE` | — | Debits the chantier's caisse by the TTC |

Before settling, the caisse balance must be ≥ the order TTC, otherwise `422`
`Insufficient funds in caisse '…'. Solde: X, Debit: Y`. Credit the caisse first.

Out-of-order calls return `422`, e.g. `Cannot FACTURE an Achat that is currently
'EN_COURS'. Expected status: 'LIVRE'`.

All three transitions are driven from the Actions column of the Achats table,
which offers only the one step valid at the row's current statut and only to
roles the server would accept.

---

### 5.9 Stock — `/api/v1/stocks`

| Method | Endpoint | Roles | Description |
|--------|----------|-------|-------------|
| `GET` | `/chantiers/{chantierId}` | `ADMIN`, `MAGASINIER`, `PM`, `CHEF_CHANTIER` | Get inventory per site (paginated) |

**Response:**
```json
{
  "id": "uuid", "articleCode": "CIM-50", "designation": "Ciment CPJ 45",
  "unite": "SAC", "chantierId": "uuid", "chantierNom": "Résidence Al Firdaws",
  "quantiteTheorique": 100.000, "seuilAlerte": 20.000, "enAlerte": false
}
```

> Stock is auto-provisioned when an Achat transitions to LIVRE. No manual creation endpoint.

#### Emplacement — Dépôt central vs En Travaux

Stock has a location, expressed by `chantier_id` itself:

| `chantier_id` | `emplacement` | Meaning |
|---------------|---------------|---------|
| `NULL` | `DEPOT` | Held in the central dépôt, not yet allocated |
| set | `CHANTIER` | Allocated to that site — "en travaux" |

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/depot` | Stock held in the central dépôt |
| `GET` | `/chantiers/{chantierId}` | Stock allocated to one chantier |

A movement with no `chantierId` targets the dépôt, so an `ENTREE` without one
receives goods into the warehouse. `TRANSFERT` — previously rejected as
unsupported — now moves quantity between two locations using
`chantierDestinationId` (null for the dépôt), in either direction, and is
recorded as two movement lines sharing a reference.

The dashboard's `valeurStocksDepotHt` and `valeurStocksEnTravauxHt` partition
`valeurStocksGlobaleHt` by this location and always sum back to it. Before this,
both figures were hardcoded to `0` on the dashboard card because nothing in the
model could tell the two apart.

> Purchases still land directly on the ordering chantier: an Achat is placed for
> a site, so `validate-bl` provisions that site, not the dépôt. Use a manual
> `ENTREE` with no chantier to receive a bulk purchase into the warehouse.

#### `seuilAlerte` and `enAlerte`

`enAlerte` is computed in `StockMapper`:

```
enAlerte = quantiteTheorique <= seuilAlerte  AND  seuilAlerte > 0
```

The `> 0` guard is deliberate: a threshold of `0` means *no threshold set*, not
*alert on everything*.

> ⚠️ **`seuilAlerte` is never populated.** A `StockArticle` is created with the
> field at `0` (entity initialiser and column default), and nothing writes to it
> — there is no field on any request DTO and no update endpoint. The guard above
> is therefore always false, so **`enAlerte` is always `false`**, the column
> always reads `0`, and the "Alertes seuil" KPI is always `0`.
>
> The formula is correct; what is missing is a way to set the threshold. Making
> the feature live needs either an editable field per stock line or a default on
> the catalogue `Article` copied onto each line.

---

### 5.10 Trésorerie (Cash Registers) — `/api/v1/caisses`

| Method | Endpoint | Roles | Description |
|--------|----------|-------|-------------|
| `POST` | `/` | `ADMIN`, `FINANCE` | Create cash register |
| `GET` | `/` | `ADMIN`, `FINANCE`, `DIRECTEUR` | List all |
| `GET` | `/{id}` | `ADMIN`, `FINANCE`, `DIRECTEUR`, `CHEF_CHANTIER` | Get by ID (includes transactions) |
| `POST` | `/{id}/transactions` | `ADMIN`, `FINANCE` | Credit or debit |
| `GET` | `/{id}/transactions` | `ADMIN`, `FINANCE`, `DIRECTEUR` | Transaction history |
| `PATCH` | `/{id}/transactions/{transactionId}/indicateurs` | `ADMIN`, `FINANCE` | Toggle the billing indicators |

**Create Caisse:**
```json
{ "libelle": "Caisse chantier Al Firdaws", "chantierId": "uuid", "seuilMinimum": 50000.00 }
```

**Create Transaction:**
```json
{
  "typeTransaction": "CREDIT", "montant": 200000.00,
  "motif": "Approvisionnement initial", "referenceDocument": "VIR-001",
  "impactAnalytiqueChantier": false,
  "impactComptableFiscal": false
}
```

TypeTransaction: `CREDIT`, `DEBIT`

> DEBIT rejected if balance would go negative → `422`

Cash operations carry the same two optional billing indicators as achats
(both default to `false`) — see [Achats](#58-achats-purchase-orders--apiv1achats).

---

### 5.11 Salaires (Payroll) — `/api/v1/salaires`

| Method | Endpoint | Roles | Description |
|--------|----------|-------|-------------|
| `POST` | `/` | `ADMIN`, `RH` | Create payslip |
| `GET` | `/` | `ADMIN`, `RH`, `FINANCE`, `DIRECTEUR` | List all (optional `?periode=2026-07`) |
| `GET` | `/{id}` | Same as above | Get by ID |
| `PATCH` | `/{id}/valider` | `ADMIN`, `RH` | HR validates |
| `PATCH` | `/{id}/payer` | `ADMIN`, `FINANCE` | Finance pays → Caisse debited |

**Create Request:**
```json
{
  "employeId": "uuid", "chantierId": "uuid",
  "periode": "2026-07", "joursTravailles": 26, "salaireBase": 8500.00,
  "heuresSupplementaires": 12.00, "montantHeuresSupp": 750.00,
  "primeTransport": 500.00, "primePanier": 650.00, "autresPrimes": 0.00,
  "avance": 2000.00, "deductionsCnss": 544.32, "deductionsIr": 850.00
}
```

`netAPayer` is computed server-side. Unique constraint on (employe, periode).

Statut Flow: `BROUILLON` → `VALIDEE` → `PAYEE`

---

### 5.12 Contrats Sous-Traitant — `/api/v1/contrats-sous-traitant`

| Method | Endpoint | Roles | Description |
|--------|----------|-------|-------------|
| `POST` | `/` | `ADMIN`, `DIRECTEUR` | Create contract |
| `GET` | `/` | `ADMIN`, `DIRECTEUR`, `FINANCE` | List all (optional `?chantierId=uuid`) |
| `GET` | `/{id}` | `ADMIN`, `DIRECTEUR`, `FINANCE`, `CHEF_CHANTIER` | Get by ID |
| `PATCH` | `/{id}/terminer` | `ADMIN`, `DIRECTEUR` | Terminate contract |
| `POST` | `/{contratId}/paiements` | `ADMIN`, `FINANCE` | Create payment |
| `GET` | `/{contratId}/paiements` | `ADMIN`, `DIRECTEUR`, `FINANCE` | List payments |
| `PATCH` | `/paiements/{id}/valider` | `ADMIN`, `DIRECTEUR` | Approve payment |
| `PATCH` | `/paiements/{id}/payer` | `ADMIN`, `FINANCE` | Pay → Caisse debited |

**Create Contract:**
```json
{
  "sousTraitantId": "uuid", "chantierId": "uuid",
  "objet": "Lot Electricité - Résidence Al Firdaws",
  "montantHt": 450000.00, "dateDebut": "2026-03-01", "dateFin": "2026-12-31"
}
```

TVA/TTC computed server-side. Response includes `resteAPayer` (computed: `montantTtc - montantPaye`).

**Create Payment:**
```json
{ "montant": 100000.00, "motif": "Situation n°1 - Câblage RDC" }
```

> Payment cannot exceed `resteAPayer` → `422`

ContratStatut: `EN_COURS`, `TERMINE`, `RESILIE`
PaiementStatut: `EN_ATTENTE` → `VALIDE` → `PAYE`

---

### 5.13 Mode de paiement — `/api/v1/mode-paiement`

Every payable document — a commande (`Achat`), a fiche de paie and a paiement
sous-traitant — records **how** it was settled:

| Value | Meaning | Debits the chantier's caisse? |
|-------|---------|-------------------------------|
| `VIREMENT` | Bank transfer | No |
| `CHEQUE` | Cheque | No |
| `EFFET` | Bill of exchange / lettre de change | No |
| `CAISSE` | Cash, out of the chantier's caisse | **Yes** |

`CAISSE` is no longer a default — the mode is null until the document is paid,
and the payer chooses one explicitly in a popup. It stays selectable so cash
payouts remain possible, and records paid before this feature are backfilled to
`CAISSE`, which is exactly what happened to them.

**Setting the mode** happens on each module's own payment endpoint:

| Endpoint | Mode passed as |
|----------|----------------|
| `PATCH /achats/{id}/validate-paiement` | `?modePaiement=` |
| `PATCH /salaires/{id}/payer` | body `{ "modePaiement": … }` |
| `PATCH /contrats-sous-traitant/paiements/{id}/payer` | `?modePaiement=` |

> Only `CAISSE` runs the caisse debit, so the §8.2 prerequisite about the caisse
> balance — and its `422 Insufficient funds` — now applies to cash settlement
> only. A virement, cheque or effet settles regardless of the caisse balance.

**Correcting the mode afterwards**, for all three document types:

| Method | Endpoint | Roles |
|--------|----------|-------|
| `PATCH` | `/{typeDocument}/{documentId}` | `ADMIN`, `FINANCE` |
| `GET` | `/{typeDocument}/{documentId}/historique` | `ADMIN`, `FINANCE`, `DIRECTEUR` |

`typeDocument` is `ACHAT`, `FICHE_PAIE` or `PAIEMENT_SOUS_TRAITANT`. Only a
settled document can be corrected; anything else returns `422`.

> **The caisse is never adjusted by a correction.** Switching a paid document off
> `CAISSE` does not credit the cash back, and switching onto it does not debit —
> that would move real money on the back of what is meant to be a relabelling.
> The response carries an `avertissement` in both directions so a corrective
> entry can be made deliberately.

#### Audit trail

Every assignment and change appends one immutable row to
`mode_paiement_historique` (old mode, new mode, who, when), including the first
assignment at payment time, where the old mode is null. Re-selecting the same
mode is not a change and appends nothing.

```json
[
  { "ancienMode": "VIREMENT", "nouveauMode": "CHEQUE",
    "modifiePar": "finance@buildflow.ma", "dateModification": "2026-08-09T14:52:52" },
  { "ancienMode": null, "nouveauMode": "VIREMENT",
    "modifiePar": "finance@buildflow.ma", "dateModification": "2026-08-09T14:52:19" }
]
```

---

## 6. Authentication & Authorization

### How It Works

1. Register or login to get a JWT `accessToken`
2. Include in every request: `Authorization: Bearer eyJ...`
3. Token expires after 24 hours
4. `GET /api/v1/auth/me` verifies your token

### Public Endpoints
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `GET /actuator/health`
- `GET /swagger-ui/**`
- `GET /v3/api-docs/**`

### Roles Summary

| Role | Access |
|------|--------|
| `ADMIN` | Everything |
| `DIRECTEUR` | Projects, contracts, financial oversight |
| `FINANCE` | Payments, treasury, payroll validation |
| `RH` | Employees, payroll creation |
| `PM` | Project management, some purchasing |
| `ACHAT` | Purchasing, articles, suppliers |
| `CHEF_CHANTIER` | Site operations, inventory |
| `MAGASINIER` | Inventory |
| `VIEWER` | Read-only |

---

## 7. Data Models — All Enums Reference

| Enum | Values |
|------|--------|
| Role (auth) | `ADMIN`, `DIRECTEUR`, `CHEF_CHANTIER`, `MAGASINIER`, `RH`, `FINANCE`, `PM`, `ACHAT`, `VIEWER` |
| ChantierStatut | `EN_PREPARATION`, `EN_COURS`, `EN_PAUSE`, `TERMINE`, `ANNULE` |
| JalonStatut | `A_FAIRE`, `EN_COURS`, `TERMINE`, `EN_RETARD` |
| FournisseurStatut | `ACTIF`, `INACTIF`, `BLACKLISTE` |
| SousTraitantStatut | `ACTIF`, `INACTIF`, `BLACKLISTE` |
| EmployeStatut | `ACTIF`, `CONGE`, `INACTIF` |
| EmployeRole (HR) | `ADMIN`, `RH`, `FINANCE`, `PM`, `ACHAT`, `CONDUCTEUR_TRAVAUX`, `CHEF_EQUIPE`, `OUVRIER` |
| TypeContrat | `CDI`, `CDD`, `ANAPEC`, `JOURNALIER` |
| AchatStatut | `EN_COURS`, `LIVRE`, `FACTURE`, `PAYE` |
| TypeMouvement | `ENTREE`, `SORTIE`, `AJUSTEMENT` |
| TypeTransaction | `CREDIT`, `DEBIT` |
| FichePaieStatut | `BROUILLON`, `VALIDEE`, `PAYEE` |
| ContratStatut | `EN_COURS`, `TERMINE`, `RESILIE` |
| PaiementStatut | `EN_ATTENTE`, `VALIDE`, `PAYE` |

---

## 8. Error Handling

All errors use RFC 9457 Problem Details format:

| Status | When | Example `detail` |
|--------|------|-------------------|
| `400` | Validation failure | `"Validation failed"` + `errors` map |
| `403` | Wrong role | `"You do not have permission"` |
| `404` | Resource not found | `"Chantier not found with id: ..."` |
| `409` | Duplicate | `"A fiche de paie already exists for this employee for period 2026-07"` |
| `422` | Business rule | `"Insufficient funds in caisse 'CAISSE-CH001'. Solde: 5000.00, Debit: 9000.00"` |
| `500` | Unhandled | `"An unexpected error occurred"` |

---

## 9. Integration Guidelines for Frontend

### Step-by-Step Integration

```javascript
// 1. Auth — get token
const { data } = await axios.post('/api/v1/auth/login', { email, password });
const token = data.data.accessToken;
axios.defaults.headers.common['Authorization'] = `Bearer ${token}`;

// 2. Setup — create master data
// Categories → Articles → Fournisseurs → Chantiers → Employes → SousTraitants

// 3. Create a Caisse for each chantier (REQUIRED before any payment)
await axios.post('/api/v1/caisses', { libelle, chantierId, seuilMinimum });

// 4. Fund the caisse with CREDIT transactions
await axios.post(`/api/v1/caisses/${id}/transactions`, {
  typeTransaction: 'CREDIT', montant: 500000, motif: 'Initial funding'
});

// 5. Now you can run payment workflows (Achats, Salaires, Sous-traitance)
```

### Critical Rules
1. **Create and fund a Caisse before any payment flow** — all payments debit the caisse
2. **Workflow transitions are strict** — can't skip steps
3. **Unique constraints enforced** — duplicate codes/refs return `409`
4. **Dates are ISO format** — `"2026-07-01"` for dates, `"2026-07"` for periods
5. **Money is numbers** — send `75.00` not `"75.00"`

### CORS
Pre-configured for `http://localhost:3000`. Change via `app.cors.allowed-origin`.

---

## 10. Notes & Assumptions

### Limitations
- No UPDATE/DELETE endpoints — only CREATE and workflow transitions
- No file upload — future feature
- Stock is theoretical only (no physical inventory reconciliation)
- Threshold alerts are logged server-side only
- Single-tenant

### Database
- 18 Liquibase migrations
- All IDs are UUID v4
- All entities have auto-managed `created_at` / `updated_at` timestamps

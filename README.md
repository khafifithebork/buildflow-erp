# BuildFlow ERP — Backend

Construction ERP for Moroccan BTP companies: purchasing, stock, payroll, treasury, subcontracting, accounting. Spring Boot + PostgreSQL, REST API under `/api/v1`.

**Swagger:** [localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) — the always-current endpoint list, generated from the code.

---

## Get it running

**~5 minutes**, Docker is enough. No local Java needed.

```bash
git clone <repo-url> && cd buildflow-erp
```

```bash
cat > .env << 'EOF'
DB_USERNAME=buildflow_dev
DB_PASSWORD=devpassword123
JWT_SECRET=a-secret-key-of-at-least-32-characters-here
EOF
```

```bash
docker compose up --build -d
```

```bash
curl http://localhost:8080/actuator/health
```

Expect `{"status":"UP"}`. The API listens on **8080**, Postgres on **5433**.

First boot applies the 45 Liquibase migrations and builds the schema. Allow 1 to 2 minutes before `health` answers.

### Your first authenticated call

**~2 minutes.** Everything except `register`, `login` and `health` needs a token.

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"me@test.ma","password":"MyPassword123!","role":"ADMIN"}'
```

A new account starts pending. Approve it, or login fails:

```bash
docker compose exec postgres psql -U buildflow_dev -d buildflow \
  -c "UPDATE users SET status='APPROVED' WHERE email='me@test.ma';"
```

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"me@test.ma","password":"MyPassword123!"}'
```

The token is at `data.accessToken` and lasts 24 hours. Then:

```bash
curl http://localhost:8080/api/v1/articles \
  -H "Authorization: Bearer <the-token>"
```

---

## The 5 traps that sink an integration

Read these five lines before writing your first call. They are the ones that cost an afternoon.

1. **Create and fund a caisse before any payment.** Purchases, payroll and subcontracting all debit the chantier's caisse. With no funded caisse, every payment returns `422`.
2. **Codes are server-assigned.** Never send `code`, `ref` or `matricule` — the field is absent from every create request and never changes afterwards.
3. **Workflow transitions are strict.** An order goes `EN_COURS → LIVRE → FACTURE → PAYE`, no skipping. Out of sequence: `422`.
4. **Responses are enveloped.** The payload is under `data`, never at the root. `data.accessToken`, not `accessToken`.
5. **Money is a number, not a string.** Send `75.00`, not `"75.00"`. Dates are ISO: `"2026-07-01"`, and `"2026-07"` for a payroll period.

### What to create, in order

Nothing can be created in a vacuum — each step needs the one before it.

```
Categories → Articles ─┐
Fournisseurs ──────────┼→ Achats
Chantiers ─────────────┤
  └→ Caisse (+ CREDIT) ─┴→ any payment
Employes ──────────────→ Payroll
Sous-traitants ────────→ Contracts
```

```javascript
// 1. token
const { data } = await axios.post('/api/v1/auth/login', { email, password });
axios.defaults.headers.common.Authorization = `Bearer ${data.data.accessToken}`;

// 2. the chantier's caisse, then fund it
const caisse = await axios.post('/api/v1/caisses', { libelle, chantierId, seuilMinimum });
await axios.post(`/api/v1/caisses/${caisse.data.data.id}/transactions`, {
  typeTransaction: 'CREDIT', montant: 500000, motif: 'Initial funding'
});

// 3. payment workflows are now open
```

**CORS**: `http://localhost:3000` by default. Change `app.cors.allowed-origin`.

---

## Environment variables

| Variable | Purpose | Default |
|----------|---------|---------|
| `DB_URL` | JDBC connection string | `jdbc:postgresql://localhost:5433/buildflow` |
| `DB_USERNAME` | PostgreSQL user | `buildflow_dev` |
| `DB_PASSWORD` | PostgreSQL password | `devpassword123` |
| `JWT_SECRET` | HMAC signing key, 32 characters minimum | **required** |

---

## Roles

Nine roles, held in an enum column on `users`. There is no `roles` table and no `user_role` table.

| Role | Access |
|------|--------|
| `ADMIN` | everything |
| `DIRECTEUR` | projects, contracts, financial oversight |
| `FINANCE` | payments, treasury, payroll validation |
| `RH` | employees, payroll creation |
| `PM` | project management, some purchasing |
| `ACHAT` | purchasing, articles, suppliers |
| `CHEF_CHANTIER` | site operations, stock |
| `MAGASINIER` | stock |
| `VIEWER` | read-only |

**Public endpoints**, the only ones that need no token: `POST /auth/register`, `POST /auth/login`, `GET /actuator/health`, `GET /swagger-ui/**`, `GET /v3/api-docs/**`.

---

## Tech stack

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

## API conventions

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

## Endpoints

### Authentication — `/api/v1/auth`

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

### Categories Articles — `/api/v1/categories-articles`

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

### Articles — `/api/v1/articles`

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

### Fournisseurs — `/api/v1/fournisseurs`

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

### Chantiers — `/api/v1/chantiers`

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

### Employés — `/api/v1/employes`

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

### Sous-Traitants (Master Data) — `/api/v1/sous-traitants`

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

### Achats (Purchase Orders) — `/api/v1/achats`

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

### Stock — `/api/v1/stocks`

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

### Trésorerie (Cash Registers) — `/api/v1/caisses`

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
(both default to `false`) — see [Achats](#achats-purchase-orders--apiv1achats).

---

### Salaires (Payroll) — `/api/v1/salaires`

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

### Contrats Sous-Traitant — `/api/v1/contrats-sous-traitant`

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

### Mode de paiement — `/api/v1/mode-paiement`

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

## Enums

| Enum | Values |
|------|--------|
| `Role` (auth) | `ADMIN`, `DIRECTEUR`, `CHEF_CHANTIER`, `MAGASINIER`, `RH`, `FINANCE`, `PM`, `ACHAT`, `VIEWER` |
| `UserStatus` | `PENDING`, `APPROVED`, `REJECTED` — an account must be `APPROVED` to log in |
| `ChantierStatut` | `EN_PREPARATION`, `EN_COURS`, `EN_PAUSE`, `TERMINE`, `ANNULE` |
| `JalonStatut` | `A_FAIRE`, `EN_COURS`, `TERMINE`, `EN_RETARD` |
| `FournisseurStatut` | `ACTIF`, `INACTIF`, `BLACKLISTE` |
| `SousTraitantStatut` | `ACTIF`, `INACTIF`, `BLACKLISTE` |
| `EmployeStatut` | `ACTIF`, `CONGE`, `INACTIF` |
| `EmployeRole` (HR) | `ADMIN`, `RH`, `FINANCE`, `PM`, `ACHAT`, `CONDUCTEUR_TRAVAUX`, `CHEF_EQUIPE`, `OUVRIER` |
| `TypeContrat` | `CDI`, `CDD`, `ANAPEC`, `JOURNALIER` |
| `DossierStatut` | `INCOMPLET`, `COMPLET` |
| `AchatStatut` | `EN_COURS`, `LIVRE`, `FACTURE`, `PAYE` |
| `TypeMouvement` | `ENTREE`, `SORTIE`, `TRANSFERT`, `AJUSTEMENT` |
| `TypeTransaction` | `CREDIT`, `DEBIT` |
| `ModePaiement` | `VIREMENT`, `CHEQUE`, `EFFET`, `CAISSE` — only `CAISSE` debits the treasury |
| `TypeDocumentPaiement` | `ACHAT`, `FICHE_PAIE`, `PAIEMENT_SOUS_TRAITANT`, `DEMANDE_PAIE` |
| `FichePaieStatut` | `BROUILLON`, `VALIDEE`, `PAYEE` |
| `DemandePaieStatut` | `SOUMISE`, `VALIDEE`, `PAYEE` |
| `AttachementStatut` | `SOUMIS`, `ENCAISSE` |
| `ContratStatut` | `EN_COURS`, `TERMINE`, `RESILIE` |
| `PaiementStatut` | `EN_ATTENTE`, `VALIDE`, `PAYE` |

---

## Error Handling

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

## Endpoints not detailed here

These controllers exist and answer, but their tables are not in this README. Swagger lists them, being generated from the code.

| Prefix | Domain |
|--------|--------|
| `/api/v1/chantiers/{chantierId}/bpu-lignes` | bordereau des prix unitaires (unit-price schedule) |
| `/api/v1/chantiers/{chantierId}/attachements` | attachements (progress claims) |
| `/api/v1/demandes-paie` | payroll requests |
| `/api/v1/dashboard/kpis` | dashboard indicators |
| `/api/v1/comptabilite` | accounting entries, derived |
| `/api/v1/export` | Excel exports |
| `/api/v1/users` | account administration (approval) |

---

## Architecture

Modular monolith. One package per domain, the same structure inside each.

```
com.buildflow.erp
├── common/         shared kernel — BaseEntity, ApiResponse, exceptions, Tva
├── config/         SecurityConfig, OpenApiConfig, JpaAuditingConfig
├── security/       JWT filter, JwtService, UserPrincipal
└── domain/<name>/
    ├── controller/   REST endpoints
    ├── dto/request/  validated inbound records
    ├── dto/response/ outbound records
    ├── entity/       JPA entities + enums
    ├── mapper/       MapStruct interfaces
    ├── repository/   Spring Data interfaces
    └── service/      business rules (interface + impl)
```

The path of a request:

```
request → JWT filter (sets the SecurityContext)
        → controller (validates the body, delegates)
        → service (business rules, cross-domain calls)
        → repository (JPA → PostgreSQL)
        → mapper (entity → DTO)
        → ApiResponse wrapper → JSON
```

### Cross-domain side effects

This is what surprises most on first read: validating a step in one domain fires another elsewhere.

```
Achats.validateBL()       → StockService.approvisionnerDepuisAchat()
Achats.regler()           → TresorerieService (debits the amount paid)
Salaires.payer()          → TresorerieService (debit)
SousTraitance.payer()     → TresorerieService (debit)
SousTraitance.create()    → SousTraitant.nombreContratsActifs++
SousTraitance.payer()     → SousTraitant.montantTotalPaye += montant
```

---

## Known limitations

- No general delete endpoint — mostly creates and workflow transitions. `chantiers` is the exception.
- No file upload.
- Stock is theoretical: nothing reconciles it against a physical count.
- Threshold alerts never leave the server logs.
- Single-tenant.

### Database

- 45 Liquibase migration files, applied at startup.
- All IDs are UUID v4.
- `created_at` / `updated_at` are managed automatically on every entity.
- The column `mouvements_stock.ligne_achat_id` exists but is no longer read or written: stock attribution by origin was reverted after it shipped. To drop or to document.

---

## Developing without Docker

Java 25 required. Run Postgres separately, then:

```bash
DB_URL=jdbc:postgresql://localhost:5433/buildflow DB_USERNAME=buildflow_dev DB_PASSWORD=devpassword123 JWT_SECRET=a-key-of-at-least-32-characters ./mvnw spring-boot:run
```

Tests need a database of their own — never the development one: migration `043` deletes operational data.

```bash
docker run -d --name bf-test -e POSTGRES_DB=buildflow -e POSTGRES_USER=buildflow_dev -e POSTGRES_PASSWORD=localdevpw -p 5448:5432 postgres:16-alpine
```

```bash
DB_URL=jdbc:postgresql://localhost:5448/buildflow DB_PASSWORD=localdevpw JWT_SECRET=a-key-of-at-least-32-characters ./mvnw -B clean verify
```

`JWT_SECRET` is needed even for tests: `ErpApplicationTests` does not load the `test` profile and reads `application.yml`, which requires it.

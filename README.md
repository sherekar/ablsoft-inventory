# ABLSoft Product Inventory

A full-stack product inventory application. Import an XLSX workbook, review stock in a sortable and paginated dashboard, and see product counts, inventory value, and average stock age.

**Stack:** Java 21 · Spring Boot 3.5.16 · PostgreSQL 17.10 · Angular 22.1 · Apache POI 5.5.1

## Start with Docker

Prerequisites: Git and Docker Desktop with Linux containers enabled (or Docker Engine with the Compose plugin). Docker must be running. Clone the repository and start the application:

```powershell
git clone https://github.com/sherekar/ablsoft-inventory.git
cd ablsoft-inventory
docker compose up --build -d --wait
```

Open **http://localhost:8088**. If you downloaded the ZIP, extract it, open a terminal in the folder containing `compose.yaml`, and run only the Docker command. The first build downloads the dependencies and container images. Subsequent starts reuse them. No local Java, Maven, Node, or PostgreSQL installation is needed for this path.

1. Download the sample from the dashboard, or select `samples/inventory-valid.xlsx`.
2. Choose **Import data**, select the file, and import it.
3. Review the 12 entries, sort any column, and use the paginator.
4. Import `samples/inventory-formulas.xlsx` to add a formula-based example.
5. Reimport either workbook to see duplicate handling. Try `samples/inventory-invalid.xlsx` for row-level validation errors.

The database starts empty. Samples are never loaded automatically.

```powershell
docker compose ps                  # Service health
docker compose logs -f backend     # Backend logs
docker compose down                # Stop; retain inventory data
```

**Reset only when you want to delete this project's inventory:** `docker compose down -v` removes its database volume.

### Optional configuration

Defaults work without an `.env` file. Copy `.env.example` to `.env` to change the browser port, database password, application timezone, or currency code before starting.

| Variable | Default | Meaning |
|---|---|---|
| `APP_PORT` | `8088` | Browser port; change if already in use |
| `DB_PASSWORD` | `inventory` | Local database password; changing it does not reset an existing PostgreSQL volume |
| `APP_TIMEZONE` | `UTC` | IANA timezone used to determine today's date |
| `APP_CURRENCY` | `USD` | ISO currency code for display; does not convert existing prices |
| `DB_PORT` | `5432` | Local development database port only |

Only the frontend port is exposed in the default Compose configuration. Nginx serves Angular and proxies API requests, OpenAPI documentation, and Swagger UI to the backend. Database and backend connections stay on the Compose network. The application is designed for a single trusted user and does not include authentication.

## Workbook contract

The importer supports **XLSX** workbooks, including Excel formula evaluation. Upload a standard, unencrypted `.xlsx` file, up to **5 MB**, with at most **10,000 data rows** in its first worksheet.

Row 1 must contain these headers. Header order may vary; matching ignores case and surrounding whitespace. Additional columns are ignored. Duplicate required headers are rejected. Other worksheets are not imported, but formulas may reference them within the same workbook.

| Header | Accepted value |
|---|---|
| Product SKU | Required text, up to 100 characters. Text preserves identifiers such as `00124`. |
| Product Name | Required text, up to 200 characters. |
| Category | Required text, up to 100 characters. |
| Purchase Date | `YYYY-MM-DD` text or a formatted Excel date cell. From 1900 through today. |
| Unit Price | Numeric/currency cell, nonnegative, up to 999999999999.99, at most two decimal places. Currency symbols are formatting, not cell text. |
| Quantity | Numeric cell, whole number from 0 through 2147483647. |

Formulas are evaluated with Apache POI and their resulting values follow the same rules. The importer does not trust cached formula results. Excel errors, unsupported functions, and unresolved external workbook references reject the upload with cell-level feedback. There is no guarantee that every Excel function is supported by POI.

### Validation and duplicate handling

- SKU values are trimmed and uppercased using `Locale.ROOT`; names and categories are trimmed.
- A SKU may appear on different purchase dates. The same normalized SKU and date may appear only once.
- Fully blank inventory rows are skipped. Partially filled rows are rejected.
- Validation reports the first issue in each invalid row, with up to 100 displayed errors and the total error count.
- Parsing and formula evaluation happen outside the database transaction. The transaction then checks existing entries in one lookup and persists the validated batch.
- A successful upload adds all rows in one transaction. Any validation failure or write conflict saves **zero** rows.
- A database unique constraint is the final protection against overlapping concurrent uploads.
- Imports add entries; they do not update existing records or silently skip duplicates.

The invalid sample intentionally contains a duplicate, negative price, invalid date, and division-by-zero formula. It should never import successfully.

## Summary definitions

| Metric | Calculation |
|---|---|
| Total products | Count of distinct normalized SKUs |
| Inventory value | Sum of `unit price × quantity` across all entries |
| Stock age | Whole calendar days between purchase date and today in the configured timezone |
| Average stock age | Unweighted average across inventory entries, displayed to one decimal place |

All summary values cover the entire inventory, independently of pagination. Empty totals are zero. Stock age is calculated when requested rather than stored, so it cannot become stale in the database. Dates are returned without a time component and displayed without converting them to the browser's timezone. A shared Java `Clock` keeps calculations consistent and tests deterministic.

For `inventory-valid.xlsx`, expect **12 entries**, **10 distinct products**, and **USD 9,641.92** in inventory value. The formula sample adds one entry with a unit price of **10.00** and quantity **7**. Stock ages depend on the current date.

## API

Interactive documentation is available at **[Swagger UI](http://localhost:8088/swagger-ui/index.html)** after the same Docker start command. The generated **[OpenAPI JSON](http://localhost:8088/v3/api-docs)** describes all three endpoints, their parameters, successful responses, and error bodies. If you changed `APP_PORT`, use that port in these links.

Expand an endpoint and select **Try it out**, then **Execute**. The import endpoint accepts a file from `samples/` through its file picker. Requests run against the current database; reimporting an existing sample will show duplicate errors. The documentation uses the current host and port, so no additional service or exposed backend port is needed.

For local development with Spring Boot running directly, use **http://localhost:8080/swagger-ui/index.html**. OpenAPI and Swagger UI are generated with [springdoc-openapi](https://springdoc.org/v2/).

All paths below are available through `http://localhost:8088`.

| Method and path | Behavior |
|---|---|
| `POST /api/inventory/imports` | Multipart upload using the field `file`; returns `201` and the imported row count |
| `GET /api/inventory?page=0&size=10&sort=purchaseDate&direction=desc` | Zero-based page; page size 1–100 |
| `GET /api/inventory/summary` | Full-inventory metrics, currency, and calculation date |

Sortable fields: `sku`, `productName`, `category`, `purchaseDate`, `unitPrice`, `quantity`, `stockAgeDays`. Directions: `asc`, `desc`. Stock age sorting reverses purchase date sorting. The database ID breaks ties to keep pagination stable.

Errors use Spring's Problem Detail response with `title`, `status`, and `detail`. Validation failures also include `errors` and `totalErrors`.

```json
{
  "title": "Import validation failed",
  "status": 422,
  "detail": "Import rejected. Correct the errors and try again. No rows were saved.",
  "errors": [{ "row": 8, "column": "Quantity", "message": "Use a whole number between 0 and 2147483647." }],
  "totalErrors": 1
}
```

Status codes: `400` for invalid requests/files, `413` for oversized uploads, `422` for workbook validation or detected duplicates, and `409` for database write conflicts, including concurrent duplicate uploads. Unexpected failures return a generic `500` response and are logged on the server.

## Project structure

```text
backend/
  src/main/java/com/ablsoft/inventory/
    config/          Application clock and OpenAPI documentation
    inventory/       Entity, repository, read service, DTOs, REST controller
    spreadsheet/     XLSX parsing, validation, transactional import
    error/           Central exception handling
  src/main/resources/db/migration/   Flyway schema
  src/test/          Parser and PostgreSQL API tests
frontend/
  src/app/inventory/
    dashboard/       Summary, table, pagination, page states
    import-dialog/   File selection and validation feedback
    inventory-api.service.ts
    inventory.models.ts
samples/             Valid, formula, and intentionally invalid XLSX files
compose.yaml         Complete runtime
compose.dev.yaml     Optional local database port
```

The backend uses a single table and direct DTO mapping. Flyway owns the schema; Hibernate only validates it. Monetary calculations use `BigDecimal` and PostgreSQL `NUMERIC`. Angular uses standalone components, signals for view state, RxJS for HTTP cancellation, and Material for accessible controls. The UI displays API-calculated metrics.

## Tests

### Backend

With Java 21 and Docker running:

```powershell
cd backend
.\mvnw.cmd verify
```

On macOS/Linux: `./mvnw verify` (or `sh mvnw verify` if the executable bit was not preserved).

`verify` runs parser unit tests plus API integration tests against an isolated PostgreSQL Testcontainers database. The tests cover formula evaluation and stale caches, date parsing, type/range validation, duplicates, atomic rollback, concurrent imports, summary totals, pagination, sorting, and the generated OpenAPI upload contract. `./mvnw test` runs only the unit tests and does not need Docker.

### Frontend

With compatible Node.js (the Docker build uses 24.15.0):

```powershell
cd frontend
npm ci
npm test
npm run build
```

Or run the frontend tests entirely in Docker from the project root:

```powershell
docker build --target test -f frontend/Dockerfile -t ablsoft-inventory-ui-tests .
```

The component and HTTP tests cover empty state, sorting requests, multipart uploads, invalid file selection, validation feedback, and repeated-submit prevention.

## Local development

Start just PostgreSQL with its localhost port enabled:

```powershell
docker compose -f compose.yaml -f compose.dev.yaml up -d database
```

Then run these in separate terminals:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

```powershell
cd frontend
npm ci
npm start
```

Open http://localhost:4200. The development proxy forwards `/api` to port 8080. The sample workbook is included in `frontend/public/samples` for local development and copied from `samples` during the Docker build. If changing default database credentials, port, currency, or timezone for a local backend, set the corresponding `DB_URL`, `DB_USER`, `DB_PASSWORD`, `APP_CURRENCY`, and `APP_TIMEZONE` environment variables in that terminal; Compose's `.env` is not loaded by Spring Boot.

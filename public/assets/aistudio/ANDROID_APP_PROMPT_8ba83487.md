# Apptividades Lab Android: build prompt for Google AI Studio

Paste everything below the line into Google AI Studio (Build → Android). Build one phase at a time, and only start the next phase when every acceptance check in the current phase passes on a real phone.

---

## 0. Who this app is for and what it must achieve

Build **Apptividades**, a native Android app for **Omar Suniaga**, a professional violinist in Punta Cana, Dominican Republic. He plays gigs ("toques") in hotels, weddings and private events. Agencies ("proveedores", for example Crisbel, FJ and José David) book him and pay **one month in arrears**: a gig played in October is paid during November and is **overdue** from December onward.

The app must help him:
1. Never miss or arrive late to a gig. It tells him when to start getting ready and when he must leave, based on live traffic from where he is.
2. Never lose money. It shows what each agency owes him, flags overdue payments, and lets him send a reminder or the PDF statement by WhatsApp.
3. Prepare the music. It reminds him which songs he still has to find sheet music for and study before each gig.
4. Register gigs in seconds, including from a screenshot or a shared WhatsApp message, read by AI.
5. Grow his income. It compares the month against his goal and shows how dependent he is on each agency.

The UI language is **Spanish (Dominican Republic)**, the currencies are **DOP (RD$)** and **USD (US$)**, and the time zone is **America/Santo_Domingo (UTC-4, no daylight saving)**. Code, identifiers and comments are in English.

### Non-negotiable rule: same backend as the web app
This app is a second client of an existing production-grade backend. It is **not** a new product with its own data.

- Backend: **Supabase project `Apptividades_Lab`**
  - URL: `https://fmtjiwltxyzlfreimcue.supabase.co`
  - Publishable (anon) key: read it from `local.properties` → `BuildConfig.SUPABASE_PUBLISHABLE_KEY`. **Never hard-code it and never use a service_role or secret key in the app.**
- The web app (TanStack Start + React, repo `omarsuniaga/apptividades-lab`) uses the same tables. Anything the phone writes must appear on the web after a refresh, and the reverse.
- **Do not create, alter or drop tables, policies, triggers or functions from the app.** The schema below is the contract. Schema changes for Phase 0 are delivered as SQL migrations and Edge Functions in the `apptividades-lab` repository, separately from this project.
- Row Level Security is on. Every row has `owner_id = auth.uid()`. Always send `owner_id` on insert and always filter by it, even though RLS also enforces it.
- Never connect to Firebase `apptividades-5b66f`, the Supabase "SOI" project or the "Monica Project". They are out of scope.

---

## 1. Technical stack and architecture

- Kotlin, Jetpack Compose, Material 3 with the custom theme in section 3. Min SDK 26, target the latest stable SDK.
- Supabase Kotlin SDK (`io.github.jan-tennert.supabase`): `auth-kt`, `postgrest-kt`, `functions-kt` and `storage-kt`, with the Ktor OkHttp engine and kotlinx.serialization.
- Room for an offline read cache and a write outbox (Phase 5). WorkManager for background work. AlarmManager for exact alarms.
- Google Play Services: Fused Location Provider. Maps Compose and the Places SDK for picking venues. The Google **Routes API** (`computeRoutes`, `TRAFFIC_AWARE`) for travel time. Read the Maps/Places key from `local.properties` and restrict it to the app's package and SHA-1 in Google Cloud.
- Clean / hexagonal layering, mirroring the web app:
  - `domain/`: pure Kotlin models and rules with no Android or Supabase imports, unit-tested with JUnit.
  - `data/`: repository interfaces (ports) and their Supabase and Room implementations (adapters).
  - `ui/`: Compose screens and ViewModels that depend only on domain and repository interfaces.
  - A single `AppContainer` (or Hilt module) is the only place that builds Supabase clients.
- Dates are `yyyy-MM-dd` strings for `event_date` and `HH:mm:ss` for `event_time`. Compute "today" in America/Santo_Domingo, never in UTC.
- Money is `BigDecimal`. Display RD$ with thousands separators and no decimals when the value is whole, e.g. `RD$ 59,000`.

---

## 2. Database contract (Supabase `public` schema)

### 2.1 Existing tables (already in production; use as-is)

**`lab_activities`**: one row per gig.

| column | type | notes |
|---|---|---|
| id | uuid PK | server default |
| owner_id | uuid | = auth.uid() |
| supplier_id | uuid null | set by a DB trigger from `supplier_name`; send null |
| supplier_name | text | free text, may be empty; the trigger creates or reuses the supplier (case and space-insensitive) |
| title | text, required | derived client-side: supplier name → else first line of description → else venue → else "Servicio musical" (max 200) |
| activity_type | text | `'fijo'` or `'eventual'`; new rows: `'eventual'` |
| event_date | date | required |
| event_time | time null | |
| venue_name | text, required | max 200 |
| venue_sub_location, venue_address, city, notes | text null | keep the existing values when editing |
| description | text null | |
| amount | numeric ≥ 0 | |
| currency | text | `'DOP'` or `'USD'` |
| payment_status | text | `'pendiente'` or `'pagado'` |
| paid_at | timestamptz null | set to now when marking paid; null when pending |
| tags | text[] | default `{}` |
| series_id | uuid null | weekly recurring series (same id for every date) |
| latitude, longitude | double null | both or neither |
| melodyflow_id | text null | legacy import id; never write it |
| created_at, updated_at | timestamptz | server-managed; never write them |

- **Schedule conflicts are enforced by the database.** An exclusion constraint rejects overlapping gigs of the same owner, using that owner's setup, event and teardown minutes. The error has Postgres code **`23P01`**. Show: *"Conflicto de horario: ya existe otro servicio en el intervalo reservado."* Before saving, also warn locally (see the domain rules in 2.3).
- To mark several gigs as paid at once, use one update: `payment_status='pagado', paid_at=<now>`, filtered by `owner_id`, `payment_status='pendiente'` and `id in (...)`.
- Paginate reads in pages of 500, ordered by `event_date` and then `id`.

**`lab_suppliers`**: `id uuid, owner_id uuid, name text (≤160), phone text null, email text null, notes text null`. Composite FK `(supplier_id, owner_id)`.

**`lab_settings`**: one row per owner (`owner_id` unique): `default_currency ('DOP'|'USD')`, `setup_minutes int 0–240` (default 45), `event_minutes int 1–1440` (default 120), `teardown_minutes int 0–240` (default 45), `updated_at`. Upsert on `owner_id`. If no row exists, use the defaults.

### 2.2 New backend objects (delivered in Phase 0 through the `apptividades-lab` repo)

Code against these. If a call fails because an object does not exist yet, show a clear "Función pendiente de activar en el servidor" message and do not crash.

**`lab_settings`: new columns**
- `monthly_goal numeric` (default 100000; the goal is in `default_currency`)
- `prep_minutes int` (default 60): time to get dressed and ready before leaving
- `travel_buffer_minutes int` (default 15): safety margin added to the travel time
- `home_latitude, home_longitude double null`: fallback origin when there is no GPS
- `score_reminder_days int` (default 3): how many days before a gig to warn about unstudied songs

**`lab_venues`**: saved places, so the same hotel is never typed three different ways.
`id, owner_id, name text, place_id text null (Google Place ID), address text null, latitude, longitude, notes text null, created_at`. Unique on `(owner_id, place_id)`.
`lab_activities` gets `venue_id uuid null` (FK `(venue_id, owner_id)`). Keep writing `venue_name`, `latitude` and `longitude` on the activity as well, because the web app reads them.

**`lab_activity_songs`**: the repertoire for each gig.
`id, owner_id, activity_id uuid (FK, cascade delete), title text, composer text null, arrangement text null, status text ('por_buscar'|'encontrada'|'estudiando'|'lista'), score_url text null, score_path text null (Storage), notes text null, position int, created_at, updated_at`.
Storage bucket **`scores`** (private), path `{owner_id}/{activity_id}/{file}`, read and write only by the owner.

**`lab_bank_accounts`**: the accounts shown on the PDF statements.
`id, owner_id, bank_name text, account_type text ('ahorros'|'corriente'), account_number text, holder_name text, currency text ('DOP'|'USD'), is_default boolean, created_at`. At most one default per owner and currency.

**`lab_devices`**: `id, owner_id, fcm_token text unique, platform text, updated_at`. Reserved for server pushes later; Phase 2 alarms are local.

**`lab_agent_tokens`**: personal tokens for the MCP server.
`id, owner_id, name text, token_hash text, scopes text[] ('read'|'write'), last_used_at, created_at, revoked_at`. Only the SHA-256 hash is stored.

**Edge Functions** (called with the user's session JWT):
- `extract-activity`: input is an image (base64 or a Storage path) and/or text. It calls Gemini on the server, so **no Gemini key ships in the app**. It returns the JSON described in Phase 4.
- `agent-tokens`: `POST {name, scopes}` returns `{id, token}` (the token is shown once). `DELETE {id}` revokes it.
- `mcp`: the MCP server (Phase 6). The app only manages tokens for it.

### 2.3 Domain rules (implement in `domain/` with unit tests)

1. **Month-in-arrears receivables.** Only gigs with `payment_status='pendiente'` **and** `event_date < today` are receivable. Let M be the gig's month and the current month be C:
   - C > M+1 → **`vencido`** (overdue, red)
   - C = M+1 → **`este mes`** (collect this month)
   - C = M → **`próximo mes`** (collect next month)

   Group by supplier, status and currency. Show the total, the number of gigs, the dates, and the days outstanding since the oldest gig. Sort overdue first, then by amount.
2. **Name normalization.** Remove accents, lowercase, drop a leading "hotel ", and remove non-alphanumerics. "José David" and "Jose David" are the same supplier. "Hotel ClubMed Punta Cana", "Hotel Club Med Punta Cana" and "hotel club med" are the same venue: a key of at least 5 characters that is a prefix of a longer key joins it. Display the most frequent spelling; on ties, prefer the longer and accented one.
3. **Usual rate.** The most frequent amount charged to a supplier in the selected currency, with the most recent one winning ties. Pre-fill it when a supplier is chosen.
4. **Frequent suppliers.** The top 3 by number of gigs in the last 365 days, shown as one-tap chips.
5. **Local conflict warning.** Same date, and `start − setup < other_start + event + teardown` and `other_start − setup < start + event + teardown` (minutes from `lab_settings`).
6. **Month overview.** Billed = the sum of the month's gigs in the default currency. Progress = billed ÷ `monthly_goal`. "Faltan ≈ N toques" = ceil((goal − billed) ÷ average per gig over the previous 12 complete months).
7. **12-month balance.** The previous 12 complete months: total and count per month, average per month, gigs per month and average per gig. Supplier shares are the top 3 plus "N proveedores más". Venue ranking is the top 4, with the number of spellings merged.
8. **Departure time** (Phase 2). `leave_at = event_start − travel_minutes(live traffic) − travel_buffer_minutes − setup_minutes`. `prepare_at = leave_at − prep_minutes`.

Reference numbers to verify the rules on his real data, with today = 2026-11-20:
- November 2026 billed: RD$ 59,000 (8 gigs).
- Overdue: RD$ 22,000 (José David: 3 gigs from 4 Aug, 108 days; Romina: 1 gig on 27 Sep, 54 days).
- Nov 2025 – Oct 2026: 146 gigs, RD$ 1,021,260, RD$ 85,105 per month, 12.2 gigs per month, RD$ 6,995 per gig.
- Crisbel + FJ + José David ≈ 75% of income.

---

## 3. Design system (from the approved Claude Design proposal; reproduce it faithfully)

**Look.** A light warm-paper background with ink-black text and one "stage-light" amber accent. Calm, dense and legible in sunlight. Never use gradients, emojis or neon.

| token | hex | use |
|---|---|---|
| ground | `#F4F3EF` | app background |
| surface | `#FFFFFF` | cards |
| line | `#E3E1DA` | card borders and dividers |
| ink | `#15171C` | text, dark summary card, selected chips |
| ink-2 | `#2E313A` | progress track on the dark card, dividers |
| ink-3 | `#3F434C` | bars below the goal |
| muted | `#5B606B` | secondary text (passes 4.5:1 on white and on ground) |
| on-dark-muted | `#C9CCD3` | secondary text on ink |
| accent | `#B45309` | brand, central + button, active tab, goal line, bars at or above the goal |
| accent-hover | `#92400E` | pressed accent, link text |
| accent-soft | `#FDF1E3` | soft amber backgrounds ("por cobrar" chip) |
| accent-bright | `#F59E0B` | progress fill on the dark card |
| danger | `#B42318` / text `#8A1C12` / bg `#FDECEA` / border `#F5C2BC` | overdue |
| success | `#0F6B4F` / text `#0F5A43` / bg `#E4F3EC` | paid, "mark paid" button, free days |
| info | `#1D4ED8` / bg `#E8EEFD` | scheduled future gigs |
| neutral chip | `#EEEDE8` / text `#3F434C` | |
| share palette | `#15171C`, `#B45309`, `#93A8E8`, `#D6D3CA` | supplier concentration bar; the colors differ in lightness |

- **Typography.** Inter for body text and Space Grotesk for headings and money figures (Google Fonts, bundled). Sizes: screen title 26sp bold with −2% tracking; hero amount 40sp bold with −3% tracking; section title 16sp bold; body 14sp; caption 12–13sp; tab label 11sp.
- **Shape.** Cards 16–22dp radius, chips fully rounded, buttons 12–14dp, the central + button 18dp. Spacing: 20dp screen padding, 16dp between sections, 8–12dp inside groups.
- **Touch targets** at least 44–48dp. Never rely on color alone: always pair it with a label ("Vencido", "Pagado"). Support TalkBack, with content descriptions on every icon button. Respect the system font size.
- **Navigation.** A bottom bar with 4 tabs and a central raised + button: **Inicio · Agenda · [+] · Cobros · Balance**. The + is a 56dp square with an 18dp radius, filled with accent and a soft amber shadow, raised 24dp above the bar. Active tab = accent with bold text; inactive = muted. Reportes, Proveedores and Ajustes are icon buttons in the top app bar.
- Icons: Material Symbols Rounded or Lucide-style outline icons with a 1.8 stroke.

### The five approved screens

1. **Inicio**
   - Date line in muted ("Viernes 20 de noviembre"), then the greeting "Hola, Omar" and a bell button.
   - **Dark summary card** (ink, 22dp radius): "Noviembre 2026 · facturado" with the number of activities on the right. Hero amount "RD$ 59,000". An amber progress bar on an ink-2 track. Under it, "59% de la meta · RD$ 100,000" and "faltan ≈ 6 toques". A 3-column row: Tocadas / Por venir / Cobrado.
   - **Overdue card** (danger colors), only when there is debt: an alert icon, "RD$ 22,000 vencidos", "José David · 3 toques (ago–sep) · Romina · 1 toque (sep)" and the button "Ver cobros y enviar recordatorio".
   - **Próximas**: the next 3–4 gigs as white cards, each with a 52dp date tile (weekday and day number), title, "hora · lugar · proveedor" and the amount.
2. **Agenda**
   - Title "Noviembre 2026" with previous and next month buttons.
   - Monday-first month grid inside a white card. Each day shows dots: amber = played and pending payment, info blue = scheduled, green = paid. Today is tinted accent-soft and the selected day is ink.
   - "Fines de semana libres este mes": green chips for Friday–Sunday dates from today on that have no gig. Tapping one opens New Activity on that date.
   - The selected day's gigs (time, venue, "supplier · amount", status chip) and the gap between consecutive gigs ("4 h 30 min entre toques"). Each gig also shows "Sal a las 3:35 p. m." once Phase 2 exists.
3. **Nueva actividad** (full-screen sheet)
   - A close button and the title "Nueva actividad".
   - **Proveedor** chips: the frequent suppliers plus "+ Otro". Choosing one pre-fills the usual rate.
   - **Tipo** chips: Actividad fija, Boda, Cóctel, Privado. They map to `activity_type` (`fijo` for Actividad fija, `eventual` for the rest) and the label is saved as the first line of `description` unless the user writes one.
   - Fecha and Hora.
   - An inline **conflict warning** in danger colors ("Choque de horario: ya tienes Crisbel · Club Med a las 12:15 ese día").
   - **Lugar** with suggestions from `lab_venues` and a "Elegir en el mapa" action (Phase 2).
   - **Monto** with an RD$/US$ segmented control and the hint "Tarifa habitual de Crisbel: RD$ 6,000".
   - A "Ya me pagaron" switch.
   - Canciones (Phase 3).
   - A sticky "Guardar actividad" button.
4. **Cobros**
   - "Por cobrar · toques ya tocados" with the big total.
   - Two tiles: Vencido (danger) and En plazo (mes vencido).
   - Sections: Vencidos / Se cobran en {mes actual} / Se cobran en {mes siguiente}. Each supplier card has a checkbox, the name, a status chip, "N toques · fechas · X días" and the total, plus "Enviar recordatorio por WhatsApp" for every status except next month.
   - When something is selected, a sticky green bar: "Marcar N toques como pagados · RD$ X".
   - An action "Compartir estado de cuenta (PDF)" for each supplier (Phase 3).
5. **Balance**
   - "Balance 12 meses" with the period, and an "Exportar PDF / CSV" button.
   - 3 KPI tiles: Promedio/mes, Toques/mes, Por toque.
   - A 12-bar monthly chart: amber when the month is at or above the goal, ink-3 when below, with a dashed amber goal line. Month initials under the bars. Tapping a bar shows the amount and the number of gigs. The goal is editable inline. A sentence such as "4 de 12 meses alcanzaron la meta. El mes más bajo fue julio 2026."
   - "¿De quién depende tu ingreso?": a segmented bar, a legend with gigs and percentage, and an amber insight box ("3 proveedores generan el 75%. Si Crisbel se detiene, pierdes ≈ RD$ 28,700 al mes.").
   - "Dónde tocas más": the top 4 venues with ink progress bars and the note "Se unificaron N nombres escritos de distinta forma."

Empty, loading and error states in every screen, in Spanish and in the same style.

---

## PHASE 1: Foundation, sign-in and the five screens (read and write)

**Build**
- The project skeleton, theme (section 3), navigation and `AppContainer`.
- Supabase Auth: email and password sign-in with a persisted session, auto-refresh and sign-out. **No sign-up screen** (accounts are private). "¿Olvidaste tu contraseña?" calls `resetPasswordForEmail` with redirect `https://apptividades-lab.netlify.app/reset-password`.
- Repositories for `lab_activities`, `lab_suppliers` and `lab_settings`, following the exact contract in section 2.1.
- The domain rules 1–7 from section 2.3, with JUnit tests that reproduce the reference numbers.
- Screens Inicio, Agenda, Nueva/Editar actividad, Cobros and Balance with full CRUD:
  - Edit and delete gigs; deleting asks for confirmation.
  - Weekly series: "Repetir cada semana N veces" creates N rows sharing one new `series_id` (a UUID generated on the client), with dates every 7 days. Editing offers "Solo esta fecha / Toda la serie".
  - Toggle payment and bulk mark-paid.
- Proveedores: list, edit (name, phone, email, notes) and delete. Ajustes: currency, the three durations and the monthly goal (the goal stays local until Phase 0 lands, then moves to `lab_settings`).
- The WhatsApp reminder from Cobros: `https://wa.me/<digits>?text=<message>`. A 10-digit Dominican number gets the prefix `1`. With no phone, open the WhatsApp chooser with the text. Message: *"Hola {proveedor}, ¿cómo estás? Te escribo por los {n} toques del {dd/MM, …}, pendientes de pago: RD$ {total}. ¡Gracias!"* (singular when n = 1).

**Acceptance**
- Signing in with Omar's real account shows the same gigs as the web app, and a gig created on the phone appears on the web after a refresh.
- Creating an overlapping gig shows the 23P01 message and does not crash.
- With today = 2026-11-20 on his data, the reference numbers in 2.3 match.
- All screens work at 360dp width and with font scale 1.3, with no clipped text.

## PHASE 2: Alarms, venues on the map and "when to leave"

**Build**
- Permissions are requested in context, each with a short Spanish explanation: `POST_NOTIFICATIONS`, `ACCESS_FINE_LOCATION`, and `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` for the alarms. If exact alarms are denied, fall back to inexact WorkManager alarms and say so in Ajustes.
- Notification channels: "Salidas y preparación" (high importance, alarm-style), "Cobros", "Partituras" and "Resumen".
- **Venue picker:** Maps Compose plus Places autocomplete, with the search biased to Punta Cana / Bávaro / Cap Cana. Picking a place upserts it into `lab_venues` (by `place_id`) and fills `venue_name`, `venue_id`, `latitude` and `longitude` on the activity. Saved venues appear first.
- **Departure engine** for every upcoming gig with coordinates:
  - At T−24h, schedule a provisional alarm using the last known or home location.
  - From T−3h, every 15 minutes (WorkManager), recompute with the current location and the Routes API (`TRAFFIC_AWARE`, `departureTime=now`) and move the alarms if the time changed by 5 minutes or more.
  - Two alarms: **"Empieza a prepararte"** at `prepare_at` and **"Sal ahora hacia {venue}"** at `leave_at`, which shows travel time, arrival time and a "Navegar" action that opens Google Maps directions.
  - If `leave_at` has already passed when it is computed, notify immediately: "Vas tarde: sal ya, llegarás a las HH:mm".
- Reschedule all alarms after a reboot (`BOOT_COMPLETED`), after a time-zone change and after any edit to a gig. Cancel them when a gig is deleted.
- The Agenda and Inicio cards show "Sal a las h:mm" and "Prepárate a las h:mm".
- A daily 8:00 a.m. summary notification: today's gigs with their departure times, plus overdue totals on Mondays.

**Acceptance**
- With airplane mode on, the provisional alarm still fires.
- Changing a gig's time moves its alarms.
- With a gig at Club Med at 12:15 and Omar in Bávaro, the "Sal ahora" alarm fires at the expected time (travel + 15 + 45 minutes before).

## PHASE 3: Repertoire and sheet-music reminders, bank accounts, PDF and WhatsApp sharing

**Build**
- A **Canciones** section in New/Edit Activity and a song list on each gig:
  - Fields: title, composer, arrangement and status (`por_buscar` → `encontrada` → `estudiando` → `lista`).
  - Optional score: attach a PDF or photo to the Storage bucket `scores`, or a URL. Viewing it in-app with a pinch-zoom PDF viewer.
- **Score reminders:**
  - When a gig is saved with songs that are not `lista`: "Tienes N canciones por preparar para {fecha}".
  - `score_reminder_days` before the gig, at 7 p.m.: "Faltan X días para {evento}: N partituras sin estudiar", with an action that opens the list.
  - A badge on the Agenda for gigs with unprepared songs.
- **Bank accounts:** CRUD in Ajustes → "Cuentas bancarias" (bank, type ahorros/corriente, number, holder, currency, default per currency). The number is masked in lists (•••• 1234) and shown in full on the PDF.
- **PDF statement** (generated on-device with `android.graphics.pdf.PdfDocument`, letter size), in two variants:
  - "Estado de cuenta del mes" (all gigs of a month).
  - "Pendientes por proveedor" (the receivable gigs of one supplier).
  - Layout: header "Apptividades · Omar Suniaga", the period and the supplier; a table with Fecha, Hora, Lugar, Descripción, Monto and Estado; totals per currency (pagado, pendiente, total); then a **"Datos para el pago"** block with the default bank account for each currency in the report (bank, type, number, holder); and a footer with the generation date and page X of Y. Content that does not fit continues on new pages with the header repeated.
- **Share to WhatsApp:**
  - Save the PDF to the cache, expose it with a `FileProvider`, and send `ACTION_SEND` (`application/pdf`) with the reminder text as `EXTRA_TEXT`.
  - Prefer the package `com.whatsapp`, then `com.whatsapp.w4b`, then the system chooser.
  - Available from each supplier card in Cobros and from Reportes.
- **Reportes screen:** month picker, a preview of the totals, buttons "Compartir PDF" and "Exportar CSV". The CSV uses the same columns as the web history export and is UTF-8 with a BOM.

**Acceptance**
- A pending statement for José David produces a multi-page PDF with his 3 gigs, RD$ 15,000 and the default DOP account, and it opens in WhatsApp with the PDF attached.
- Marking a song `lista` clears its reminder.

## PHASE 4: Create gigs from an image or shared text (AI)

**Build**
- Register the app as an Android **share target** for `image/*` and `text/plain`, so a WhatsApp screenshot or message can be shared straight into it. Inside New Activity, add "Desde una imagen" (camera or gallery) and "Pegar mensaje".
- Downscale images to 1600px on the long side and JPEG at 80%. Call the Edge Function `extract-activity` with `{ image_base64?, text?, today: "yyyy-MM-dd", timezone: "America/Santo_Domingo", known_suppliers: [...], known_venues: [...] }`.
- Expected response (one or several gigs, because one message can list a whole week):

```json
{ "activities": [ {
  "date": "2026-12-12", "time": "18:30", "supplier_name": "Crisbel",
  "venue_name": "Hotel Club Med Punta Cana", "description": "Cóctel de bienvenida",
  "amount": 6000, "currency": "DOP", "activity_type": "eventual",
  "songs": [ { "title": "Perfect", "composer": "Ed Sheeran" } ],
  "confidence": { "date": 0.95, "time": 0.9, "amount": 0.6 }
} ], "warnings": ["La hora puede ser a.m. o p.m."] }
```

- Show each result as a **pre-filled draft form**. Highlight fields with confidence below 0.75 in amber with the hint "Revisa este dato". Match suppliers and venues with the normalization rule; propose the usual rate if the amount is missing; run the conflict check.
- **Never save without explicit confirmation.** With several results, use a list with a checkbox per gig and "Guardar seleccionados".
- Images are sent only to the Edge Function and are not stored unless the user attaches them as a score.

**Acceptance**
- A screenshot of "Sábado 12/12, 6:30 pm, Club Med, cóctel, 6,000" produces a correct draft.
- A blurry image produces amber fields and nothing is saved automatically.

## PHASE 5: Offline and reliability

**Build**
- Room cache of activities, suppliers, venues, songs and settings, so the last data is shown instantly and without a connection, with a "Sin conexión · datos de hh:mm" banner.
- An **outbox** for creates, edits, payments and deletes made offline, sent in order when the connection returns. A conflict (23P01 or a row edited elsewhere) is shown to the user to resolve, never silently overwritten. The client-generated UUID is used as the id for new rows.
- Pull-to-refresh, and a refresh on resume when the data is older than 5 minutes. Optionally, Supabase Realtime on `lab_activities` filtered by `owner_id`.
- Crash-safe alarm scheduling (idempotent per gig id). Logging without personal data.

**Acceptance**
- Register a gig in airplane mode, reconnect, and it appears on the web; the alarms were scheduled while offline.

## PHASE 6: Agent access (MCP), managed from the app

The MCP server itself runs as the Supabase Edge Function `mcp`, built in the `apptividades-lab` repository. In this app, build only the management UI:
- Ajustes → **"Acceso para agentes (MCP)"**:
  - The list of tokens (name, scopes, created, last used) with "Revocar".
  - "Crear token": a name plus scopes, read-only by default, with "permitir cambios" as an explicit opt-in. It calls `agent-tokens` and shows the token **once**, with a copy button and the warning that it will not be shown again.
  - Show the server URL `https://fmtjiwltxyzlfreimcue.supabase.co/functions/v1/mcp` and a copyable configuration snippet for MCP clients (Streamable HTTP with header `Authorization: Bearer <token>`).
- For reference, the tools the server will expose:
  - Read: `list_activities(from,to,supplier?,status?)`, `get_month_summary(month)`, `get_receivables(today?)`, `get_balance(last_month,months=12)`, `list_suppliers()`, `list_venues()`, `search_activities(query)`, `export_history_csv(from,to)`.
  - Write (with the write scope): `create_activity_draft(...)` and `mark_paid(ids[])`.
  - Every call is audited.

**Acceptance**
- A revoked token stops working immediately.
- A read-only token cannot mark anything as paid.

---

## Global quality bar (every phase)
- No secret keys in the APK. A release build with R8 and ProGuard rules that keep Supabase and kotlinx serialization models.
- Unit tests for every rule in 2.3 and for the departure-time math. A Compose UI test of the New Activity flow.
- All strings in `strings.xml` (es-DO), with numbers and dates formatted for es-DO. Correct plurals ("1 toque" / "2 toques").
- Accessibility: TalkBack labels, 48dp targets and 4.5:1 contrast, as in the palette.
- Do not invent data or placeholder gigs. Empty states invite the user to create the first gig.
- At the end of each phase, list what was built, what is pending, and any assumption you made.

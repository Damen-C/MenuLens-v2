You are building an Android app + Python backend.

Project name: MenuLens
Goal: Help tourists in Japan scan Japanese menus and understand dishes.
Platforms: Android first
Language: English first
Offline: Not needed for MVP
Monetization: Subscription (but billing can be stubbed for now)

========================================================
FROZEN PRODUCT REQUIREMENTS (do not change)
========================================================

A) Core flow
1) User takes a photo of a Japanese menu (or selects from gallery).
2) App uploads image to backend.
3) Backend returns a list of menu items + preview details (English title, 1–2 sentence description, images).
4) Results list is FREE to view (JP name + price always visible).
5) Item details are GATED:
   - Pro subscriber: unlimited reveals
   - Non-Pro: 3 reveals per day (Asia/Tokyo day)
   - If no credits remain: show paywall
6) “Show to staff” screen: fullscreen card with “これをください” + the Japanese item name.

B) Gating policy (frozen)
- Gate item details, not list.
- Credits: 3 reveals/day
- Daily reset based on Asia/Tokyo date.
- Gating is implemented on-device for MVP.
- Billing is NOT required for the initial scaffold; implement a debug toggle to simulate Pro.

C) Backend (frozen)
- Single endpoint ONLY for scan: POST /v1/scan_menu
- Stateless MVP (no DB required). Do not store user images; process and discard.
- Backend generates:
  - OCR -> menu item candidates
  - Translation JP->EN for title (or reasonable English name)
  - Dish explanation in natural English (1–2 sentences) using Gemini
  - Dish images via placeholder/stub for now (can return fake URLs)
- MUST have safe explanation rules (see below).

========================================================
API CONTRACT (MUST MATCH EXACTLY)
========================================================

Endpoint: POST /v1/scan_menu
Request: multipart/form-data fields
- image: file (jpg/png)
- target_lang: "en"
- device_id: string UUID (from client)
- app_version: string
- timezone: "Asia/Tokyo"

Response JSON shape:
{
  "scan_id": "uuid",
  "detected_type": { "type": "ramen", "confidence": 0.72 },
  "items": [
    {
      "item_id": "uuid",
      "jp_text": "醤油ラーメン",
      "price_text": "900円",
      "confidence": 0.91,
      "preview": {
        "en_title": "Shoyu ramen",
        "en_description": "Soy sauce–based ramen broth, often served with sliced pork and green onion.",
        "tags": ["pork_possible"],
        "images": [
          { "url": "https://...", "score": 0.83 }
        ]
      }
    }
  ]
}

Important:
- preview.en_description is REQUIRED and must be 1–2 sentences.
- The client will lock/blur preview fields until the user reveals/unlocks them.

========================================================
DISH EXPLANATION REQUIREMENT (NEW, FROZEN)
========================================================

For every item, backend MUST generate preview.en_description using Gemini.

Rules for en_description:
- 1–2 sentences only.
- Natural English, tourist-friendly.
- Describe what it typically is and common ingredients/prep.
- Use cautious language if uncertain: “Typically…”, “Often…”, “Usually…”.
- DO NOT claim guaranteed allergens, safety, or medical advice.
- DO NOT include price or marketing language.
- If Gemini fails, fallback to:
  "Japanese menu item; details may vary by restaurant."

Gemini integration:
- Use Google GenAI Python SDK (google-genai).
- API key provided via environment variable GEMINI_API_KEY.
- Model name configurable via env GEMINI_MODEL (default to a reasonable model; do not hardcode a deprecated one).
- Keep Gemini calls short and deterministic-ish (temperature low or default).

========================================================
STRICT PRD DEFINITION: ITEM DETAIL GATING (REVEAL)
(MUST FOLLOW EXACTLY; DO NOT INTERPRET DIFFERENTLY)
========================================================

Objective:
- Keep List View usable for free.
- Gate high-value understanding content (English + images + tags) behind reveal/subscription.
- “Show to staff” must always be available and must never expose gated content.

1) Definitions
1.1 List View (Results Screen): scrollable list of detected items from a scan.
1.2 Item Detail View (Detail Screen): view for a single item.
1.3 Reveal/Unlock: consumes one free credit (if non-Pro) and unlocks the item’s gated content.
1.4 Show to Staff: fullscreen ordering card derived ONLY from Japanese fields; never gated.

2) Visibility rules (strict)
2.1 ALWAYS VISIBLE (never gated; show on List + Detail):
- jp_text
- price_text (if present)

2.2 MUST BE GATED (locked until reveal/subscription):
- preview.en_title
- preview.en_description
- preview.images[] (thumbnails and full)
- preview.tags[] (if displayed)

3) UI behavior rules (strict)
3.1 List View row:
Always show: jp_text + price_text.
Locked item:
- show lock icon + “Tap to reveal details”
- DO NOT show readable en_title or en_description
- DO NOT show real dish images (placeholders must not reveal identity)
Unlocked item:
- show en_title in full
- optionally show 1–2 thumbnails
- re-open unlocked item without consuming credits again

3.2 Detail View:
Always show at top: jp_text (+ price_text) and “Show to staff” button (always enabled).
Locked item:
- show locked content card with blurred/skeleton placeholders for en_title/en_description/images
- CTA: “Reveal (X left today)”
- if X=0, CTA becomes “Upgrade to unlock” and navigates to Paywall
Unlocked item:
- show en_title, en_description (1–2 sentences), images list/grid/carousel
- if tags shown, include disclaimer: “Best-effort; confirm with staff for allergies.”

3.3 Show to Staff (never gated; no credit consumption):
- accessible from List and Detail regardless of unlock
- display ONLY:
  - large text “これをください”
  - large jp_text
  - optional price_text
- MUST NOT display English title/description/images/tags (no bypass)

4) Reveal logic (strict)
When user taps an item in List OR presses Reveal in Detail:
1) If already unlocked -> open Detail (no credit consumed)
2) Else if Pro -> unlock -> open Detail
3) Else if credits_remaining_today > 0 -> decrement -> unlock -> open Detail
4) Else -> navigate to Paywall

5) Daily reset (strict)
- Credits reset to 3 at Tokyo local day change.
- Store last_reset_date_tokyo + credits_remaining_today in DataStore.
- Reset when stored_date != today_in_tokyo.

6) Unlock scope (strict)
- At minimum, unlock is per scan session (ViewModel unlockedItemIds set).
- Optional (recommended): persist unlocks for scan history so revisiting same scan does not consume credits again.

7) Non-bypass constraints (strict)
- Locked content must not be accessible via any path without unlock.
- Do not expose locked English text via accessibility tree.
- Do not log locked content to analytics/logcat.

========================================================
DELIVERABLES TO GENERATE (Android + Backend)
========================================================

1) ANDROID APP (Kotlin + Jetpack Compose + MVVM + StateFlow)
- Single activity Compose app.
- MVVM with ViewModels using StateFlow (no LiveData).
- Retrofit + OkHttp for networking.
- Coil for image loading.
- DataStore for credits.
- Room for scan history (minimal).

Screens (Compose):
- ScanScreen:
  - Use CameraX capture if possible. If too large for initial scaffold, use gallery picker as placeholder.
- ProcessingScreen:
  - Shows loading state.
- ResultsScreen:
  - Must follow the STRICT gating rules above.
- DetailScreen:
  - Must follow the STRICT gating rules above.
- ShowToStaffScreen:
  - Must follow the STRICT rules above.
- PaywallScreen:
  - Stub UI explaining subscription.
  - Include a DEBUG switch/button to set Pro=true (simulate subscription).
  - Include Restore stub.

Core logic:
- CreditsDataStore:
  - Stores remaining credits + date string for Asia/Tokyo date.
  - Reset to 3 when stored date != today(Tokyo).
- EntitlementRepository:
  - Exposes isPro boolean (debug toggle only for now).
- ResultsViewModel:
  - Holds ScanResult, creditsRemaining, isPro, unlockedItemIds set.
  - Emits one-off navigation events (SharedFlow/Channel).
- ScanRepository:
  - Calls backend POST /v1/scan_menu and maps DTO -> domain.

Testing:
- Unit tests for:
  - credits reset on day change
  - reveal decrement when not Pro
  - paywall trigger when credits=0 and not Pro
  - already unlocked does not consume another credit

README:
- Setup instructions (base URL config)
- How to run app and test gating
- How to point to local backend

2) FASTAPI BACKEND (Python)
- FastAPI server with endpoint POST /v1/scan_menu
- Pydantic models matching response contract
- Accept multipart image
- Pipeline modules:
  - ocr_japanese(image) -> list of lines/blocks (STUB ok)
  - parse_menu_items(blocks) -> list of items with jp_text and optional price_text
  - translate_title(jp_text) -> en_title (simple stub ok)
  - explain_dish_with_gemini(jp_text, en_title) -> en_description (REAL Gemini call)
  - detect_restaurant_type(all_text) -> detected_type (keyword heuristic ok)
  - retrieve_images(jp_text, en_title) -> list of {url, score} (stub ok)
- Do not store or log raw images.
- Config via env vars:
  - GEMINI_API_KEY (required)
  - GEMINI_MODEL (optional default)
  - Optional: MAX_ITEMS (cap items returned)
- Include requirements.txt and run instructions.

Gemini call:
- Use google-genai SDK (from google import genai).
- Build prompt:
  "Write a 1–2 sentence tourist-friendly description of the dish. Use cautious language. Do not assert allergens unless explicitly stated. No pricing. Output only the description."
- Enforce 1–2 sentences by post-processing (trim, remove extra lines, optionally truncate to ~240 chars).

Output format for this task:
- Provide a file tree for Android project.
- Provide the key code files in full (navigation, screens, viewmodels, datastore, api/dto/mappers, room entities/dao, repositories, tests, README).
- Provide backend file tree and full content for main FastAPI files and Gemini helper.

Start now. Do not change the contract or gating rules.

# MenuLens Request Flow

```mermaid
flowchart TD
    A["Android · capture or upload menu"] --> B["POST /v1/scan_menu"]
    B --> C["Vision OCR"]
    C --> D["Optional Gemini OCR normalization"]
    D --> E["Versioned Gemini menu parsing"]
    E --> F["Items + empty preview.images + signed image tokens"]
    F --> G["Results · Japanese name and price only while locked"]
    G -->|Reveal succeeds| H["Open Detail immediately"]
    H --> I{"App image cache?"}
    I -->|Hit| J["Render cached WebP"]
    I -->|Miss| K["POST /v1/generate_dish_image · token only"]
    K --> L{"Backend normalized cache?"}
    L -->|Hit| M["Return cached WebP"]
    L -->|Miss| N["Gemini 3.1 Flash Image · fixed 4:3 1K prompt"]
    N --> O["Compress to WebP"]
    O --> P["Local cache in development or GCS in production"]
    P --> M
    M --> Q["Save to Android app cache"]
    Q --> J
```

Image work is absent from the initial scan latency. Locked dishes never request
an image, and the generation endpoint accepts no arbitrary prompt. The
Show-to-Staff route uses only the Japanese dish name, 「これをください」, and
the optional price.

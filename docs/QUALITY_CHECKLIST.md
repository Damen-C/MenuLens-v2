# MenuLens Quality Checklist

## 1. Baseline Commands
Run these before merging:

```powershell
cd android
.\gradlew.bat clean lintDebug testDebugUnitTest assembleDebug
```

Run instrumentation test (connected device/emulator required):

```powershell
cd android
.\gradlew.bat connectedDebugAndroidTest
```

## 2. Release Readiness Gates
- No failing lint errors in `:app:lintDebug`.
- Unit tests pass in `:app:testDebugUnitTest`.
- At least one critical flow test passes in `:app:connectedDebugAndroidTest`.
- Debug build and release build both assemble successfully.
- App launches and completes the primary user flow on a real device.
- Network failure and retry UX verified manually.
- Rotation/background-foreground behavior checked manually.

## 3. KPI Tracking
Track these in each iteration:
- Crash-free sessions.
- Core flow success rate (scan -> results).
- Median time from scan start to results view.

## 4. Iteration Workflow
1. Add or update a test for the feature/fix.
2. Run lint + tests locally.
3. Validate on device/emulator.
4. Merge only when gates are green.

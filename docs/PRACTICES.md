# Countersteer — Development Practices

## Documentation Requirements

### Large-Scale Changes
Any code change that introduces a new feature, modifies architecture, or significantly alters behavior must include documentation updates:
- Update the relevant section in the design spec (`docs/specs/`)
- Add inline code comments where the logic isn't self-evident

## Code Practices

### General
- Kotlin with Jetpack Compose — follow official Android style guide
- Single-module architecture with clean package separation
- All units in metric (km/h, km) — no imperial units anywhere
- Dark theme only

### Architecture Patterns
- MVVM with ViewModels and UI state (StateFlow)
- Repository pattern for data access
- Hilt for dependency injection
- Coroutines + Flow for async operations
- Room for local database

### Testing
- Unit tests for business logic (route scoring, curvature calculation)
- Integration tests for database operations
- UI tests for critical flows (onboarding)

### API Clients
- Ktor Client for HTTP
- Kotlinx Serialization for JSON
- Respect rate limits (Nominatim: 1 req/sec, debounce search input)
- Handle offline gracefully — cache what you can, fail silently where appropriate

### Safety
- Crash detection is handled by the Pixel's built-in Personal Safety app
- No custom crash detection code in this app

## Project Structure

```
countersteer/
├── app/                    # Android application (once scaffolded)
├── docs/
│   ├── specs/              # Design specifications
│   ├── PRACTICES.md        # This file
│   └── TOMTOM-API-SETUP.md  # End-user guide for TomTom API key
└── .gitignore
```

## Sharing & Distribution
- App is self-contained — no hosted servers
- BYOK (Bring Your Own Key) model for TomTom API
- End-user documentation for API key generation included in-app and in `docs/TOMTOM-API-SETUP.md`
- Backup/restore via local ZIP files — portable across devices

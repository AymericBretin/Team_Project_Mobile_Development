# Authentication

Auth uses an **access token + refresh token** pair (JWT or opaque tokens, the app doesn't care: it never reads their content).

- The access token is sent on every request: `Authorization: Bearer <accessToken>`.
- When a request gets a `401`, the app calls `POST /auth/refresh` once, then replays the request.
- If the refresh fails (`4xx`), the local session is cleared and the user is sent back to the login screen.

---

## API contract (for the server)

Base URL: configurable (`apiBaseUrl`, see below). All bodies are JSON, fields in `camelCase`.
The app ignores unknown fields, so you can add fields without breaking it.

### Shared auth response: `AuthResponse`

Returned by `login`, `register` and `refresh`:

```json
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "dGhpcyBpcyBh...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": { "id": "42", "email": "jane@example.com", "displayName": "Jane" }
}
```

| Field | Required | Notes |
|---|---|---|
| `accessToken` | yes | Short lifetime recommended (e.g. 15 min). |
| `refreshToken` | yes | **Rotation expected**: `refresh` returns a new refresh token and the old one must be invalidated. |
| `tokenType` | no | Defaults to `"Bearer"`. |
| `expiresIn` | no | Seconds. Informational only: the app relies on `401`s. |
| `user.id` | yes | String (even if it's an integer in the database). |
| `user.email` | yes | |
| `user.displayName` | no | `null` or missing is fine. |

### Endpoints

| Method | Route | Body | Success | Expected errors |
|---|---|---|---|---|
| `POST` | `/auth/register` | `{ "email", "password", "displayName"? }` | `201` + `AuthResponse` | `409` email already in use, `400`/`422` validation |
| `POST` | `/auth/login` | `{ "email", "password" }` | `200` + `AuthResponse` | `401` invalid credentials, `429` too many attempts |
| `POST` | `/auth/refresh` | `{ "refreshToken" }` | `200` + `AuthResponse` | `401` refresh token invalid / expired / revoked |
| `POST` | `/auth/logout` | `{ "refreshToken" }` + header `Authorization: Bearer …` | `204` | none handled (the app signs out no matter what) |

Every other API route (budgets, transactions…) must answer **`401`** when the access token is missing, invalid or expired: that's what triggers the refresh on the app side. (Use `403` for "authenticated but not allowed": the app won't refresh on it.)

### Error format

```json
{ "code": "EMAIL_ALREADY_IN_USE", "message": "An account already exists with this email." }
```

The app relies on the **HTTP status code**. `message` is displayed as-is for `400`/`422` errors, so it must be user-readable.

### Client-side validation rules (to mirror on the server)

- email: `x@y.z` format
- password: **at least 8 characters**

---

## Frontend usage (Compose)

Everything goes through `AuthViewModel` (`ui/auth/AuthViewModel.kt`):

```kotlin
val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
val authState by authViewModel.authState.collectAsStateWithLifecycle()
val uiState by authViewModel.uiState.collectAsStateWithLifecycle()

when (val state = authState) {
    AuthState.Loading -> SplashScreen()
    AuthState.Unauthenticated -> LoginScreen(
        isLoading = uiState.isLoading,
        error = uiState.error,
        onLogin = { email, password -> authViewModel.login(email, password) },
        onFieldChange = authViewModel::clearError,
    )
    is AuthState.Authenticated -> HomeScreen(user = state.user, onLogout = authViewModel::logout)
}
```

- `login(email, password)` / `register(email, password, displayName?)`: there is no success callback, **`authState` switches to `Authenticated`**. Just navigate based on `authState`.
- `uiState.error` is an `AuthError` to map to a message:

| `AuthError` | Suggested message |
|---|---|
| `InvalidEmail` | Invalid email address |
| `PasswordTooShort` | At least 8 characters |
| `InvalidCredentials` | Wrong email or password |
| `EmailAlreadyInUse` | An account already exists with this email |
| `Validation(message)` | `message` (sent by the server) |
| `TooManyRequests` | Too many attempts, try again later |
| `Network` | No internet connection |
| `Server` / `Unknown` | Something went wrong, please try again |

- If the session expires while the app is in use, `authState` goes back to `Unauthenticated` on its own.
- Add `androidx.lifecycle:lifecycle-runtime-compose` for `collectAsStateWithLifecycle` (or use `collectAsState()`).

---

## Data layer usage (new APIs)

For any API that requires a signed-in user, create the Retrofit service from `authenticatedRetrofit`: the token is attached and refreshed automatically.

```kotlin
interface BudgetApi {
    @GET("budgets") suspend fun list(): List<BudgetDto>
}

val budgetApi = (application as MyBudgetApplication).container.authenticatedRetrofit.create<BudgetApi>()
```

---

## Configuration

The server URL comes from the `apiBaseUrl` Gradle property (with a trailing `/`). Default: `http://10.0.2.2:8080/`, i.e. the host machine's `localhost:8080` as seen from the emulator.

```bash
./gradlew installDebug -PapiBaseUrl=https://api.mybudget.example/
```

or permanently in `~/.gradle/gradle.properties`: `apiBaseUrl=https://...`

Cleartext HTTP is only allowed in **debug** builds, to `10.0.2.2` and `localhost`. Release builds are HTTPS only.

## Security

- The session (tokens + user) is stored in a DataStore file encrypted with AES-256-GCM, using an **Android Keystore** key (`data/auth/local/`).
- This file is excluded from Android backups (the key doesn't follow a restore onto another device).
- HTTP logs (debug only) never include bodies or the `Authorization` header.

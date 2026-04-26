# Movie Tracker

A simple Android app for tracking, rating, and organizing movies and TV shows you're watching. Built with Jetpack Compose, Firebase, and the TMDB API.

## Prerequisites

- [Android Studio](https://developer.android.com/studio) (Ladybug or newer recommended)
- JDK 21 (Android Studio's bundled JDK works; the Gradle toolchain will download one automatically if needed)
- An Android device or emulator running Android 10 (API 29) or higher

## Setup

### 1. Clone the repository

```bash
git clone <repo-url>
cd movietracker-android
```

### 2. Create a Firebase project

1. Go to the [Firebase Console](https://console.firebase.google.com) and create a new project.
2. Add an Android app to the project using the package name `com.ycs.movietracker`.
3. Enable **Authentication** and turn on the **Email/Password** sign-in provider.
4. Enable **Firestore Database** (start in production or test mode — your choice).
5. Download the generated `google-services.json` file and place it in the `app/` directory.

### 3. Add your TMDB API key via Firebase Remote Config

The app fetches movie metadata from [The Movie Database (TMDB)](https://www.themoviedb.org). The API key is delivered at runtime via Firebase Remote Config so it never has to be baked into the build.

1. Register for a free TMDB account and [request an API key](https://www.themoviedb.org/settings/api) (choose **API Read Access Token**, the long `Bearer` token).
2. In the Firebase Console, go to **Remote Config** and add a new parameter:
   - **Key**: `tmdb_api_key`
   - **Value**: your TMDB API Read Access Token
3. Click **Publish changes**.

> Without this key the app will still build and run, but movie search will be disabled.

#### Other Remote Config parameters

These are optional. If not set, the app uses the defaults shown below.

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `tmdb_search_enabled` | Boolean | `true` | — | Feature flag to show/hide the TMDB movie search UI entirely. |
| `page_size` | Number | `50` | 1–200 | Number of movies fetched per page in the home list. Higher values reduce round-trips but increase memory usage and initial load time. |
| `max_retry_attempts` | Number | `3` | 1–10 | How many times a failed Firestore operation is retried before surfacing an error to the user. |

### 4. Open in Android Studio and run

1. Open the project root directory in Android Studio.
2. Let Gradle sync finish (it will download dependencies automatically).
3. Select a device or emulator and press **Run**.

## Building from the command line

```bash
# Debug build
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run instrumented tests (requires a connected device or emulator)
./gradlew connectedAndroidTest
```

## Release builds

Release signing is configured via `local.properties`. Add the following lines (do **not** commit this file):

```properties
KEYSTORE_FILE=path/to/your.jks
KEYSTORE_PASSWORD=your_keystore_password
KEY_ALIAS=your_key_alias
KEY_PASSWORD=your_key_password
```

Then build with:

```bash
./gradlew assembleRelease
```

## Tech stack

| Layer | Library / Service |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Architecture | MVVM, Hilt (dependency injection) |
| Navigation | Jetpack Navigation Compose |
| Authentication | Firebase Authentication (email/password) |
| Database | Firebase Firestore |
| Remote configuration | Firebase Remote Config |
| Crash reporting | Firebase Crashlytics |
| Networking | Retrofit, OkHttp |
| Image loading | Coil |
| Local persistence | Jetpack DataStore |
| Movie metadata | TMDB API |
| Testing | JUnit, Mockito-Kotlin, Robolectric, Compose UI Test, OkHttp MockWebServer |

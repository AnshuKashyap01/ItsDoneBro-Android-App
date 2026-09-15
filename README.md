# ItsDoneBro

> One more Reel? Nah bro. It's done.

**ItsDoneBro** is an Android app that tracks how much time you spend watching Instagram Reels and how many Reels you consume.

When you reach your chosen daily limit, ItsDoneBro can place a blocking overlay over the Reel and remind you that, well, it's done bro.

## Why ItsDoneBro?

Short-form content is designed to keep you scrolling.

You open Instagram for "five minutes."

Thirty minutes later:

```text
Reels watched: 87
Time spent: 30 min
Original plan: 5 min
```

ItsDoneBro is built to make that behavior visible and give you a simple way to stop.

## Features

### Reel Tracking

Tracks:

- Number of Reels watched
- Time spent watching Reels
- Daily usage
- Weekly usage
- Current scrolling session

### Floating Counter

A small overlay can appear on top of Instagram while you are watching Reels.

```text
Reels: 42
Time: 21:03

Bro... seriously?
```

### Daily Limits

Set a maximum amount of Reel time per day.

Example:

```text
Daily limit: 30 minutes
```

### Reel Blocking

When the limit is reached, ItsDoneBro can cover the Reel with a blocking overlay.

```text
+--------------------------------+
|                                |
|        ITS DONE BRO.           |
|                                |
|        87 Reels                |
|        30 minutes              |
|                                |
|    You had your fun.           |
|    Now go do something else.   |
|                                |
|        [ I'M DONE BRO ]        |
|                                |
|       [ CLOSE INSTAGRAM ]      |
|                                |
+--------------------------------+
```

The goal is not to modify Instagram itself. Instead, the app uses an Android overlay to cover the Reel.

### Multiple Blocking Modes

- **Warning Mode**: The user is warned but can continue.
- **Hard Block**: Reels remain covered after the limit is reached.
- **Cooldown**: Reels are blocked temporarily and become available again after the cooldown.

## Personality

ItsDoneBro is designed to feel like a sarcastic friend rather than a strict parental-control application.

Examples:

```text
Your thumb is doing overtime.
```

```text
Bro. We have plans.
```

```text
This Reel better be important.
```

```text
Instagram has successfully kidnapped another 20 minutes.
```

```text
Congratulations. You said "one more" 47 times.
```

The app should roast the scrolling behavior, not the user.

## How It Works

ItsDoneBro does not have access to Instagram's private internal Reel data.

Instead, the app uses Android's Accessibility Service to infer when the user is viewing Reels.

High-level flow:

```text
User opens Instagram
        |
        v
Accessibility Service detects Instagram
        |
        v
Reel detection
        |
        v
Start tracking
        |
        +----------------------+
        |                      |
        v                      v
Count Reel              Track time
        |                      |
        +----------+-----------+
                   |
                   v
           Daily limit reached?
                   |
             +-----+-----+
             |           |
            No          Yes
             |           |
             v           v
         Continue     Show blocking
         tracking       overlay
```

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose & Material 3
- **Architecture**: MVVM + Clean Architecture
- **Dependency Injection**: Hilt
- **Local Storage**: Room Database & DataStore Preferences
- **System Integrations**: AccessibilityService & WindowManager (Overlays)

## Project Structure

```text
com.itsdonebro
├── accessibility/
│   └── ItsDoneBroAccessibilityService.kt
├── data/
│   ├── db/
│   │   ├── AppDatabase.kt
│   │   ├── DailyStats.kt
│   │   ├── DailyStatsDao.kt
│   │   ├── ReelSession.kt
│   │   └── ReelSessionDao.kt
│   └── preferences/
│       └── SettingsDataStore.kt
├── di/
│   └── AppModule.kt
├── domain/
│   ├── MessageEngine.kt
│   ├── ReelDetector.kt
│   └── TrackingEngine.kt
├── overlay/
│   ├── BlockingOverlay.kt
│   ├── FloatingCounterOverlay.kt
│   ├── OverlayLifecycleOwner.kt
│   └── OverlayManager.kt
├── service/
│   ├── BootReceiver.kt
│   └── TrackingForegroundService.kt
├── ui/
│   ├── dashboard/
│   ├── onboarding/
│   ├── settings/
│   ├── theme/
│   └── navigation/
├── ItsDoneBroApplication.kt
└── MainActivity.kt
```

## Permissions

ItsDoneBro requires the following permissions to function:

- **Accessibility Service**: Used to detect relevant Android UI events and infer when Instagram Reels are active.
- **Display Over Other Apps**: Used to display the floating Reel counter, live timer, and the blocking overlay.
- **Notifications**: Used for background tracking service and optional daily reminders.

## Privacy

ItsDoneBro is designed with a **100% local-first** approach:

- All data stays locally on your device in Room SQLite database.
- No network analytics, no external servers, no cloud sync.
- Does not collect or read messages, photos, videos, or personal data.

## Disclaimer

ItsDoneBro is an independent Android project and is not affiliated with, sponsored by, or endorsed by Instagram or Meta.

## License

```text
MIT License
```

[![Contributors][contributors-shield]][contributors-url]
[![Forks][forks-shield]][forks-url]
[![Stargazers][stars-shield]][stars-url]
[![Issues][issues-shield]][issues-url]
![MIT License][license-shield]

# Kairos

*Everyone has 24 hours a day. Plan your day before it plans you.*

A smart, personalised and practical solution to school/work stress and last minute tasks.
**Kairos** is an Ai assisted smart schedule, allowing you to fit your tasks into an organised and optimised day.

---

## Key Features

*   **AI Powered automatic scheduling:** Saves you time and will maximise your time management.
  
*   **AI assistance:** Planning and chat use the authenticated Kairos Cloudflare API, backed by OpenRouter.
  
*   **Open source:** Shares the Kairos planner and backend-owned Free/Plus AI allowances with the web and iOS clients.
---

## What its Made Of

*   Kotlin
*   **AI Coordination:** Firebase Auth and App Check tokens sent to the Kairos Cloudflare API. No provider key or shared relay secret is bundled in the app.

---

## Try Kairos


### 1. Clone the Repository
```bash
https://github.com/MRHWCKRR/Kairos.git
```
### 2. Use the Website

[Open Kairos](https://kairos-web.kirosapp.workers.dev/app.html)

### 3. Get the Android App

```bash
Coming Soon!
```

---

## Notes:

- Release builds use Firebase App Check with Play Integrity; register the app and its release SHA-256 certificate before release. Debug builds use the debug provider; register the generated device token privately in Firebase App Check. Do not commit debug tokens. See [Firebase setup](https://firebase.google.com/docs/app-check/android/play-integrity-provider) and [debug setup](https://firebase.google.com/docs/app-check/android/debug-provider).
- The backend must allow the registered Android App Check identity before using this build. Deploy the companion web backend change first. There is no fallback to the old relay.
- Shared planner edits preserve unknown web/iOS fields through transactions. Android's recurrence UI still has less functionality than the web client.
- Bugs will likely exist, please create and issue to let me know :)

[contributors-shield]: https://img.shields.io/github/contributors/MRHWCKRR/Kairos-Android.svg?style=for-the-badge
[contributors-url]: https://github.com/MRHWCKRR/Kairos-Android/graphs/contributors
[forks-shield]: https://img.shields.io/github/forks/MRHWCKRR/Kairos-Android.svg?style=for-the-badge
[forks-url]: https://github.com/MRHWCKRR/Kairos-Android/network/members
[stars-shield]: https://img.shields.io/github/stars/MRHWCKRR/Kairos-Android.svg?style=for-the-badge
[stars-url]: https://github.com/MRHWCKRR/Kairos/Kairos-Android
[issues-shield]: https://img.shields.io/github/issues/MRHWCKRR/Kairos-Android.svg?style=for-the-badge
[issues-url]: https://github.com/MRHWCKRR/Kairos/issues
[license-shield]: https://img.shields.io/github/license/MRHWCKRR/Kairos-Android.svg?style=for-the-badge

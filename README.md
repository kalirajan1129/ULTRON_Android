# ULTRON Android prototype

Wake phrase: **Hey ULTRON**

Implemented:
- Always-running foreground voice service using Android SpeechRecognizer.
- Red circular center overlay when awakened.
- Tamil reply: “சொல்லுங்க பாஸ்”.
- Call a saved alias/contact.
- Open Google Maps navigation.
- Open installed apps by spoken app name.
- Fetch and read top five technology headlines using Google News RSS, without a paid API.
- Launch Chennai One and conservative Accessibility-based source/destination filling. It intentionally stops before payment confirmation.

Important limitations:
- This is a prototype, not a guaranteed always-on hotword engine. Android may pause SpeechRecognizer in the background on some devices.
- Chennai One UI automation is intentionally conservative because the app's internal UI structure is not public/stable. App updates can break Accessibility selectors.
- Payment, UPI PIN, OTP and final confirmation are never automated.

## Build
Open in Android Studio and build, or push to GitHub: the included GitHub Actions workflow builds `app-debug.apk` automatically.

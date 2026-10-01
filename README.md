# ULTRON Android — v0.3

Privacy-first Android voice assistant prototype for Tamil + English mixed commands.

## Wake behavior
- ULTRON does **not** keep Android SpeechRecognizer listening continuously.
- Start ULTRON once, enable its Accessibility service, then press **Volume Up 3 times within ~1.2 seconds**.
- ULTRON shows a red circular overlay, says **“சொல்லுங்க பாஸ்”**, and only then opens one-shot speech recognition.
- The mic closes after one command or after the timeout.
- A **Test wake now** button is included for setup/testing.

## Natural-language workflows in v0.3
ULTRON now parses intent + entities instead of requiring exact hard-coded sentences.

### Calls
Examples:
- `Akka-ku call pannu`
- `Amma call`
- `9876543210 call pannu`

Aliases can be saved in the app. If no alias exists, ULTRON tries the phone contacts list.

### Maps
Examples:
- `T Nagar-ku route podu`
- `Velachery to T Nagar route`
- `Anna Nagar navigation open pannu`

### News (free RSS, no paid API key)
Examples:
- `Chennai-la top 5 technology news sollu`
- `India sports news top 3`
- `Tamil Nadu business news today`
- `world science news top 10`

Supported news slots:
- location: Chennai / Tamil Nadu / India / World
- category: general / technology / sports / business / entertainment / science / politics
- count: 1–10
- timeframe: today / latest / week

### Chennai One ticket helper
Examples:
- `Velachery-la irundhu T Nagar-ku bus 51A OTP 4821 ticket book pannu`
- `Velachery to T Nagar ticket book pannu bus 51A code 4821`

ULTRON launches Chennai One and uses Accessibility to *best-effort* fill exposed source, destination, bus number and bus OTP/code fields. It can advance through clearly non-payment navigation controls.

**Safety boundary:** ULTRON deliberately stops when the screen looks like payment/UPI/bank authorization. It does not enter UPI PIN, bank OTP, CVV, card data, or perform final payment authorization.

## Free fallback for unknown questions
If a command is not one of the local workflows, v0.3 opens a Google web search for the spoken query instead of pretending it knows the answer. A future optional local/remote LLM module can replace this fallback.

## Important limitations
- Chennai One is a third-party app and its UI structure can change. Accessibility selectors are therefore best-effort and may need adjustment after Chennai One updates.
- Built-in Android TTS voice depends on the phone's installed TTS voices. ULTRON lowers pitch/rate for a deeper generic voice, but it does not clone a specific real person's voice.
- Android may restrict background services differently across phone manufacturers.
- Another enabled Accessibility service that owns key-event filtering may interfere with the Volume Up x3 shortcut.
- Volume Up presses are not consumed, so the phone volume can rise while waking ULTRON.

## GitHub build
The included workflow `.github/workflows/build-apk.yml` builds a debug APK automatically on every push to `main`.

After the GitHub Actions run succeeds:
1. Open the successful workflow run.
2. Download the artifact **ULTRON-debug-apk**.
3. Extract it to get `app-debug.apk`.
4. Install that APK on the Android phone.

## First-run setup
1. Install and open ULTRON.
2. Allow Microphone, Contacts, Phone and Notification permissions as needed.
3. Allow **Display over other apps**.
4. Tap **Start ULTRON**.
5. Open **Enable ULTRON Accessibility + Volume shortcut** and enable ULTRON.
6. Press Volume Up 3x quickly and say a command after “சொல்லுங்க பாஸ்”.

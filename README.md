# YGO Deck Builder — Android

Native Android client for [YGO Deck Builder](https://github.com/MathieuDubart/ygo-deckbuilder),
the self-hosted Yu-Gi-Oh! collection and deck builder. Kotlin, Jetpack Compose, Android 10+.

It talks to the same API as the web app and the [iOS app](https://github.com/MathieuDubart/ygo-deckbuilder-iOS),
and reads the very same translation files.

## State of the port

This is an in-progress 1:1 port of the iOS app. What works today:

- **Server setup** — enter the address of your instance (HTTPS, or HTTP on your local network / VPN).
  There is no default server on purpose: the app is built for instances you host yourself.
- **Account** — sign in or sign up; the session is renewed automatically on a 401, once at a time.
- **Card scanner** — the camera reads the code printed under the artwork (`SDBE-FR001`), finds the
  card, and queues it with the right print and language. Scan a whole pile without stopping, then
  review the queue — fix quantities, drop a misread — and add everything to the collection at once.
- **Five languages** — English, French, German, Italian, Portuguese, from the web app's own messages.

Still to come, in rough order: collection, deck list and builder, suggestions, owned products,
set tracking, wishlist, tags, social, rules.

Two deliberate omissions:

- **The 3D card showcase is not coming.** It is built on RealityKit, which has no Android
  counterpart; rebuilding it on Filament would be a different feature, not a port.
- **The duel simulator** is not planned for now. The engine runs on the server, but the board,
  the prompts, the replay and the synthesized sound effects are a rewrite of their own.

## Requirements

- A YGO Deck Builder server **with native token auth** (`X-Auth-Mode: token`). The app talks to
  `https://<your-server>/api/…`, the same single port as the browser.
- Android 10 (API 29) or later, and a camera for the scanner — the app installs and runs without
  one, only the scanner screen declares itself unavailable.
- To build: JDK 17 and the Android SDK (Android Studio brings both).

## Building

```bash
./gradlew :domain:test        # the pure rules, in about a second
./gradlew :app:assembleDebug  # the APK
```

**CI is the reference compiler for this repository.** The code is written in an environment with
no Android SDK and no access to Maven, so nothing is considered to build until the `Android`
workflow is green. It runs the domain tests, assembles the APK, and uploads it.

## Installing the APK

Every push builds `ygo-deckbuilder-debug-<sha>` and attaches it to the workflow run. Download it
from the Actions tab, unzip, and move the `.apk` to your phone — you will have to allow installs
from whichever app you transfer it with. It is signed with the CI's debug key, which is exactly
what sideloading needs and useless for a store.

The debug build installs as `dev.ygo.app.debug`, so it can sit next to a release build.

## Architecture

```
domain/                   pure Kotlin, no Android: print codes, deck rules, Cardmarket links
app/src/main/kotlin/dev/ygo/app/
  core/
    net/                  ApiClient (auth header, locale, single-flight refresh on 401), endpoints, DTOs
    auth/                 TokenStore (tokens and server address, in DataStore)
    l10n/                 L10n, reading the shared messages through ICU MessageFormat
    state/                AppState (server, session, data versions)
  ui/                     theme, root routing (server → sign-in → screens)
  feature/                one package per screen (onboarding, auth, home, scanner)
  assets/                 messages.<lang>.json (generated)
localization/             Android-only strings (android.<lang>.json)
scripts/                  sync-messages.py
```

- **State**: one `AppState` created by the activity and passed down, no dependency injection
  framework. Same shape as the iOS app's `AppState`, so the two read like each other.
- **`domain/` is the third port of the same rules**, after `packages/shared` (TypeScript) and
  `Core/Domain` (Swift). The three must agree: a print code accepted on one platform and not
  another is a card you can file from the browser but not from your phone. Everything there is
  tested, with the cases the other two use.
- **Tokens live in a preferences DataStore, not the Keystore.** An access token lasts fifteen
  minutes and the refresh token is revocable server-side, so the gain of hardware-backed storage
  looked small next to its complexity. Worth revisiting if the app ever has to hold up on a
  shared device.

## Translations

UI strings are the web app's own messages (`apps/web/messages`, ICU format) plus an `android`
namespace. `app/src/main/assets/messages.<lang>.json` is **generated** — do not edit it:

```bash
python3 scripts/sync-messages.py            # expects ../ygo-deckbuilder next to this repo
python3 scripts/sync-messages.py /path/to/ygo-deckbuilder/apps/web/messages
```

A string that also shows on the web belongs in the web repository. A string that only exists here
goes in `localization/android.<lang>.json`, and all five languages are kept in step.

## Credits

Card data and images: [YGOPRODeck](https://ygoprodeck.com/). Box art and set lists:
[Yugipedia](https://yugipedia.com/). Yu-Gi-Oh! is a trademark of Konami; this project is not
affiliated with or endorsed by Konami.

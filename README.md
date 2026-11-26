# NFC Transit Reader

An Android app that reads the **remaining balance and recent trip history** from
contactless public-transit cards over NFC. It is a read-only inspector in the
same spirit as the open-source *FareBot* / *Metrodroid* apps.

Primary supported card: **Clipper** (San Francisco Bay Area — VTA, Caltrain,
BART, Muni, AC Transit, and the ferries). Other NFC cards show a generic info
panel (UID, technologies, DESFire application IDs).

## What it does and doesn't do

- ✅ Reads balance, last-transaction time, and recent trips from a Clipper card.
- ✅ Shows UID / card type for other NFC cards.
- ❌ Does **not** write, modify, clone, or emulate any card. It only reads.
- ❌ Cannot read key-protected data (e.g. most MIFARE Classic transit cards),
  because it performs no authentication and carries no keys. Clipper works
  because the balance and trip files are freely readable.

This reads only what the card already exposes to any reader — nothing is
decrypted or bypassed.

## Requirements

- An Android phone with NFC (Android 8.0 / API 26 or newer).
- Android Studio (Ladybug / 2024.2 or newer recommended).
- JDK 17 (bundled with recent Android Studio).

## Build & run

1. Open **Android Studio** → **Open** → select this folder
   (`nfc android card`). Let it finish the first Gradle sync — it will download
   Gradle 8.10.2 and the Android dependencies, and generate the Gradle wrapper
   if needed.
2. Plug in your phone with **USB debugging** enabled (Settings → Developer
   options), or set up a physical device — note that the **Android emulator has
   no NFC**, so you need a real phone to actually read a card.
3. Press **Run** (the green ▶). The app installs and launches.

Command line alternative, once Android Studio has generated the wrapper:

```
./gradlew installDebug      # macOS / Linux
gradlew.bat installDebug    # Windows
```

## Using it

1. Launch the app. It shows "Tap a transit card to the back of your phone."
2. Hold the card flat against the phone's NFC antenna (usually upper-middle of
   the back). Keep it still for a second.
3. The Clipper balance and recent trips appear. Tap another card to replace.

## How Clipper parsing works

Clipper is a MIFARE DESFire card. The app selects the Clipper application
(`0x9011F2`, or `0x9111F2` for a phone-provisioned credential) and reads a few
freely readable files:

| File   | Contents                                   |
|--------|--------------------------------------------|
| `0x02` | E-cash: balance (offset `0x12`, signed, cents) + last txn time |
| `0x06` | History index (ordering of trip records)   |
| `0x08` | Card serial number                         |
| `0x0E` | Ride history — 32-byte trip records        |

Timestamps are stored as seconds since 1900-01-01 GMT. Byte offsets were taken
from the open-source Flipper Zero `clipper.c` parser.

## Project layout

```
app/src/main/java/com/jml/transitreader/
├─ MainActivity.kt                 NFC reader-mode plumbing + Compose host
├─ nfc/
│  ├─ DesfireProtocol.kt           DESFire commands over ISO-DEP (read-only)
│  ├─ TransitCardReader.kt         Tag dispatch → parser → result
│  └─ Hex.kt
├─ parser/
│  ├─ Models.kt                    ReadResult / ClipperCard / ClipperTrip
│  ├─ ClipperParser.kt             Clipper byte-layout parsing
│  └─ ClipperAgencies.kt           Agency + station name tables
└─ ui/
   ├─ ReaderScreen.kt              Compose UI (balance card + trip list)
   └─ Theme.kt
```

## Adding another card

1. Add a parser in `parser/` that takes a `DesfireProtocol` (or reads other tech
   via its own `Tag` handling) and returns a model.
2. Call it from `TransitCardReader.read()` before the generic fallback.
3. Add a `ReadResult` variant and a view in `ReaderScreen.kt`.

Station/agency names for Clipper live in `ClipperAgencies.kt` — the BART and
Caltrain maps are partial; extend them to label more stops.

## Legal / ethical note

Use this only on cards you own or are authorized to read. It's an inspector for
your own balance and trips; it does not defeat any access control.

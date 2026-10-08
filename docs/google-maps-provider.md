# Google Maps Navigation Provider Architecture

## 1. Overview

The Google Maps integration acquires turn-by-turn guidance without modifying or reverse-engineering Google Maps binaries. It utilizes Android's standard **`NotificationListenerService`** API:

```text
[ Google Maps Navigation ]
           │
           │ (Status Bar Notification: "Turn left onto MG Road • 250 m • 12 min")
           ▼
[ GoogleMapsNotificationListenerService ]
           │ (Raw Title, Text, SubText)
           ▼
[ GoogleMapsNotificationParser ]
           │ (Regex extraction of Maneuver, Distance, ETA, Road)
           ▼
[ NavigationUpdate ]
           │ (Emitted via Kotlin Flow)
           ▼
[ NavigationBridge ]
```

---

## 2. Text Parsing Specifications

The parser extracts information using regular expressions that handle international units and formatting variants:

### 1. Distance Extraction
- **Units Handled**: meters (`m`, `meters`), kilometers (`km`), feet (`ft`), miles (`mi`).
- **Normalized Unit**: Converted to standard integer meters.
- Examples:
  - `"250 m"` ➔ `250`
  - `"1.2 km"` ➔ `1200`
  - `"500 ft"` ➔ `152`
  - `"0.5 mi"` ➔ `804`

### 2. Maneuver Classification
Maneuver keywords are parsed case-insensitively:
- `"left"`, `"turn left"` ➔ `Maneuver.LEFT`
- `"right"`, `"turn right"` ➔ `Maneuver.RIGHT`
- `"slight left"`, `"bear left"` ➔ `Maneuver.SLIGHT_LEFT`
- `"slight right"`, `"bear right"` ➔ `Maneuver.SLIGHT_RIGHT`
- `"u-turn"` ➔ `Maneuver.U_TURN`
- `"roundabout"`, `"rotary"` ➔ `Maneuver.ROUNDABOUT`
- `"straight"`, `"continue"` ➔ `Maneuver.STRAIGHT`
- `"arrived"`, `"destination"` ➔ `Maneuver.DESTINATION`

### 3. Road Name & Primary Instruction
- Delimiters such as `" onto "`, `" on "`, and `" toward "` isolate the target street name.
- Strings exceeding 17 bytes are preserved in memory while Frame B sets length indicators per protocol rules.

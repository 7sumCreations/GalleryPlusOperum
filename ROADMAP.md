# Roadmap

Where GrapheneGalleryFunctio is going. Work is planned in epics; each one ships only after it has been verified by hand on a real GrapheneOS phone. Every feature on this list follows the same ground rules: no internet access, no accounts, nothing leaves the device unless you send it, and anything automatic is opt-in.

Status key: ✅ done · 🔨 next · 💭 later (planned, not yet scheduled). Epics are listed in build order.

## ✅ Epic 1 · File by hand + auto-file

Turn the read-only gallery into one where you file photos into real folders, safely, and camera photos can file themselves by date.

- Move and copy photos and videos to a folder, one or many at a time
- Background progress with cancel
- One permission dialog per batch for media the app didn't create
- Create, rename, move (nest / re-parent) and delete folders
- Favourites album
- Trash with restore, delete permanently, empty and automatic purge
- Undo the last operation
- Auto-file new camera photos into `Pictures/YYYY/MM`
- Hardening: unused permissions removed, sensor access removed, system share sheet, no file-provider exposure

v0.1.1:

- In-app Help screen
- Asks for photo and notification access on first launch, and explains what to do if you said no
- Move and copy from the full-screen viewer
- The Favourite star shows whether a photo is already a favourite
- Auto-file tells you what it filed, with Undo, and when it had to skip photos
- Security hardening: fewer ways in for other apps, no camera permission, no app-data backup

## 🔨 Epic 2a · Fast filing, export, folder hygiene

Make filing fast, get photos off the phone on your terms, and tidy the grid.

- Recent destinations at the top of the folder picker
- Quick-file to the last-used folder
- Swipe-to-file in the viewer
- Suggested folder names from the photos
- Export a selection or folder to a destination of your choice (USB drive, storage provider)
- Export as zip
- Import from a folder or USB drive
- Hidden folders
- Pinned folders and custom album order

## 🔨 Epic 2b · Encrypted vault

Folders that live encrypted at rest inside the app's own private storage, unlocked with biometrics or a passphrase, and invisible to every other app — even apps that hold full photo permission. Items leave the vault only when you share them.

## 💭 Epic 3 · Safer batches & folder care

Make big batches and folder housekeeping safe.

- Name-collision choices (skip / replace / keep both), asked once per batch
- Resume or roll back an interrupted batch
- Lock a folder against move / delete
- Empty-folder handling
- Folder sizes and a "biggest folders" view
- Choose how long Trash keeps items (e.g. 10 / 20 / 30 days)

## 💭 Epic 4 · Works with other apps + accessibility

File a photo from wherever you see it, and make filing usable for everyone.

- "Move to" and "Copy to" a folder straight from the share sheet inside the gallery
- "Save to folder" as a share target from other apps
- Open a folder in your file manager, and open a folder from it in the gallery
- Accessible picker and multi-select (TalkBack, large text)

## 💭 Epic 5 · Metadata & timeline

- On-device EXIF index
- EXIF panel: view, add, edit, and strip location
- Per-folder sort and filter
- Timeline with date headers and a scrubber
- Picking a date for photos with none

## 💭 Epic 6 · Bulk filing

- Sort a folder into sub-folders by pattern
- Batch rename by pattern
- Operation history screen

## 💭 Epic 7 · Search & tags

- Search across names, places and dates
- Tags and ratings written into the files themselves (XMP)
- Smart folders (saved searches)

## 💭 Epic 8 · On-device intelligence

Opt-in, and never leaves the phone.

- Scene and object labels
- People (faces)
- Duplicate and blurry-photo clean-up review

## 💭 Epic 9 · Smarter auto-file

- Choose which folders auto-file watches
- A "not auto-filed" view
- A gentle "file this" nudge for new photos
- A filing stats card

## 💭 Epic 10 · Backup & sync

- Checksum manifest with every export
- One-way backup to a server you own, with per-folder backup status
- Backup and restore of the app's own settings

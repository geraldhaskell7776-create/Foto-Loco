# Foto Loco

An Android app that shows your photos as a card feed with **when** and **where** each one was taken.

- Each card shows the photo, the date and time ("Oct 24, 2026 • 2:15 PM"), and the place ("Eagle Mountain, UT").
- Tap a photo to open a full-screen viewer: swipe for the next photo, pinch or double-tap to zoom, tap or press back to return.
- Tap a place to show only photos from there. Tap the "Filtered by" chip to clear it.
- Delete photos: tap the trash icon in the full-screen viewer, or long-press photos in the feed to select several and tap the trash icon. On Android 11+ they go to the system trash, where they can be restored for about 30 days.
- Swipe left (or tap **Places** at the bottom) to see every place your photos were taken, with a cover photo and count. Tap one to see its photos.

## Install on your phone

1. On your Android phone, open this repo's **Releases** page and pick the newest "Foto Loco build".
2. Tap the `FotoLoco-<number>.apk` file to download it.
3. Open it. If asked, allow your browser to install unknown apps, then tap **Install**.
4. Open Foto Loco and allow photo access.

Every push to GitHub builds a new APK automatically (see `.github/workflows/build-apk.yml`). New builds install over old ones.

## Tech

Kotlin, Jetpack Compose, Material 3, MVVM (`ViewModel` + `StateFlow`), Coil for images, `MediaStore` + AndroidX `ExifInterface` for photo data, and Android's `Geocoder` for place names. Min Android 8.0 (API 26), targets Android 15 (API 35).

| File | What it does |
| --- | --- |
| `app/src/main/AndroidManifest.xml` | Permissions and app setup |
| `app/build.gradle.kts` | Dependencies |
| `data/PhotoItem.kt` | Photo and location models |
| `data/PhotoRepository.kt` | MediaStore query, EXIF reading, reverse geocoding |
| `data/MediaPermissions.kt` | Which permissions to ask for on each Android version |
| `data/PhotoDeleter.kt` | Deleting photos on each Android version |
| `ui/PhotoDeletion.kt` | Delete confirmation and system permission prompt |
| `PhotoViewModel.kt` | Feed state, background metadata loading, location filter |
| `ui/feed/PhotoFeedScreen.kt` | Feed, filter chip, permission/empty/loading states |
| `ui/feed/PhotoCard.kt` | One photo card |
| `ui/places/PlacesScreen.kt` | Grid of every place with a cover photo and count |
| `ui/viewer/FullScreenViewer.kt` | Full-screen pager with pinch-to-zoom |

The APK is signed with `app/sideload.keystore` so updates install cleanly. That key is public in this repo, so it's fine for personal sideloading but shouldn't be used for a Play Store release.

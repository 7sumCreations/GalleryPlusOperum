# Gallery help

A private gallery for your photos and videos. Everything stays on your phone.

## Before you start

The app does not ask for permissions on its own. To set it up:

- Open Android **Settings → Apps → Gallery → Permissions**.
- Allow **Photos and videos**. Without this the app has nothing to show.
- Allow **Notifications** if you want to see progress, and a Cancel button, while photos are being moved or copied.

## Selecting photos

- **Long-press** a photo or video to select it.
- **Tap** more items to add them, or tap a selected one to remove it.
- To select everything, tap the "1 selected" title at the top and choose **Select all**.
- Press **Back** to stop selecting.

The buttons at the top act on everything you selected. Anything that doesn't fit there is under **⋮**.

## Move or copy to a folder

- Select photos or videos → tap **⋮** → **Move** or **Copy to folder**.
- Pick a folder from the list. The list shows folders inside Pictures and DCIM that already have something in them.
- To make a new folder, tap **+ New folder** at the top of the list, type a name and tap **OK**. Your photos go straight into it. New folders are made inside **Pictures**.
- **Move** takes the photos out of their old folder. Their dates don't change.
- **Copy** leaves the originals where they are and puts a second copy in the folder you chose.
- If a file with the same name is already there, the new one gets a number, like "IMG_1234 (1).jpg". Nothing is overwritten.

While the app works, a notification shows how far it has got (for example "3 of 20") with a **Cancel** button. Cancel stops after the current item. Whatever was already done stays done, and you can still Undo it.

If one item fails, the app stops there and tells you how many worked and how many failed.

## The permission dialog

Android protects photos made by other apps, such as the ones your camera takes. Before this app can move, delete, restore or favourite them, Android asks you.

- You get **one** dialog for the whole selection, not one per photo.
- **Allow**: the app finishes the job.
- **Deny**: those items stay exactly as they were. The result tells you how many failed.
- If you had already left the app, tap the progress notification to bring the dialog back.

To stop seeing this dialog, see "Manage media without asking" below.

## Undo

- After you move, copy, delete, restore or favourite photos, a bar appears at the bottom of the screen, for example "Done. 5 photos." with **UNDO**.
- The bar stays for **10 seconds**. Tap **UNDO** to reverse the whole action.
- Only the last action can be undone. If you leave the app before the job finishes, Undo isn't shown.
- Undoing a copy deletes the copies. The originals are untouched.
- There is no Undo for **Delete permanently**, **Empty trash**, renaming or moving a folder, or auto-file.

## Folders

On the Albums screen, long-press a folder to select it. Then:

- **Rename**: tap **⋮** → **Rename folder**, type the new name, tap **OK**.
- **Move**: tap **⋮** → **Move folder** and pick the folder to put it in. Everything inside it, including sub-folders, goes with it.
- **Delete**: tap the bin. Everything in the folder goes to **Trash**, so you can Undo it or restore it later.
- **New folder**: on the Albums screen, tap **⋮** → **+ New folder**. It is made inside Pictures.

Good to know:

- The **Camera** folder can't be renamed, moved or deleted, because your camera app needs it.
- Renaming or moving a folder **can't be undone**. To reverse it, rename or move it back.
- If a folder with that name is already there, the app refuses rather than mixing the two together.
- Rename and Move work on one folder at a time.

## Favourites

- **Add**: select photos → tap the **star**. Or open a photo full screen and tap the star.
- **Remove**: open the photo full screen and tap the star again. (In a selection, the star only adds.)
- The **Favourites** album is at the top of the Albums screen. Photos stay in their own folders; Favourites just gathers them in one place.
- Photos marked as favourites by other apps show up here too.
- For now, Favourites shows photos only, not videos.

## Trash

- **Delete** asks first, then sends photos and videos to **Trash**. Nothing is erased straight away.
- The **Trash** album is at the bottom of the Albums screen.
- **Restore**: open Trash, select items → **Restore**. They go back to the folder they came from. You can also open one full screen and tap **Restore**.
- **Delete permanently**: open Trash, select items → **⋮** → **Delete permanently**. This can't be undone.
- **Empty trash**: open Trash → **⋮** → **Empty trash**. It asks first. This can't be undone.
- Items that have been in Trash for **30 days** are removed for good automatically. The app checks about once a day.

## Sharing

- Select one or more photos or videos → tap **Share**. Or open one full screen and tap **Share**.
- Android's own share sheet opens, so you can pick any app.
- The app you share with can open only the items you picked.
- You can share up to about 300 items at a time.

## Auto-file new photos

Find it in **⋮ → Settings → Auto-file new photos**.

It moves new photos out of one folder and into **Pictures/year/month** (for example Pictures/2026/10), using the date each photo was taken.

- It is **off** until you switch on **File new photos automatically**.
- It only files photos added **after** you switch it on. Photos you already had are never touched. Switching it off and on again starts fresh from that moment.
- **Watch this folder** is the folder it looks in. The default is DCIM/Camera, where your camera saves. Photos in sub-folders are left alone.
- **Wait before moving** is how many minutes a new photo is left alone first (default 5, anything from 1 minute to 1 day). The app checks in the background, so it can take a little longer than that.
- It files **photos only**, not videos. A photo with no date taken stays where it is.
- It never shows the permission dialog. If Android would need to ask before moving a photo, auto-file leaves it and tries again on its next check.
- It works quietly: there is no notification and no Undo. To put a photo back, select it and use **Move**.
- To stop it, switch off **File new photos automatically**. That stops all automatic moves straight away. Photos already filed stay where they are.

## Manage media without asking

Android 12 and newer only. Find it in **⋮ → Settings → Media access → Manage media without asking**.

- It is off unless you turn it on. The app never asks for it by itself.
- Tapping it opens Android's own settings page, where you can allow or not allow it.
- **Allowed** means this app can change, move or delete any photo or video on your phone without asking you each time, so the permission dialog stops appearing. Only turn it on if you're comfortable with that.
- You can turn it off again at any time on the same page. The line under the setting shows whether it is allowed right now.

## Trim and mute videos

- Open a video full screen → **⋮** → **Trim** or **Mute**.
- **Trim**: drag the handles to the part you want to keep, then tap **Save** at the top.
- **Mute** makes a copy of the video with no sound.
- Both save a **new** video, usually in the same folder. The original isn't changed.
- If the video is in an unusual folder (not DCIM, Movies or Pictures), saving may fail with "Can't trim video." or "Can't mute video." Move the video into a folder in Pictures first.

## Privacy

- **No internet.** The app has no permission to go online, so Android won't let it connect. No ads, no tracking, no cloud, no update checks.
- **Nothing leaves your phone** unless you share it yourself.
- **Few permissions.** It doesn't use the microphone, your contacts or accounts, or your phone's location.
- **No sensors.** It doesn't read the motion sensors.
- **Your choice.** "Manage media without asking" is only ever turned on by you.

## Known limitations

- **Move** and **Copy** show up in the full-screen viewer's menu but don't work there yet. Select the photos in the grid instead.
- The star in the full-screen viewer doesn't show whether a photo is already a favourite. Tapping it switches the photo in or out of Favourites.
- Favourites shows photos only, not videos.
- Renaming or moving a folder can't be undone.
- In Trash, camera photos may show a blank thumbnail.
- Tapping **Deny** straight after making a new folder can make that new, empty folder disappear.
- Trim and Mute may fail for videos in unusual folders.
- Auto-file has no notification and no Undo.
- The app hasn't been tested on Android 10.

## Something went wrong?

- Report it at https://github.com/cpw7776/GrapheneGalleryFunctio/issues (you need a free GitHub account).
- Say what you did, what you expected and what happened. Add your phone model, Android version and the app version (shown in Android **Settings → Apps → Gallery**).
- If the app crashed and Android shows **Show details**, tap it, copy the text and paste it into your report. Read it first and remove anything you'd rather keep private.

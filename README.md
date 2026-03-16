# DCIM Cleaner
![Static Badge](https://img.shields.io/badge/Built_with_Claude-333333?style=for-the-badge)
![Static Badge](https://img.shields.io/badge/—_vibe_coded_with_love_<3-ff0000?style=for-the-badge)

**An Android app to help you clean up your camera roll.** This app takes you to a random date in your camera roll and lets you trash photos in one tap or two. Comes with widgets as well.

[![App preview](https://i.imgur.com/iO7p6z6.jpeg)](https://i.imgur.com/iO7p6z6.jpeg)
> Scrolling through thousands of photos to clean them up is hard. So instead, this app scans your camera roll by date — you pick a random month or day, see what's there, and decide what to keep.

## Features

**📊 DCIM Analyzer**
- Breaks down your photos by Month, Year, and Day — showing file count and size per period. Columns are sortable. Tap any row to open that batch in the grid.

**🎲 Random Picker**
- Loads a random Month or Day of photos into the grid.

**⚡ Quick Trash**
- Toggle to enable. Tapping a photo moves it to trash immediately.

**🖼 Grid**
- 3 or 5 column layout
- File size on each thumbnail
- Swipe down in full screen to dismiss

**⚙️ Settings**
- Re-index DCIM folder
- Ignore specific subfolders (e.g. Screenshots)

**⬛ Widget (2x1) — "clean me up" widget**
- Clickable widget that shows you random date every 6 hours.
- Displays date, its size & number of photos you can clean up.

**⬛ Widget (4x1) — "today & yesterday storage" widget**
- Watch your storage with a widget that you can add in your home screen.
- Displays your available storage space, updated every day.
- Keeps track of your storage so you can compare how many space you've lost or gained from yesterday.

[![Widget preview](https://i.imgur.com/7lHf8Fu.jpeg)](https://i.imgur.com/7lHf8Fu.jpeg)

> [!WARNING]
> ⚠️ **Personal use only.** This app was AI-generated and is not fully tested. No APK will be distributed — build it yourself via Android Studio.

## Android Studio Setup

1. Open the `DCIMCleaner` folder via **File → Open**
2. Wait for Gradle sync
3. On your phone: **Settings → About phone** → tap **Build number** 7 times
4. Enable **USB Debugging** under **Developer Options**
5. Plug in via USB, tap Allow on the prompt
6. Select your device in the toolbar and hit ▶ Run
7. Grant storage permission — indexing starts automatically

> Built with Claude by Anthropic — [claude.ai](https://claude.ai)

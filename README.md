# Focus

Focus is a small Android app that helps you stay on track. It counts the reels you watch on
Instagram and, when you reach your limit, shows a reminder of the dream you are working toward.

## Features

- Reminder popup with your dream, your photo and a motivational quote
- **Automatic dream photo:** type your dream (for example "Mahindra Scorpio") and a matching
  photo is loaded from the internet. Tap **Next photo** to see another one
- Or pick your own photo from the gallery
- Adjustable reel limit and an optional relaxed detection mode
- Live status card with a progress bar
- No data is collected or shared. Counting happens only on your device

## Build the APK (GitHub Actions)

1. Create a new GitHub repository and upload all project files (including the `.github` folder).
2. Open the **Actions** tab and let **Build APK** finish (3-5 minutes).
3. Download **Focus-APK** from Artifacts, unzip it and install `app-debug.apk`.

## First-time setup on the phone

1. Open Focus, write your dream and tap **Save settings**.
2. Tap **Open Accessibility settings** and turn on **Focus**.
   On Android 13+, first tap **Allow restricted settings (App info)** and allow restricted
   settings from the three-dot menu.

## Tips

- Photos come from Wikipedia and Wikimedia Commons, so an internet connection is needed.
  Specific names work best ("Mahindra Scorpio" is better than "Scorpio").
- If the scroll events number stays at 0, the accessibility service is not active.
  If events increase but reels are not counted, turn on **Relaxed detection**.

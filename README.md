# CarPlay Restart

One big glowing button for the Dasaita radio. When CarPlay or Android Auto gets stuck after the phone drops off and comes back (out of the car for a delivery, back in), tap the **Restart** orb: it closes ZLink, the app that runs CarPlay and Android Auto on the radio, and opens it fresh. **Hold to reboot radio** restarts the whole radio for the times that isn't enough.

## Install on the radio

1. Download the newest `CarPlay-Restart-<version>.apk` from the repo's **Releases** page. Either open the page in the radio's browser (signed in to GitHub), or download it on your phone or a computer and copy it to a USB stick.
2. Open the APK on the radio (from Downloads, or the radio's file manager for the USB stick) and tap **Install**. If the radio asks, allow that app to install unknown apps. Newer versions install over the old one and keep your settings and stats.

## First time: turn on the helper

Android doesn't let one app close another by itself, so on a radio without root a small helper does the tapping.

1. Open CarPlay Restart and tap **Set up** (or just tap the orb), then **Open settings**.
2. Find **CarPlay Restart** in Accessibility (it can be under *Downloaded apps* or *Installed services*) and turn it on.
3. If the radio says **Restricted setting** (Android 13 and newer): go to Settings › Apps › CarPlay Restart, tap **⋮** at the top, choose **Allow restricted settings**, then turn it on again.

The status card turns green: **Ready · Helper on**. If the radio has root, none of this is needed: it says **Ready · Root access**.

## Using it

- **Restart orb:** closes ZLink and opens it again. The comet races round the ring while it works, and the orb turns green when it's done (amber if it didn't go through). With the helper you'll see ZLink's App info page flash up for a second while it presses **Force stop** and **OK**; then ZLink opens and your phone reconnects.
- **Hold to reboot radio:** press and hold for about a second and a half while the button fills, then the whole radio restarts (about a minute). Let go early to cancel. With root it reboots directly; with the helper it opens the power menu and presses **Restart**. Some radios have no power menu, and then rebooting needs root.
- **Last restart / Today:** when the last restart was and whether it worked, and how many restarts have worked today.
- **Settings (gear):** pick a different app if your radio runs CarPlay through something other than ZLink, choose whether to open it again after restarting, and see whether the helper and root are on.
- **Open it as (in Settings):** **Auto** (the default) just opens ZLink and lets it choose, which normally means CarPlay for an iPhone and Android Auto for an Android phone. Pick **Apple CarPlay** or **Android Auto** if ZLink opens on the wrong one or waits for a tap: after the restart, the helper presses that button in ZLink for you. This needs the helper on, even with root, and if ZLink has no button with that name nothing happens.

## How it's built

A small Kotlin app with no libraries (`app/src/main/java/app/carplayrestart`):

- `MainActivity.kt`: the orb, the cards and Hold to reboot. With root, it runs `am force-stop` on the CarPlay app and opens it again, or `svc power reboot`.
- `HelperService.kt`: the helper (an accessibility service) for radios without root. It only acts right after a button press: it opens the app's App info page and presses Force stop and OK (then, if you picked Apple CarPlay or Android Auto, presses that button in the app once it opens), or opens the power menu and presses Restart, finding the buttons by name (English, Spanish and Chinese).
- `OrbView.kt`, `AuroraView.kt`, `GlassLayout.kt`, `HoldButton.kt`, `PulseDot.kt`: the look, all drawn by hand: the glowing orb with its gauge ring and orbiting comet, the slowly drifting aurora backdrop, the frosted-glass cards, the hold-to-confirm button and the pulsing status light. The animation runs on the display's frame clock, so it keeps moving on radios with system animations turned off (and Hold to reboot can't be fooled into firing on a tap), and it stops whenever the screen isn't showing.
- `Apps.kt`: finds ZLink (`com.zjinnova.zlink`) or another CarPlay app on the radio.

The app asks for no permissions: no internet, no storage. The font is Sora (SIL Open Font License, `licenses/OFL-Sora.txt`).

GitHub builds the APK on every push to `main` (`.github/workflows/build-apk.yml`) and puts it on the Releases page. **Actions › Clean up old releases** deletes old builds.

CarPlay Restart isn't made by or connected to Apple, Google, Dasaita or ZLink.

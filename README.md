# CarPlay Restart

One big button for the Dasaita radio. When CarPlay or Android Auto gets stuck after the phone drops off and comes back (out of the car for a delivery, back in), tap **Restart**: it closes ZLink, the app that runs CarPlay and Android Auto on the radio, and opens it fresh. **Hold to reboot radio** restarts the whole radio for the times that isn't enough.

It can also fix CarPlay by itself when it drops (**auto-fix**), put a small **floating restart button** over ZLink, add a **home screen widget**, clear ZLink's cache, and come in six looks.

## Install on the radio

1. Download the newest `CarPlay-Restart-<version>.apk` from the repo's **Releases** page. Either open the page in the radio's browser (signed in to GitHub), or download it on your phone or a computer and copy it to a USB stick.
2. Open the APK on the radio (from Downloads, or the radio's file manager for the USB stick) and tap **Install**. If the radio asks, allow that app to install unknown apps. Newer versions install over the old one and keep your settings and stats.

## First time: turn on the helper

Android doesn't let one app close another by itself, so on a radio without root a small helper does the tapping. Auto-fix and the floating button need it too, even with root.

1. Open CarPlay Restart and tap **Set up** (or just tap Restart), then **Open settings**.
2. Find **CarPlay Restart** in Accessibility (it can be under *Downloaded apps* or *Installed services*) and turn it on.
3. If the radio says **Restricted setting** (Android 13 and newer): go to Settings › Apps › CarPlay Restart, tap **⋮** at the top, choose **Allow restricted settings**, then turn it on again.

The status card turns green: **Ready · Helper on**. If the radio has root it says **Ready · Root access**, and restarts happen instantly without the helper.

## Using it

- **Restart:** closes ZLink and opens it again. The button animates while it works, then shows **Done** (or **Try again** if it didn't go through). With the helper you'll see ZLink's App info page flash up for a second while it presses **Force stop** and **OK**; then ZLink opens and your phone reconnects.
- **Hold to reboot radio:** press and hold for about a second and a half while the button fills, then the whole radio restarts (about a minute). Let go early to cancel. With root it reboots directly; with the helper it opens the power menu and presses **Restart**. Some radios have no power menu, and then rebooting needs root.
- **Last restart / Today:** when the last restart was and whether it worked, and how many restarts have worked today.

## Settings

Settings (the gear) has a menu down the left side:

- **CarPlay app:** pick a different app if your radio runs CarPlay through something other than ZLink, choose whether to open it again after restarting, and **Open it as**: **Auto** (the default) lets ZLink choose; **Apple CarPlay** or **Android Auto** makes the helper press that button in ZLink after each restart.
- **Look:** six looks: **Aurora** (the original glowing orb), **Redline** (a tachometer on carbon fiber), **Neon Drive** (a synthwave sunset), **Warp** (a portal in deep space), **Midnight** (quiet black and gold) and **Sakura** (cherry blossoms). The floating button and the widget follow the look.
- **Auto-fix** (off until you turn it on; needs the helper): every few seconds it checks whether ZLink is showing CarPlay. It restarts ZLink when CarPlay drops and ZLink sits on its own screen for 30 seconds, when ZLink is stuck on "Connecting…" for 40 seconds, or when CarPlay still hasn't shown up 25 seconds after you plug in a phone. It only starts acting once it has seen CarPlay running in ZLink, so it knows what CarPlay looks like there. It holds back while another app is in front, while you're tapping around in ZLink, for 45 seconds after any restart and for a minute after the radio wakes up. It tries once each time; after 3 tries that don't bring CarPlay back it waits until CarPlay is back or you restart it yourself, and it never restarts more than 3 times in 10 minutes. Each rule has its own switch, and **Recently** lists what it did.
- **Quick restart:** the **floating restart button** floats over ZLink (only while ZLink is in front): tap it to restart, drag it anywhere (it settles against the nearer side), hold it to open this app. The **home screen widget** is a one-tap Restart button; **Add** puts it on the home screen if the radio's home screen allows it, otherwise add it from the home screen's widget list.
- **Fixes:** **Clear cache & restart** clears ZLink's temporary files (not its settings or paired phones) and restarts it, with root or through ZLink's Storage page with the helper. **Reset Wi-Fi & Bluetooth** turns both off and on and then restarts ZLink, for wireless CarPlay that won't connect; it needs root.
- **Helper & root:** whether the helper is on and whether the radio has root.

## How it's built

A small Kotlin app with no libraries (`app/src/main/java/app/carplayrestart`):

- `MainActivity.kt`: the main screen. It builds the chosen look's scenery and Restart button, the cards and Hold to reboot.
- `Restarter.kt`: restarts ZLink the same way from everywhere (the main screen, the floating button, the widget, auto-fix). With root it runs `am force-stop` and opens ZLink again (`svc power reboot` for a reboot, `svc wifi` / `svc bluetooth` for the reset); otherwise it hands over to the helper.
- `HelperService.kt`: the helper (an accessibility service). It opens the app's App info page and presses Force stop and OK (via Storage › Clear cache first if asked), presses Apple CarPlay or Android Auto in ZLink if you picked one, or opens the power menu and presses Restart, finding the buttons by name (English, Spanish and Chinese). It also runs auto-fix (`AutoFix.kt`) and the floating button (`Bubble.kt`), and notices when a phone is plugged into USB.
- `QuickRestart.kt`: the home screen widget, and the see-through screen it opens to restart without showing the app.
- `Themes.kt`: the six looks' colors and fonts, and `Styler`, which paints each screen in the chosen look. `HeroView.kt` and `BackdropView.kt` are the base for each look's Restart button and scenery: `OrbView.kt` / `AuroraView.kt` (Aurora), `RedlineViews.kt`, `NeonViews.kt`, `WarpViews.kt`, `MidnightViews.kt` and `SakuraViews.kt`. `ThemeSwatch.kt` draws the little previews in Settings › Look.
- `GlassLayout.kt`, `HoldButton.kt`, `PulseDot.kt`: the cards, the hold-to-confirm button and the pulsing status light. All the animation runs on the display's frame clock, so it keeps moving on radios with system animations turned off (and Hold to reboot can't be fooled into firing on a tap), and it stops whenever the screen isn't showing.
- `SettingsActivity.kt`, `SetupDialog.kt`: Settings and the "Turn on the helper" card.
- `Apps.kt`: finds ZLink (`com.zjinnova.zlink`) or another CarPlay app on the radio.

The app asks for no permissions: no internet, no storage. The floating button is drawn by the helper, so it doesn't need "draw over other apps" either.

Fonts (all SIL Open Font License, in `licenses/`): Sora (Aurora), Saira Semi Condensed (Redline), Audiowide and Outfit (Neon Drive), Space Grotesk (Warp), Manrope (Midnight), Fredoka and Nunito (Sakura). Icons are Material icons (Apache 2.0).

GitHub builds the APK on every push to `main` (`.github/workflows/build-apk.yml`) and puts it on the Releases page. **Actions › Clean up old releases** deletes old builds.

CarPlay Restart isn't made by or connected to Apple, Google, Dasaita or ZLink.

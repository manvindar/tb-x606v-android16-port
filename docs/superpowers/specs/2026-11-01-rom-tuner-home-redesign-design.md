# ROM Tuner home redesign (concept C with the graph from concept B)

Approved by the user in the session of 2026-11-01. Mocks: `rom-tuner/docs/mocks/`.

## Home
Search bar, the large live graph (CPU fast and slow cores, GPU, chip temperature), "How should it run?" with six mode cards (Battery, Balanced, Smooth, Performance, Extreme, Custom), the panel of what the chosen mode changes compared with Balanced, a Fine tune grid (two columns of tiles with an icon and a one line state), and a sticky bottom bar. The mode cards carry a drawn round icon and three meters; the active one has an In use chip. Landscape keeps the section list on the left and shows the same overview in the right pane.

## Custom card and bottom bar
Custom is lit whenever the settings are not one of the five modes and shows the profile name when one is in use. Tapping it opens a sheet with the saved profiles (tap to apply, Delete), Save current settings, and Update when the profile in use was changed. The bottom bar reads "Profile X in use" with "No changes" or "Changed since applied" (the app cannot read the profile values, only whether the daemon cleared profile_active), and a Save as profile button. It is shown on the overview only.

## Picture page
Sliders and the two pickers edit pending values. Nothing reaches the display until Apply (one engine restart); Discard drops them. A preview card shows a drawn scene: the left half as the display is now, the right half with the extra change the pending contrast and saturation would add (estimate; gamma and sharpness are not previewed).

## Stale value fix
A value the user changed is kept for 12 seconds and overlaid on every state read, so older published data cannot put the old number back (the picture engine needs about 4 seconds to restart and publish, the app re-read after 2.5). Applies to every setting.

## Tests
JVM tests: `Pending`, `PicMath`, `Search`, `Hist`, `Layouts`, `Prog`, `Profiles`. On the tablet: portrait and landscape, tapping a mode, with the tuner state snapshotted before and restored after.

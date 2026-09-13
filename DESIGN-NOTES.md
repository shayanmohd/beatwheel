# Beatwheel design notes

**Reading this as:** a precision instrument for ensemble musicians (wind and string players, band
directors, early-music players), with a laboratory-strobe language, leaning toward Barlow Semi
Condensed for the note letter and titles plus Atkinson Hyperlegible Next for everything read, on a
slate and neon tangerine palette.

**Dials**

- DESIGN_VARIANCE 3. An instrument is read at a glance, so every Tune layout centres on the disc.
  Asymmetry appears only on wide screens, where the disc takes the left pane.
- MOTION_INTENSITY 2. Only the disc moves unprompted, because its motion is the data. Everything else
  moves in answer to a tap: the drone strip expands in 200 ms, strobe and needle cross-fade in 150 ms.
  Under reduced motion every transition snaps and the disc is drawn still with a Neon rim arc showing
  the cents offset; a Settings switch restores rotation.
- VISUAL_DENSITY 4. A sparse tuner, plain unboxed lists separated by spacing, no card grids.

## Tokens

| Token | Light | Dark | Material role |
|---|---|---|---|
| Stand | #EFF2F4 | #12171C | background, surface |
| Case | #DFE4E8 | #1C232A | surfaceVariant, surfaceContainer, sheets, drone strip, fields |
| Ink | #18212A | #E3E8EC | onSurface, primary, error, zero line |
| Graphite | #4F5B66 | #9AA6B1 | onSurfaceVariant, axis labels, icons |
| Rule | #B9C2CA | #34404A | outline, dividers, disc skeleton, slider track |
| Neon | #C85A26 | #E8844A | the one accent: strobe segments, trace, slider thumb, selected outline |

Neon never carries body text (3.8:1 on light Stand). It marks strokes, segments and numerals of
24sp and up. Strobe gaps are the Stand ground, never Rule. Primary buttons are Ink with a Stand
label. Errors are Ink with an outlined icon, never red.

## Type

- Barlow Semi Condensed SemiBold: note letter, 112sp on Tune and 180sp in Stand.
- Barlow Semi Condensed Medium: screen titles, 22sp.
- Atkinson Hyperlegible Next (variable, weight set through FontVariation): body 16sp 400, labels 14sp
  500, cents and Hz 700 with tabular figures.
- Accidentals are drawn vectors (neither family has sharp or flat glyphs); TalkBack reads "B flat".

## Shape

RadiusSm 4dp chips and fields, RadiusMd 10dp buttons and the drone strip, RadiusLg 20dp sheet tops.
Lists are unboxed.

## The one memorable thing

The disc that stops. Four rings of tangerine segments on the slate ground drift clockwise when
sharp, anticlockwise when flat, and freeze at pitch. No green light, no tick: stillness is the signal.
Everything around it stays quiet: Graphite labels, Ink numerals, no colour except Neon on the disc,
the trace and the one thumb.

## Notes while building

- Nav: bottom NavigationBar under 600dp, NavigationRail from 600dp. Stand is a full-screen route.
- Width class comes from LocalConfiguration.screenWidthDp rather than androidx.window, which keeps one
  dependency out for a single breakpoint.
- The disc is one rigid wheel of four rings with 64, 32, 16 and 8 segments, each ring halving the one
  outside it like the octave rings of a bench strobe, so every edge lines up and the pattern reads as
  an instrument. The whole wheel turns one inner-ring period per beat; the outer rings smear when the
  note is far off. Idle, the same wheel stands still at half-strength Neon.
- Each ring is one cached filled path of annular sectors, built once per size, so a frame draws four
  paths rather than 120 arcs.
- The note letter and "Play a note" scale with the disc, so short landscape panes never collide with
  the inner ring.
- Selected navigation icons, the open level meter and the selected chip outline use Neon; labels stay Ink.
- The idle disc is the same Neon disc at half strength, standing still. Grey segments read as a loading
  skeleton; the skeleton state keeps its Rule outlines so loading and idle stay distinct.
- Neon carries the brand in five places only: live and idle strobe segments, the input meter fill once
  the gate opens, the selected note chip outline, the selected navigation icon, and the drift trace.
  Selected navigation icon is Neon on Case (3.3:1 light, above 5:1 dark), which clears the 3:1 icon rule.
- Empty and permission states use a subject glyph (microphone, chart line), never a segmented circle,
  which looks like a spinner at 32dp.
- Opening the drone settings scrolls the whole strip into view, so the twelve note chips are reachable
  at 1080x1920 as well as 1080x2400.
- Ring segments are filled annular-sector paths built once per size in drawWithCache, not wide arc
  strokes, which tessellate unevenly on the software renderer.

## Review pass notes

- Text field outlines use Graphite when unfocused (6.2:1 light, 7.3:1 dark) instead of Rule (1.6:1), so an
  input's edge clears the 3:1 rule for component boundaries. Dividers, slider tracks and outlined button
  borders keep Rule, where the Ink label or the Neon thumb already identifies the control.
- The note letter and "Play a note" inside the disc are sized against the disc and divided by the system
  font scale: they belong to the graphic, so a 130 percent system font cannot push them over the inner ring.
  The cents, hertz and every other text line still scale.
- The Record drift sheet puts "Stop and save" on its own full-width line, with "Discard" and
  "Keep recording" below, so no label is cut short at large font sizes. Discarding a recording that has
  readings asks first ("Discard recording" / "Keep recording").
- Under reduced motion the live disc stands still but redraws ten times a second, so the Neon rim arc
  follows the reading.
- The screen stays on in Stand mode and for as long as a drift recording runs, on any screen.
- The NavHost is movable content, so rotating across the 600dp breakpoint keeps every screen's saved state; the
  drone settings stay open and the Record drift sheet stays up. The sheet scrolls, so Stop and save is reachable on a
  phone in landscape.
- Opening the drone settings waits two frames before bringing them into view, so the scroll also happens with
  animations off.

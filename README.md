# Animation

Android native custom View demo for a restrained HMI airflow direction animation.

The project currently focuses on a single `AirflowPathView`: three fixed start dots emit three light-blue curved airflow paths. Each path has a fixed endpoint arrow and one moving arrow. The direction can be adjusted by dragging the airflow target, and the preview direction is committed on release.

## Current Experience

- Three start dots stay near each other and are anchored around the center of `AirflowPathView`.
- The drag radius is derived from the View size: `min(width, height) / 2`.
- The center path is the main direction path.
- The left and right paths expand slightly around the center path and stay shorter than the center path.
- Endpoint arrows are drawn over the path tails and extend slightly past the path endpoint.
- Moving arrows travel from the start dots to the endpoint arrows, scale up while moving, and loop.
- Dragging can start from the start-dot area, existing paths, or endpoint arrow areas.
- During dragging, a low-alpha range circle and preview airflow paths are shown.
- On release after an effective drag, the preview direction becomes the active airflow direction.

The visual style is intentionally minimal: light cyan-blue paths, thin strokes, light glow, no smoke, no particles, no grid, no laser effect, and no extra UI controls.

## Project Structure

```text
.
├── app/
│   └── src/main/
│       ├── java/com/zz/animation/
│       │   ├── AirflowPathView.kt   # custom airflow drawing, animation, drag interaction
│       │   └── MainActivity.kt      # Activity setup and color configuration
│       └── res/layout/
│           └── activity_main.xml    # single AirflowPathView in ConstraintLayout
├── tu1.png                          # visual reference only, not used at runtime
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew.bat
```

## Key Implementation Notes

- Rendering is done with Android `Canvas`, `Path`, `PathMeasure`, and `Paint`.
- No third-party animation or graphics library is used.
- `AirflowPathView` avoids object creation in `onDraw()` and reuses paths, paints, arrays, and path measures.
- Path geometry is rebuilt on size changes and during drag updates, not allocated every frame.
- The custom View owns its animation lifecycle through `onAttachedToWindow()`, `onDetachedFromWindow()`, and visibility changes.
- `setPathColor(Int)` remains the public color hook used by `MainActivity`.

## Build

Use PowerShell 7 in this workspace.

```powershell
& 'C:\Users\79979\AppData\Local\Programs\PowerShell\7\pwsh.exe' -NoLogo -Command ".\gradlew.bat :app:assembleDebug"
```

By project preference, do not run these unless explicitly requested:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

## Visual Acceptance Checklist

- Three start dots are visible, close to each other, and not overlapping.
- The start-dot group is positioned from the custom View center.
- The drag range uses half of the shortest View side.
- Dragging to left, right, top, bottom, and diagonals keeps paths and endpoint arrows inside the View bounds.
- The center path remains the main direction path.
- The side paths expand around the center path without crossing.
- Endpoint arrows cover the path tails and point along the terminal tangent.
- Moving arrows remain on their paths, grow while moving, and loop cleanly.
- The preview state is visually weaker than the active airflow state.

## Current Constraints

- This is a single-view animation prototype, not a full HMI screen.
- `tu1.png` is only a reference image and is not imported as a runtime asset.
- Visual screenshot acceptance is handled manually.
- Do not add third-party libraries or unrelated features without an explicit requirement.

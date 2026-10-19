# Contributing to Flexure MAS

Thank you for helping make Flexure MAS better for engineering students.

## Report a problem
Open an **Issue** and include:
- what you did (the beam, supports and loads; attach the `.fmas` file if you can),
- what you expected and what you saw (a screenshot helps),
- your device and browser.

If a result looks wrong, add the hand calculation or textbook formula you compared it with.

## Suggest a feature
Open an Issue that explains who it helps and how it would work. Small, focused ideas are easiest to add.

## Change the code
1. Fork the repository and create a branch.
2. Edit `index.html`. Everything lives in one file with labelled sections: Structural solver, Units, .fmas files, Model panel, Section cut, Canvas drawing, Summary and 3D view.
3. Run the solver checks: `node tests/verify.js`. All checks must pass; add a new check for any solver change.
4. Open the page in a browser and test on a phone-sized window as well as a wide screen, in light and dark mode.
5. If you changed any file the offline app caches, raise the version in `CACHE` at the top of `sw.js`.
6. Open a pull request describing the change and how you tested it.

## Guidelines
- Keep the app dependency-free apart from three.js; no build tools.
- Keep calculations in SI base units (kN, m); convert only for display.
- Keep the `.fmas` format backward compatible; see `FMAS-FORMAT.md`.
- By contributing you agree that your work is released under the MIT License.

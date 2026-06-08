# Design

## Visual Direction

Codex Mobile uses a restrained product UI style inspired by mature chat apps. The interface should feel light, stable, and tool-ready rather than decorative.

## Color

- Background: near-white neutral, used for the app shell.
- Surface: white, used for panels and controls.
- Soft surface: cool gray, used for secondary buttons, chips, and grouped controls.
- Text: near-black neutral.
- Muted text: medium gray with enough contrast for labels and metadata.
- Accent: dark ink for primary actions, soft green-tinted background for active assistant/tool states.

## Typography

Use the Android system sans stack. Keep product UI type fixed rather than fluid. Chat content can be 15, 16, or 17 sp through the existing display setting. Labels and metadata stay smaller but readable.

## Shape And Elevation

Use a small set of radii:

- 10 to 12 dp for inputs and compact controls.
- 14 to 18 dp for panels and grouped settings.
- Full pill only for circular icon buttons, chips, and status badges.

Avoid pairing heavy shadows with borders. Prefer borders and very soft elevation only where hierarchy needs it.

## Layout

Use a 4 dp spacing base. Related controls group tightly at 8 to 12 dp; distinct panels separate at 16 to 24 dp. Phone portrait remains single-column. Tablet and landscape may use wider constrained content but should not stretch text lines too far.

## Motion

Motion communicates state: panel reveal, menu open, button press, loading, and collapse. Most transitions should stay between 150 and 300 ms with ease-out curves. No page-load choreography.

## Components

Buttons, chips, inputs, spinners, settings sections, history rows, chat actions, source cards, code blocks, and image placeholders should share the same color, radius, spacing, and state vocabulary.

# Style notes (scratch)

Notes gathered while rewriting the README. Not documentation, just the checklist I wrote against.

## Words that read as machine-written

Max Planck / arXiv work on lexical overrepresentation flags a stable set: delve, robust, pivotal,
crucial, seamless, leverage, harness, streamline, foster, elevate, underscore, testament, realm,
tapestry, landscape, comprehensive, holistic, nuanced, cutting-edge, transformative. "Delve" alone
rose ~654% in biomedical abstracts 2020-2023. Also filler transitions: moreover, furthermore,
additionally, it's worth noting, in today's landscape, that said.

## Sentence shapes

- **Negative parallelism** ("it's not just X, it's Y", "not merely A but B") is the single loudest
  tell. HN and humanizedcopy both call it out ahead of the em dash.
- **Rule of three** where the third item is a grander restatement of the second.
- **Em dashes used for emphasis** rather than as a genuine aside. More than one or two per page and
  it starts to smell. Commas, parentheses and full stops do the same job.
- Anaphora (repeated sentence openings), "The X? A Y." rhetorical question-and-answer, comma-clipped
  trailing phrases, "From X to Y" false ranges.

## Structural tells

- Every section the same length. Real docs are lumpy: one section is four lines, the next is twenty.
- Over-signposting ("In this section we will...", "Let's break this down"), then a summary paragraph
  that restates what was just said. Fractal summaries at every level.
- Bullet lists where prose belongs, and every bullet opening with a **bolded phrase**.
- Title Case Headings On Everything.
- Tables that are just prose in a grid. A table earns its place when the reader wants to compare
  values across rows.

## Tonal tells

- Unearned enthusiasm and marketing-speak; stakes inflation on a mundane topic.
- Corporate neutrality: nothing is ever bad, no tradeoff is ever named, no opinion is ever held.
- Hedging everything (may, might, could potentially, generally speaking).
- Vague attribution and no concrete numbers.

## What actual developer writing does

Specificity (real numbers, real class names, real commands), opinions stated flat out, blunt
admissions of what doesn't work or isn't tested, dry humour, sentence length all over the place,
occasional fragments, "I" and "you" used naturally, and the odd sentence starting with "But".

## Real mod READMEs, for calibration

- **Carpet** (gnembon) — chatty, first person, jokes ("Cause all carpets are made of fabric?"),
  bullets that link straight to the setting they describe. No polish, very readable.
- **ModernFix** (embeddedt) — two sentences of description, then links. Credits where credit is due.
  Under 30 lines total.
- **Controlling** (jaredlll08) — title plus one line. That's the whole README.
- **Chunky**, **Lithium**, **Sodium** — short intro, downloads, support, hardware/compat caveats.
  Sodium is the most corporate of the bunch and it's still mostly links.
- **spark** (lucko) — the one that does go long, because it's explaining three separate tools.

Common shape: what it is in one or two sentences, how to install, the config/settings that matter,
the caveats, links out. Detail lives on a wiki, not in the README. Mod READMEs are far shorter and
blunter than corporate docs, and nobody writes an executive summary.

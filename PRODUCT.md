# Product

## Register

product

## Users
A single owner (personal use) who keeps private photos, videos, notes and PDFs on their own Android
phone. They open Cripta in short, focused sessions, often one-handed and in public or semi-public
places, to import something quickly, find a file by folder/tag, or view it and leave. The job: keep
media out of the system gallery and away from casual snoopers, with zero friction for the owner.

## Product Purpose
Cripta is an encrypted on-device vault (Tink StreamingAead + SQLCipher, Keystore-backed keys,
biometric unlock, auto-lock, crypto-shredding delete). It also imports from gallery/share sheet and
downloads videos from links (yt-dlp). Success = the owner trusts it, finds any file in seconds, and
never sees plaintext leak outside the app.

## Brand Personality
Calm, secure, discreet. A premium safe, not a gadget. Voice is plain Italian, short and reassuring.
Emotional goal: quiet confidence; the app should feel solid and unhurried, never flashy.

## Anti-references
- "Hacker" aesthetics: neon green on black, terminal fonts, glitch effects, matrix rain, padlock
  clip-art as decoration.
- Motion that performs instead of informs: bounces, elastic springs, confetti, long choreography.

## Design Principles
1. Security is felt, not shown: trust comes from steadiness and clear state, not from security
   iconography everywhere.
2. Every motion explains a state change (lock/unlock, selection, progress, navigation depth);
   nothing animates for decoration.
3. Fast in, fast out: short sessions, so no load choreography and no blocking transitions.
4. Discretion by default: nothing surprising or loud on screen that could draw attention in public.
5. Clear feedback for every action, especially destructive and long-running ones.

## Accessibility & Inclusion
- Target WCAG 2.2 AA contrast in both light and dark themes.
- Respect system "Remove animations" (ANIMATOR_DURATION_SCALE = 0): motion collapses to instant or
  crossfade.
- 48dp minimum touch targets; TalkBack labels on icon-only controls; support font scaling.

# DEJA calls and messaging: platform requirements

The current app intentionally opens Android's dialler and message composer so the person can review and confirm each action. It does not silently answer calls or send messages.

- **Call answering:** Android requires a user-approved call-screening/default-phone role for call handling. A call-screening service can identify, allow, silence, or reject calls within platform rules; it is not a general-purpose permission to secretly answer and record calls. Any future implementation must show caller context and require explicit user setup.
- **SMS auto-replies:** A dependable SMS receiver/sender workflow requires DEJA to become the user's selected default SMS app and implement the required SMS app components, permissions, notification behavior, and privacy disclosures. The existing preference toggle is not an active sender.
- **WhatsApp:** The current supported integration is a user-reviewed share handoff. Do not promise unrestricted background sending or bypass WhatsApp's own controls.

These features must be implemented as separate, opt-in Android components and tested on the target Samsung device. They are not represented as completed in this release.

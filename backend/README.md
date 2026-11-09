# DEJA OpenAI gateway

This Node.js service keeps the OpenAI API key on the server, not in the Android app. It exposes `POST /v1/chat` and `GET /health`.

## Deployment preparation (Render)

A Render Blueprint is included at the project root as `render.yaml`. It configures the backend folder as the service root, Node.js 20, `npm install`, `npm start`, and `/health` as the health check.

**Important security gate:** this starter gateway currently has IP-based rate limiting but does not yet have real user authentication. Do not publish it for unrestricted public use or connect it to a released app until authenticated access, per-user quotas, and monitoring are implemented. A token embedded in the APK is not a secure substitute because APK contents can be inspected.

When the authentication layer is ready, an adult account owner should:

1. Create/connect the Render service from the project's Git repository and select the `render.yaml` Blueprint.
2. In the service's Environment page, add `OPENAI_API_KEY` using a secret created for the DEJA backend. Never put the key in Git, this ZIP, Android code, or chat.
3. Keep `OPENAI_MODEL` as `gpt-6-astra` unless the project owner intentionally selects another available model.
4. Deploy and confirm that `https://<service>.onrender.com/health` returns `{ "ok": true, ... }`.
5. Only after gateway authentication is implemented, build Android with the service's HTTPS base URL:

   `gradle -PDEJA_API_BASE_URL=https://<service>.onrender.com :app:assembleDebug`

The API key is stored only in Render's environment variables. Render documents this flow in its Node/Express deployment and environment-variable guides.

## Local development

1. Install Node.js 20 or newer.
2. Copy `.env.example` to `.env` and set `OPENAI_API_KEY` privately. Do not commit `.env`.
3. Run `npm install` and `npm start`.
4. Check `http://localhost:8080/health`.

Do not use a public `http://` endpoint. Use HTTPS for deployment.

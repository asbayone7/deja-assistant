# DEJA OpenAI gateway

The Node.js gateway keeps the OpenAI API key on the server and requires signed bearer-token authentication before any AI request. The Android app signs in to `/v1/auth/login`; the gateway issues a 12-hour HS256 JWT. `/v1/chat` rejects requests without a valid token.

## Render deployment

1. Connect the private GitHub repository to Render and deploy the Blueprint at the project root (`render.yaml`).
2. In the Render service Environment page, set `OPENAI_API_KEY` to the private OpenAI API key. Never place it in Android, Git, a ZIP, or chat.
3. Set `DEJA_GATEWAY_USERNAME` and `DEJA_GATEWAY_PASSWORD` to a unique DEJA gateway login. Use a long password not reused elsewhere.
4. Render generates `DEJA_JWT_SECRET` from `generateValue: true`. Confirm it is at least 32 random bytes; do not share it.
5. Keep `OPENAI_MODEL` set to a model enabled for the OpenAI account.
6. Deploy. Check `https://YOUR-SERVICE.onrender.com/health`; it should return `ok: true` and `auth: required`.
7. Build Android with the HTTPS service base URL: `gradle -PDEJA_API_BASE_URL=https://YOUR-SERVICE.onrender.com :app:assembleDebug`. No API key or JWT secret is passed to Gradle.

## Authentication model and limitations

- Login credentials are configured only as Render environment secrets. This is a single gateway account shared by installations, not per-person account management. Rotate credentials in Render if compromised.
- The app stores the short-lived access token encrypted with an Android Keystore AES-GCM key. The token is never hard-coded into the APK.
- Login is rate-limited (8 attempts per 15 minutes per source IP); chat is rate-limited (20 requests per minute per source IP). Add persistent per-account usage quotas and monitoring before broad public distribution.
- Use HTTPS only. Do not expose a local or plain-HTTP gateway to the internet.
- A successful `/health` check proves only that the service is running; it does not prove OpenAI credentials/model access. Complete a signed-in test chat to verify the live brain.

## Local development

Copy `.env.example` to `.env`, fill values locally, run `npm install` and `npm start`. Keep `.env` out of Git. Test health at `http://localhost:8080/health`; test login at `/v1/auth/login` and then send a bearer token to `/v1/chat`.

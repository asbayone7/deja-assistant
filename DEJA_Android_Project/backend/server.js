import express from 'express';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import crypto from 'node:crypto';

const app = express();
app.disable('x-powered-by');
app.use(helmet());
app.use(express.json({ limit: '12kb' }));
const loginLimiter = rateLimit({ windowMs: 15 * 60_000, limit: 8, standardHeaders: 'draft-7', legacyHeaders: false });
const apiLimiter = rateLimit({ windowMs: 60_000, limit: 20, standardHeaders: 'draft-7', legacyHeaders: false });
const apiKey = process.env.OPENAI_API_KEY;
const model = process.env.OPENAI_MODEL || 'gpt-6-astra';
const gatewayUser = process.env.DEJA_GATEWAY_USERNAME;
const gatewayPassword = process.env.DEJA_GATEWAY_PASSWORD;
const jwtSecret = process.env.DEJA_JWT_SECRET;
const tokenTtlSeconds = 12 * 60 * 60;

function safeEqual(a, b) {
  const aa = Buffer.from(String(a));
  const bb = Buffer.from(String(b));
  return aa.length === bb.length && crypto.timingSafeEqual(aa, bb);
}
function signToken(payload) {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const body = Buffer.from(JSON.stringify(payload)).toString('base64url');
  const input = `${header}.${body}`;
  const signature = crypto.createHmac('sha256', jwtSecret).update(input).digest('base64url');
  return `${input}.${signature}`;
}
function verifyToken(token) {
  if (!jwtSecret || typeof token !== 'string') return null;
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  const input = `${parts[0]}.${parts[1]}`;
  const expected = crypto.createHmac('sha256', jwtSecret).update(input).digest();
  let actual;
  try { actual = Buffer.from(parts[2], 'base64url'); } catch { return null; }
  if (actual.length !== expected.length || !crypto.timingSafeEqual(actual, expected)) return null;
  try {
    const payload = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
    if (payload.iss !== 'deja-gateway' || payload.aud !== 'deja-android' || payload.exp <= Math.floor(Date.now() / 1000)) return null;
    return payload;
  } catch { return null; }
}
function requireAuth(req, res, next) {
  const header = req.get('authorization') || '';
  const match = header.match(/^Bearer ([A-Za-z0-9._-]+)$/);
  const claims = match ? verifyToken(match[1]) : null;
  if (!claims) return res.status(401).json({ error: 'Authentication required. Sign in again.' });
  req.auth = claims;
  next();
}

app.get('/health', (_req, res) => res.json({ ok: true, service: 'DEJA AI gateway', auth: 'required' }));

app.post('/v1/auth/login', loginLimiter, (req, res) => {
  if (!gatewayUser || !gatewayPassword || !jwtSecret || jwtSecret.length < 32) {
    return res.status(503).json({ error: 'Gateway authentication is not configured.' });
  }
  const username = typeof req.body?.username === 'string' ? req.body.username : '';
  const password = typeof req.body?.password === 'string' ? req.body.password : '';
  if (username.length > 120 || password.length > 256 || !safeEqual(username, gatewayUser) || !safeEqual(password, gatewayPassword)) {
    return res.status(401).json({ error: 'The username or password is incorrect.' });
  }
  const now = Math.floor(Date.now() / 1000);
  const token = signToken({ iss: 'deja-gateway', aud: 'deja-android', sub: 'deja-user', iat: now, exp: now + tokenTtlSeconds });
  res.json({ access_token: token, token_type: 'Bearer', expires_in: tokenTtlSeconds });
});

app.post('/v1/chat', apiLimiter, requireAuth, async (req, res) => {
  if (!apiKey) return res.status(503).json({ error: 'AI service is not configured.' });
  const message = typeof req.body?.message === 'string' ? req.body.message.trim() : '';
  const language = typeof req.body?.language === 'string' ? req.body.language : 'en-US';
  if (!message || message.length > 4000) return res.status(400).json({ error: 'Message must contain 1–4,000 characters.' });
  const languageName = language === 'pt-BR' ? 'Brazilian Portuguese' : language === 'ng-NA' ? 'Oshiwambo (use only if confident; otherwise say so)' : 'English';
  try {
    const upstream = await fetch('https://api.openai.com/v1/responses', {
      method: 'POST',
      headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        model,
        instructions: `You are DEJA, a calm, intelligent, professional personal assistant. The owner is Asbay Garcia. Begin activated greetings with “Yes, Boss. I'm listening.” Respond in ${languageName} when possible. If you are not confident in Oshiwambo, be transparent and answer in English rather than inventing translations. Never claim to have performed device actions; this endpoint can only converse. Keep answers clear and accessible as on-screen text.`,
        input: message,
        max_output_tokens: 700
      }),
      signal: AbortSignal.timeout(40_000)
    });
    const data = await upstream.json();
    if (!upstream.ok) {
      console.error('OpenAI request failed:', upstream.status, data?.error?.type || 'upstream_error');
      return res.status(502).json({ error: 'The AI provider could not complete the request.' });
    }
    let reply = typeof data.output_text === 'string' ? data.output_text : '';
    if (!reply && Array.isArray(data.output)) reply = data.output.flatMap(item => item.content || []).filter(part => part.type === 'output_text').map(part => part.text || '').join('\n');
    if (!reply.trim()) return res.status(502).json({ error: 'The AI provider returned no text.' });
    res.json({ reply: reply.trim() });
  } catch (error) {
    console.error('Gateway error:', error?.name || 'unknown');
    res.status(502).json({ error: 'Unable to reach the AI provider.' });
  }
});

const port = Number(process.env.PORT || 8080);
app.listen(port, () => console.log(`DEJA AI gateway listening on ${port}`));

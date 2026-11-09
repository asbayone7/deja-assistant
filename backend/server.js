import express from 'express';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';

const app = express();
app.disable('x-powered-by');
app.use(helmet());
app.use(express.json({ limit: '12kb' }));
app.use('/v1/', rateLimit({ windowMs: 60_000, limit: 20, standardHeaders: 'draft-7', legacyHeaders: false }));

const apiKey = process.env.OPENAI_API_KEY;
const model = process.env.OPENAI_MODEL || 'gpt-6-astra';

app.get('/health', (_req, res) => res.json({ ok: true, service: 'DEJA AI gateway' }));

app.post('/v1/chat', async (req, res) => {
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
    if (!reply && Array.isArray(data.output)) {
      reply = data.output.flatMap(item => item.content || []).filter(part => part.type === 'output_text').map(part => part.text || '').join('\n');
    }
    if (!reply.trim()) return res.status(502).json({ error: 'The AI provider returned no text.' });
    res.json({ reply: reply.trim() });
  } catch (error) {
    console.error('Gateway error:', error?.name || 'unknown');
    res.status(502).json({ error: 'Unable to reach the AI provider.' });
  }
});

const port = Number(process.env.PORT || 8080);
app.listen(port, () => console.log(`DEJA AI gateway listening on ${port}`));

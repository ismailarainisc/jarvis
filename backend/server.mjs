import http from 'node:http';
import {createHash} from 'node:crypto';
import {pathToFileURL} from 'node:url';

const digest = value => createHash('sha256').update(value).digest('hex');
export function createHandler({apiKey, sessions = {}, fetchImpl = fetch, now = Date.now, maxPerMinute = 3} = {}) {
  // Operator-provisioned per-user, expiring and revocable access tokens; only hashes live here.
  const windows = new Map();
  return async (req, res) => {
    res.setHeader('Cache-Control','no-store');
    const reply = (code, value) => { if (!res.destroyed) { res.writeHead(code, {'Content-Type':'application/json'});res.end(JSON.stringify(value)); } };
    if (req.method === 'GET' && req.url === '/health') return reply(200,{status:'ok'});
    if (req.method !== 'POST' || req.url !== '/api/live/sessions') return reply(404,{error:'Not found'});
    const auth = req.headers.authorization || '';
    const session = auth.startsWith('Bearer ') && auth.length < 1024 ? sessions[digest(auth.slice(7))] : null;
    if (!session || typeof session.user !== 'string' || !(session.expiresAt > now())) return reply(401,{error:'Sign in with a valid, unexpired backend session.'});
    if (!apiKey) return reply(503,{error:'OpenAI credentials are not configured on the backend.'});
    const id=digest(session.user); let bucket=windows.get(id);
    if (!bucket || now()-bucket.start>=60000) { bucket={start:now(),count:0};windows.set(id,bucket); }
    if (windows.size>10000) for(const [k,v] of windows) if(now()-v.start>60000) windows.delete(k);
    if (++bucket.count>maxPerMinute) {res.setHeader('Retry-After','60');return reply(429,{error:'Session creation limit reached. Wait before starting again.'});}
    if (!(req.headers['content-type']||'').startsWith('application/json')) return reply(415,{error:'Expected application/json'});
    const abort=new AbortController(); const timer=setTimeout(()=>abort.abort(),20000);
    const disconnected=()=>{if(!res.writableEnded)abort.abort()};res.on('close',disconnected);
    try {
      let raw='';for await(const chunk of req){raw+=chunk.toString();if(Buffer.byteLength(raw)>65536){reply(413,{error:'Offer exceeds 64 KiB.'});req.resume();return;}}
      let body;try{body=JSON.parse(raw)}catch{return reply(400,{error:'Expected a JSON SDP offer.'});}
      if(typeof body.sdp!=='string'||!body.sdp.startsWith('v=0')||!body.sdp.includes('m=audio')||body.sdp.length>65536) return reply(400,{error:'A valid audio SDP offer is required.'});
      const upstream=await fetchImpl('https://api.openai.com/v1/live/sessions',{
        method:'POST',redirect:'error',signal:abort.signal,
        headers:{'Authorization':`Bearer ${apiKey}`,'Content-Type':'application/json'},
        body:JSON.stringify({session:{model:'gpt-live-1',instructions:'You are JARVIS. Be concise and helpful.'},transport:{type:'webrtc',sdp:body.sdp}})
      });
      if(!upstream.ok){const status=upstream.status;return reply(status===401||status===403?502:status===429?429:502,{error:status===401||status===403?'Provider rejected server credentials or GPT-Live access.':status===429?'Provider quota or rate limit reached.':status===404?'GPT-Live endpoint or model is unavailable to this account.':'GPT-Live session creation failed.',providerStatus:status});}
      const result=await upstream.json();
      if(typeof result.session?.id!=='string'||result.transport?.type!=='webrtc'||typeof result.transport?.sdp!=='string'||!result.transport.sdp.startsWith('v=0')) return reply(502,{error:'Provider returned an invalid session or SDP answer.'});
      return reply(201,{sessionId:result.session.id,sdp:result.transport.sdp});
    } catch { return reply(abort.signal.aborted?504:502,{error:abort.signal.aborted?'Session creation timed out or was cancelled.':'Unable to establish a secure provider connection.'}); }
    finally {clearTimeout(timer);res.off('close',disconnected);}
  };
}
export function startServer(env=process.env) {
  let sessions;try{sessions=JSON.parse(env.MOBILE_SESSIONS_JSON||'{}')}catch{throw Error('MOBILE_SESSIONS_JSON must be valid JSON');}
  const server=http.createServer(createHandler({apiKey:env.OPENAI_API_KEY,sessions}));
  server.requestTimeout=25000;server.headersTimeout=10000;
  server.listen(Number(env.PORT||3000),env.HOST||'127.0.0.1');return server;
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href)startServer();

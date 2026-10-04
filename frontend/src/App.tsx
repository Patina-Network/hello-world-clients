import { request } from './api';
import { useGreetings } from './lib/hooks/useGreetings';

export default function App() {
  const { echo, setEcho, replies, loaded, inboxError, busy, error, loading, notice, setNotice, submit, loadGreetings, displayedRecipient } = useGreetings();
  const language = import.meta.env.VITE_CLIENT_LANGUAGE || 'local';
  return <main>
    <header><p className="eyebrow">PATINA NETWORK · {language.toUpperCase()}</p>
      <h1>A little hello goes a long way.</h1>
      <p>Say hello, send a greeting, or see what someone has received.</p>
    </header>
    <div className="cards">
      <section><h2>Say hello</h2>
        <form onSubmit={event => void submit(event, 'hello', async data => {
          const result = await request<{ response: string }>('/api/echo?name=' + encodeURIComponent(String(data.get('name'))));
          setEcho(result.response);
        })}>
          <fieldset disabled={busy.hello}><label>Your name<input name="name" required maxLength={256} placeholder="Ada" /></label>
          <button>Say hello</button></fieldset>
        </form>
        <div role="status">{busy.hello ? 'Loading hello…' : ''}</div>
        {error.hello && <p role="alert" className="error">{error.hello}</p>}
        {echo && <p className="response">{echo}</p>}
      </section>
      <section><h2>Send a greeting</h2>
        <form onSubmit={event => void submit(event, 'send', async data => {
          await request('/api/greetings', Object.fromEntries(data.entries()));
          setNotice('Greeting sent.');
        })}>
          <fieldset disabled={busy.send}><label>From<input name="senderName" required maxLength={256} placeholder="Ada" /></label>
          <label>To<input name="recipientName" required maxLength={256} placeholder="Lin" /></label>
          <label>Message<textarea name="greeting" required maxLength={4096} placeholder="Hope you’re having a good day." /></label>
          <button>Send greeting</button></fieldset>
        </form>
        <div role="status">{busy.send ? 'Sending greeting…' : notice}</div>
        {error.send && <p role="alert" className="error">{error.send}</p>}
      </section>
      <section className="inbox" aria-busy={loading}><h2>Greetings</h2>
        <p>Enter a recipient name and click Load greetings, or leave it blank to see everyone.</p>
        {inboxError && <p role="alert" className="error">{inboxError}</p>}
        <form onSubmit={loadGreetings}>
          <label>Recipient<input name="recipientName" maxLength={256} placeholder="Leave blank to see everyone" /></label>
          <button>Load greetings</button>
        </form>
        <div role="status">{loading ? 'Loading greetings…' : ''}</div>
        {loaded && <p>Showing greetings for {displayedRecipient || 'everyone'}.</p>}
        {loaded && replies.length === 0 && <p>No greetings yet.</p>}
        <ul>{replies.map(reply => <li key={reply.id}>
          <p>{reply.message}</p><small>From {reply.senderName} to {reply.recipientName}
            {reply.receivedAt && <> · <time dateTime={reply.receivedAt}>{new Date(reply.receivedAt).toLocaleString()}</time></>}
          </small>
        </li>)}</ul>
      </section>
    </div>
  </main>;
}

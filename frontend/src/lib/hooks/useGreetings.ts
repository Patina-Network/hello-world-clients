import { useEffect, useRef, useState, type FormEvent } from 'react';
import { HttpError, request, type Greeting } from '../../api';

type EntryForm = 'hello' | 'send';

export function useGreetings() {
  const [echo, setEcho] = useState('');
  const [replies, setReplies] = useState<Greeting[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [inboxError, setInboxError] = useState('');
  const [query, setQuery] = useState<{ recipient: string; revision: number } | null>(null);
  const [busy, setBusy] = useState({ hello: false, send: false });
  const submitting = useRef({ hello: false, send: false });
  const [error, setError] = useState({ hello: '', send: '' });
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState('');
  const [displayedRecipient, setDisplayedRecipient] = useState('');

  useEffect(() => {
    if (!query) return;
    const { recipient } = query;
    const controller = new AbortController();
    let active = true;
    setLoading(true); setInboxError('');
    const path = '/api/greetings' + (recipient ? '?recipientName=' + encodeURIComponent(recipient) : '');
    async function refresh() {
      try {
        let result: { replies: Greeting[] };
        try { result = await request<{ replies: Greeting[] }>(path, undefined, controller.signal); }
        catch (err) {
          if (recipient && err instanceof HttpError && err.status === 404) result = { replies: [] };
          else throw err;
        }
        if (active) { setReplies(result.replies); setDisplayedRecipient(recipient); setLoaded(true); setInboxError(''); }
      } catch (err) {
        if (active) setInboxError(err instanceof Error ? err.message : 'Unable to load greetings.');
      } finally {
        if (active) setLoading(false);
      }
    }
    void refresh();
    return () => {
      active = false;
      controller.abort();
    };
  }, [query]);

  async function submit(event: FormEvent<HTMLFormElement>, kind: EntryForm, action: (data: FormData) => Promise<void>) {
    event.preventDefault();
    if (submitting.current[kind]) return;
    const form = event.currentTarget;
    const data = new FormData(form);
    for (const [key, value] of data.entries()) {
      if (typeof value === 'string') data.set(key, value.trim());
    }
    if ([...data.values()].some(value => value === '')) {
      setError(current => ({ ...current, [kind]: 'Please fill out every field.' }));
      return;
    }
    submitting.current[kind] = true;
    setBusy(current => ({ ...current, [kind]: true }));
    setError(current => ({ ...current, [kind]: '' }));
    if (kind === 'send') setNotice('');
    else setEcho('');
    try {
      await action(data);
      form.reset();
    } catch (err) {
      setError(current => ({ ...current, [kind]: err instanceof Error ? err.message : 'Request failed. Please try again.' }));
    } finally {
      submitting.current[kind] = false;
      setBusy(current => ({ ...current, [kind]: false }));
    }
  }
  function loadGreetings(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const recipient = String(data.get('recipientName')).trim();
    setQuery(current => ({ recipient, revision: (current?.revision ?? 0) + 1 }));
  }

  return { echo, setEcho, replies, loaded, inboxError, busy, error, loading, notice, setNotice, submit, loadGreetings, displayedRecipient };
}

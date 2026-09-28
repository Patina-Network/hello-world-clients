import { act, fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, it, vi } from 'vitest';
import App from './App';

afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers(); });
function mockResponse(body: unknown, status = 200) {
  const fetch = vi.fn().mockImplementation((path: string) => {
    const data = status === 200 && path.startsWith('/api/greetings') && !(body && typeof body === 'object' && 'replies' in body) ? { replies: [] } : body;
    return Promise.resolve(new Response(JSON.stringify(data), { status }));
  });
  vi.stubGlobal('fetch', fetch);
  return fetch;
}
it('echoes a name through HTTP', async () => {
  const fetch = mockResponse({ response: 'hello Ada' });
  render(<App />); const user = userEvent.setup();
  await user.type(screen.getByLabelText('Your name'), 'Ada');
  await user.click(screen.getByRole('button', { name: 'Say hello' }));
  expect(await screen.findByText('hello Ada')).toBeInTheDocument();
  expect(fetch).toHaveBeenCalledWith('/api/echo?name=Ada', expect.objectContaining({ method: 'GET' }));
});
it('sends the greeting fields', async () => {
  const fetch = mockResponse({}); render(<App />); const user = userEvent.setup();
  await user.type(screen.getByLabelText('From'), 'Ada');
  await user.type(screen.getByLabelText('To'), 'Lin');
  await user.type(screen.getByLabelText('Message'), 'hi');
  await user.click(screen.getByRole('button', { name: 'Send greeting' }));
  expect(await screen.findByText('Greeting sent.')).toBeInTheDocument();
  expect(fetch).toHaveBeenCalledTimes(1);
  expect(fetch).toHaveBeenCalledWith('/api/greetings', expect.objectContaining({ method: 'POST', body: JSON.stringify({ senderName: 'Ada', recipientName: 'Lin', greeting: 'hi' }) }));
});
it('loads and displays greetings', async () => {
  const fetch = mockResponse({ replies: [{ id: 1, message: 'Ada says hi', senderName: 'Ada', recipientName: 'Lin', receivedAt: null }] });
  render(<App />); const user = userEvent.setup();
  await user.type(screen.getByLabelText('Recipient'), 'Lin');
  await user.click(screen.getByRole('button', { name: 'Load greetings' }));
  expect(await screen.findByText('Ada says hi')).toBeInTheDocument();
  expect(fetch).toHaveBeenCalledWith('/api/greetings?recipientName=Lin', expect.anything());
});
it('shows an upstream failure and allows retry', async () => {
  mockResponse({ error: 'service unavailable' }, 503); render(<App />);
  await userEvent.click(screen.getByRole('button', { name: 'Load greetings' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('service unavailable');
  expect(screen.getByRole('button', { name: 'Load greetings' })).toBeEnabled();
});

it('loads only on request and does not refresh on a timer or window focus', async () => {
  vi.useFakeTimers();
  const fetch = mockResponse({ replies: [] });
  render(<App />);
  await act(async () => { await vi.advanceTimersByTimeAsync(6000); });
  fireEvent.focus(window);
  expect(fetch).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('Recipient'), { target: { value: 'Lin' } });
  expect(fetch).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Load greetings' }));
  await act(async () => {});
  expect(fetch).toHaveBeenCalledWith('/api/greetings?recipientName=Lin', expect.anything());
  await act(async () => { await vi.advanceTimersByTimeAsync(6000); });
  fireEvent.focus(window);
  expect(fetch).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole('button', { name: 'Load greetings' }));
  await act(async () => {});
  expect(fetch).toHaveBeenCalledTimes(2);
});

it('ignores stale responses after changing the recipient filter', async () => {
  let finishOld: (response: Response) => void = () => {};
  const fetch = vi.fn().mockImplementation((path: string) => {
    if (path === '/api/greetings') return new Promise<Response>(resolve => { finishOld = resolve; });
    return Promise.resolve(new Response(JSON.stringify({ replies: [{ id: 3, message: 'Lin only', senderName: 'Ada', recipientName: 'Lin', receivedAt: null }] })));
  });
  vi.stubGlobal('fetch', fetch);
  render(<App />);
  fireEvent.click(screen.getByRole('button', { name: 'Load greetings' }));
  await act(async () => {});
  fireEvent.change(screen.getByLabelText('Recipient'), { target: { value: 'Lin' } });
  fireEvent.click(screen.getByRole('button', { name: 'Load greetings' }));
  expect(await screen.findByText('Lin only')).toBeInTheDocument();
  await act(async () => { finishOld(new Response(JSON.stringify({ replies: [] }))); });
  expect(screen.getByText('Lin only')).toBeInTheDocument();
  expect(fetch.mock.calls[0][1].signal.aborted).toBe(true);
});

it('retries failures only when requested and clears the error after recovery', async () => {
  vi.useFakeTimers();
  const fetch = vi.fn()
    .mockImplementationOnce(() => Promise.resolve(new Response(JSON.stringify({ error: 'service unavailable' }), { status: 503 })))
    .mockImplementation(() => Promise.resolve(new Response(JSON.stringify({ replies: [] }))));
  vi.stubGlobal('fetch', fetch);
  render(<App />);
  fireEvent.click(screen.getByRole('button', { name: 'Load greetings' }));
  await act(async () => {});
  expect(screen.getByRole('alert')).toHaveTextContent('service unavailable');
  await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
  expect(fetch).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole('button', { name: 'Load greetings' }));
  await act(async () => {});
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  expect(screen.getByText('No greetings yet.')).toBeInTheDocument();
});

it('locks only the submitted form and clears its fields after success', async () => {
  let finish: (response: Response) => void = () => {};
  const fetch = vi.fn(() => new Promise<Response>(resolve => { finish = resolve; }));
  vi.stubGlobal('fetch', fetch);
  render(<App />);
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('From'), 'Ada');
  await user.type(screen.getByLabelText('To'), 'Lin');
  await user.type(screen.getByLabelText('Message'), 'Hello');
  await user.click(screen.getByRole('button', { name: 'Send greeting' }));
  expect(screen.getByLabelText('Message')).toBeDisabled();
  expect(screen.getByLabelText('Message')).toHaveValue('Hello');
  expect(screen.getByRole('button', { name: 'Say hello' })).toBeEnabled();
  expect(screen.getByRole('button', { name: 'Load greetings' })).toBeEnabled();
  await user.click(screen.getByRole('button', { name: 'Send greeting' }));
  expect(fetch).toHaveBeenCalledTimes(1);
  await act(async () => { finish(new Response('{}')); });
  for (const label of ['From', 'To', 'Message']) {
    expect(screen.getByLabelText(label)).toHaveValue('');
    expect(screen.getByLabelText(label)).toBeEnabled();
  }
});

it('preserves a failed greeting for retry', async () => {
  mockResponse({ error: 'Try again' }, 503);
  render(<App />);
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('From'), 'Ada');
  await user.type(screen.getByLabelText('To'), 'Lin');
  await user.type(screen.getByLabelText('Message'), 'Hello');
  await user.click(screen.getByRole('button', { name: 'Send greeting' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Try again');
  expect(screen.getByLabelText('From')).toHaveValue('Ada');
  expect(screen.getByLabelText('To')).toHaveValue('Lin');
  expect(screen.getByLabelText('Message')).toHaveValue('Hello');
  expect(screen.getByRole('button', { name: 'Send greeting' })).toBeEnabled();
});

it('keeps existing greetings visible while a manual refresh is pending', async () => {
  let finish: (response: Response) => void = () => {};
  const fetch = vi.fn()
    .mockResolvedValueOnce(new Response(JSON.stringify({ replies: [{ id: 1, message: 'Existing greeting', senderName: 'Ada', recipientName: 'Lin', receivedAt: null }] })))
    .mockImplementationOnce(() => new Promise<Response>(resolve => { finish = resolve; }));
  vi.stubGlobal('fetch', fetch);
  render(<App />);
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Recipient'), 'Lin');
  await user.click(screen.getByRole('button', { name: 'Load greetings' }));
  expect(await screen.findByText('Existing greeting')).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: 'Load greetings' }));
  expect(screen.getByText('Loading greetings…')).toBeInTheDocument();
  expect(screen.getByText('Existing greeting')).toBeInTheDocument();
  expect(screen.getByLabelText('Recipient')).toHaveValue('Lin');
  await act(async () => { finish(new Response(JSON.stringify({ replies: [] }))); });
  expect(screen.queryByText('Loading greetings…')).not.toBeInTheDocument();
  expect(screen.queryByText('Existing greeting')).not.toBeInTheDocument();
  expect(screen.getByText('No greetings yet.')).toBeInTheDocument();
});

export async function requestJson(url, { method = 'GET', body, ...options } = {}) {
  const token = document.querySelector('meta[name="csrf-token"]')?.content;
  const header = document.querySelector('meta[name="csrf-header"]')?.content;
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');
  if (body !== undefined) headers.set('Content-Type', 'application/json');
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method.toUpperCase())) {
    if (!token || !header) throw new Error('Atualize a página para renovar sua sessão.');
    headers.set(header, token);
  }
  const response = await fetch(url, {
    ...options,
    method,
    headers,
    credentials: 'same-origin',
    redirect: 'error',
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (response.status === 401)
    throw new Error('Sua sessão expirou. Entre novamente para continuar.');
  const data = response.status === 204 ? null : await response.json();
  if (!response.ok) {
    const reference = data?.correlationId ? ` Referência: ${data.correlationId}` : '';
    throw new Error(`${data?.detail ?? 'Não foi possível concluir a operação.'}${reference}`);
  }
  return data;
}

const TOKEN_KEY = 'fiely.accessToken';

function getToken(): string | null {
  return (
    window.localStorage.getItem(TOKEN_KEY) ??
    window.sessionStorage.getItem(TOKEN_KEY)
  );
}

export function clearTokens() {
  for (const s of [window.localStorage, window.sessionStorage]) {
    s.removeItem('fiely.accessToken');
    s.removeItem('fiely.refreshToken');
  }
}

export async function apiFetch(
  path: string,
  init?: RequestInit,
): Promise<Response> {
  const token = getToken();
  const headers = new Headers(init?.headers);
  if (token) headers.set('Authorization', `Bearer ${token}`);
  const res = await fetch(path, { ...init, headers });
  if (res.status === 401 && !path.includes('/api/auth/')) {
    clearTokens();
    window.location.href = '/login';
  }
  return res;
}

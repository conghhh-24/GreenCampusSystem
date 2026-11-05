let csrfToken = ''
export const clearSession = () => { csrfToken = '' }
export async function api(url, options = {}) {
  const method = (options.method || 'GET').toUpperCase()
  const headers = new Headers(options.headers)
  headers.set('X-Requested-With', 'XMLHttpRequest')
  if (!(options.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method) && csrfToken) headers.set('X-CSRF-Token', csrfToken)
  const response = await fetch(url, { ...options, method, headers, credentials: 'same-origin' })
  const data = await response.json().catch(() => ({}))
  if (!response.ok) {
    if (response.status === 401 && !url.endsWith('/auth/login')) {
      clearSession()
      window.dispatchEvent(new Event('session-expired'))
    }
    const error = new Error(data.message || data.detail || data.error || '请求失败')
    error.status = response.status
    throw error
  }
  if (data.csrfToken) csrfToken = data.csrfToken
  return data
}

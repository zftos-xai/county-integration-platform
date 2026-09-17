import { httpRequest } from '@/utils/request'

/** Returns whether the backend health endpoint responds successfully within the shared timeout. */
export async function isBackendAvailable(signal?: AbortSignal): Promise<boolean> {
  const response = await httpRequest('/actuator/health', {
    headers: { Accept: 'application/json' },
    signal,
  })
  return response.ok
}

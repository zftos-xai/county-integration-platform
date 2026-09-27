import { onBeforeUnmount, shallowRef } from 'vue'

/**
 * Describes the copy and visual emphasis displayed by a shared confirmation dialog.
 */
export interface ConfirmationRequest {
  /** Short question or action name shown in the dialog heading. */
  title: string
  /** Explains the effect of confirming, including any irreversible consequence. */
  message: string
  /** Label for the action that resolves the request as confirmed. */
  confirmLabel?: string
  /** Uses the danger treatment for destructive or consequential actions. */
  danger?: boolean
}

/**
 * Provides one accessible, shared confirmation dialog for a page and resolves
 * each request only after the user confirms or cancels it.
 */
export function useConfirmationDialog() {
  const request = shallowRef<ConfirmationRequest | null>(null)
  let resolvePending: ((confirmed: boolean) => void) | null = null

  function confirm(nextRequest: ConfirmationRequest): Promise<boolean> {
    if (resolvePending) return Promise.resolve(false)
    request.value = nextRequest
    return new Promise(resolve => { resolvePending = resolve })
  }

  function resolve(confirmed: boolean) {
    const resolveCurrent = resolvePending
    resolvePending = null
    request.value = null
    resolveCurrent?.(confirmed)
  }

  onBeforeUnmount(() => resolve(false))

  return { request, confirm, resolve }
}

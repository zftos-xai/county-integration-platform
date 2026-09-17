import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import type { Ref } from 'vue'

const FOCUSABLE_SELECTOR = [
  'a[href]', 'button:not([disabled])', 'input:not([disabled])',
  'select:not([disabled])', 'textarea:not([disabled])', '[tabindex]:not([tabindex="-1"])',
].join(',')

/**
 * Provides focus entry, focus trapping, Escape handling and trigger-focus restoration for a modal dialog.
 * The dialog owner remains responsible for deciding whether unsaved changes allow closure.
 */
export function useModalDialog(isOpen: Ref<boolean>, requestClose: () => void) {
  const dialogRef = ref<HTMLElement | null>(null)
  let returnFocus: HTMLElement | null = null

  // Focus moves only when the dialog visibility changes, not on every reactive form update.
  watch(isOpen, async open => {
    if (open) {
      returnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
      await nextTick()
      const firstControl = dialogRef.value?.querySelector<HTMLElement>(FOCUSABLE_SELECTOR)
      ;(firstControl ?? dialogRef.value)?.focus()
      return
    }
    await nextTick()
    returnFocus?.focus()
    returnFocus = null
  }, { immediate: true })

  function handleDialogKeydown(event: KeyboardEvent) {
    if (event.key === 'Escape') {
      event.preventDefault()
      requestClose()
      return
    }
    if (event.key !== 'Tab' || !dialogRef.value) return
    const controls = [...dialogRef.value.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)]
      .filter(control => control.offsetParent !== null)
    if (!controls.length) {
      event.preventDefault()
      dialogRef.value.focus()
      return
    }
    const first = controls[0]
    const last = controls.at(-1)!
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }

  onBeforeUnmount(() => {
    returnFocus?.focus()
    returnFocus = null
  })

  return { dialogRef, handleDialogKeydown }
}

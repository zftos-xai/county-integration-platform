import { nextTick, onUnmounted, ref, watch } from 'vue'
import type { Ref } from 'vue'

const FOCUSABLE_SELECTOR = [
  'a[href]', 'button:not([disabled])', 'input:not([disabled])',
  'select:not([disabled])', 'textarea:not([disabled])', '[tabindex]:not([tabindex="-1"])',
].join(',')

let activeModalCount = 0
let originalScrollStyles: {
  htmlOverflow: string
  htmlOverflowPriority: string
  bodyOverflow: string
  bodyOverflowPriority: string
  bodyPaddingRight: string
  bodyPaddingRightPriority: string
} | null = null

function restoreInlineStyle(element: HTMLElement, property: string, value: string, priority: string) {
  if (value) {
    element.style.setProperty(property, value, priority)
    return
  }
  element.style.removeProperty(property)
}

// Shared modal ownership is reference-counted so nested dialogs cannot unlock the page early.
function acquireDocumentScrollLock() {
  if (typeof document === 'undefined') return () => undefined

  const html = document.documentElement
  const body = document.body
  if (activeModalCount === 0) {
    // Preserve page alignment when locking removes a classic vertical scrollbar.
    const scrollbarWidth = Math.max(0, window.innerWidth - html.clientWidth)
    const computedBodyPadding = Number.parseFloat(getComputedStyle(body).paddingRight) || 0
    originalScrollStyles = {
      htmlOverflow: html.style.getPropertyValue('overflow'),
      htmlOverflowPriority: html.style.getPropertyPriority('overflow'),
      bodyOverflow: body.style.getPropertyValue('overflow'),
      bodyOverflowPriority: body.style.getPropertyPriority('overflow'),
      bodyPaddingRight: body.style.getPropertyValue('padding-right'),
      bodyPaddingRightPriority: body.style.getPropertyPriority('padding-right'),
    }
    html.style.setProperty('overflow', 'hidden')
    body.style.setProperty('overflow', 'hidden')
    if (scrollbarWidth > 0) body.style.setProperty('padding-right', `${computedBodyPadding + scrollbarWidth}px`)
  }
  activeModalCount += 1

  let released = false
  return () => {
    if (released) return
    released = true
    activeModalCount = Math.max(0, activeModalCount - 1)
    if (activeModalCount !== 0 || !originalScrollStyles) return

    restoreInlineStyle(html, 'overflow', originalScrollStyles.htmlOverflow, originalScrollStyles.htmlOverflowPriority)
    restoreInlineStyle(body, 'overflow', originalScrollStyles.bodyOverflow, originalScrollStyles.bodyOverflowPriority)
    restoreInlineStyle(body, 'padding-right', originalScrollStyles.bodyPaddingRight, originalScrollStyles.bodyPaddingRightPriority)
    originalScrollStyles = null
  }
}

/**
 * Provides focus entry, focus trapping, Escape handling and trigger-focus restoration for a modal dialog.
 * The dialog owner remains responsible for deciding whether unsaved changes allow closure.
 */
export function useModalDialog(isOpen: Ref<boolean>, requestClose: () => void) {
  const dialogRef = ref<HTMLElement | null>(null)
  let returnFocus: HTMLElement | null = null
  let releaseScrollLock: (() => void) | null = null

  watch(isOpen, open => {
    if (open) {
      releaseScrollLock = acquireDocumentScrollLock()
      return
    }
    releaseScrollLock?.()
    releaseScrollLock = null
  }, { immediate: true })

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
      .filter(control => control.getClientRects().length > 0 && getComputedStyle(control).visibility !== 'hidden')
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

  onUnmounted(() => {
    releaseScrollLock?.()
    releaseScrollLock = null
    const target = returnFocus
    returnFocus = null
    // Restore after the modal DOM has left the document so the removed close
    // button cannot retain focus and the triggering control is focusable again.
    queueMicrotask(() => target?.focus())
  })

  return { dialogRef, handleDialogKeydown }
}

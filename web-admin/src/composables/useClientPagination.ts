import { computed, ref, watch } from 'vue'
import type { ComputedRef } from 'vue'

/**
 * Applies consistent client-side paging to an already filtered row collection.
 * The current page is reset when filtering changes the collection and is clamped
 * when a refresh removes rows from the final page.
 */
export function useClientPagination<T>(rows: ComputedRef<T[]>, defaultPageSize = 10) {
  const page = ref(1)
  const pageSize = ref(defaultPageSize)
  const pageCount = computed(() => Math.max(1, Math.ceil(rows.value.length / pageSize.value)))
  const pagedRows = computed(() => {
    const offset = (page.value - 1) * pageSize.value
    return rows.value.slice(offset, offset + pageSize.value)
  })

  watch(rows, () => { page.value = 1 })
  watch(pageCount, count => { if (page.value > count) page.value = count })

  return { page, pageSize, pagedRows }
}

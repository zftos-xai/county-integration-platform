<!-- 通用受控分页组件：页码和每页数量均由父页面持有。 -->
<script setup lang="ts">
import { computed, watch } from 'vue'
import { IconChevronLeft, IconChevronRight } from '@tabler/icons-vue'

const props = withDefaults(defineProps<{ total: number; page: number; pageSize: number; pageSizes?: number[]; compact?: boolean }>(), {
  pageSizes: () => [10, 20, 50],
  compact: false,
})
const emit = defineEmits<{ 'update:page': [value: number]; 'update:pageSize': [value: number] }>()
const pageCount = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))
const pages = computed(() => {
  const start = Math.max(1, Math.min(props.page - 2, pageCount.value - 4))
  const end = Math.min(pageCount.value, start + 4)
  return Array.from({ length: end - start + 1 }, (_, index) => start + index)
})
// 数据量缩小时将父页面页码拉回有效范围，避免出现空白的越界页。
watch(pageCount, count => { if (props.page > count) emit('update:page', count) })
function changePage(value: number) { emit('update:page', Math.max(1, Math.min(value, pageCount.value))) }
function changePageSize(event: Event) {
  emit('update:pageSize', Number((event.target as HTMLSelectElement).value))
  emit('update:page', 1)
}
</script>

<template>
  <footer class="standard-pagination" :class="{ compact }" aria-label="列表分页">
    <nav aria-label="分页导航"><ul class="pagination pagination-sm m-0">
      <li :class="['page-item', { disabled: page <= 1 }]"><button class="page-link" aria-label="上一页" :disabled="page <= 1" @click="changePage(page - 1)"><IconChevronLeft :size="15" /></button></li>
      <li v-for="number in pages" :key="number" :class="['page-item', { active: number === page }]"><button class="page-link" :aria-label="`第 ${number} 页`" :aria-current="number === page ? 'page' : undefined" @click="changePage(number)">{{ number }}</button></li>
      <li :class="['page-item', { disabled: page >= pageCount }]"><button class="page-link" aria-label="下一页" :disabled="page >= pageCount" @click="changePage(page + 1)"><IconChevronRight :size="15" /></button></li>
    </ul></nav>
    <div class="pagination-summary"><select class="form-select form-select-sm" aria-label="每页条数" :value="pageSize" @change="changePageSize"><option v-for="size in pageSizes" :key="size" :value="size">{{ size }} 条/页</option></select><span>共 {{ total }} 条</span></div>
  </footer>
</template>

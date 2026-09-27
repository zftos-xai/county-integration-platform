import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('dictionary item filters use the shared responsive toolbar without global commands', async () => {
  const page = await readFile(new URL('../src/views/configuration/dictionary/DictionaryView.vue', import.meta.url), 'utf8')
  const component = await readFile(new URL('../src/components/ListQueryToolbar.vue', import.meta.url), 'utf8')
  const styles = await readFile(new URL('../src/assets/styles/prototype.css', import.meta.url), 'utf8')

  assert.match(page, /<ListQueryToolbar class="dictionary-item-toolbar" filters-only filters-layout="search-with-filter">[\s\S]*?v-model="itemQuery"[\s\S]*?v-model="itemStatusFilter"[\s\S]*?<\/ListQueryToolbar>/)
  assert.doesNotMatch(page, /\.dictionary-item-toolbar \.standard-list-toolbar--filters-only/)
  assert.match(component, /filtersOnly\?: boolean/)
  assert.match(component, /v-if="!filtersOnly" class="standard-list-toolbar__commands"/)
  assert.match(component, /class="work-quiet-button standard-list-toolbar__refresh" type="button"/)
  assert.match(styles, /\.standard-list-toolbar__filters--search-with-filter \{ grid-template-columns: minmax\(0, 1fr\) minmax\(160px, 210px\); \}/)
  assert.match(styles, /\.standard-list-toolbar--filters-only \{ min-height: 0; padding: 0; display: grid; grid-template-columns: minmax\(0, 1fr\); border: 0;/)
  assert.match(styles, /\.standard-list-toolbar--filters-only \.standard-list-toolbar__filters \{ grid-column: 1; grid-row: 1; \}/)
  assert.match(styles, /@container \(max-width: 760px\)[\s\S]*?\.standard-list-toolbar__filters--search-with-filter \{ grid-template-columns: minmax\(0, 1fr\); \}/)
})

test('dictionary type pagination stays outside the scrollable mobile type list', async () => {
  const page = await readFile(new URL('../src/views/configuration/dictionary/DictionaryView.vue', import.meta.url), 'utf8')

  assert.match(page, /<aside class="dictionary-types"><div class="dictionary-type-list">[\s\S]*?<\/div><AdminPagination v-if="filteredTypes\.length > 0" compact/)
  assert.match(page, /@media \(max-width: 900px\)[\s\S]*?\.dictionary-types \{ max-height: none; overflow: visible;[\s\S]*?\.dictionary-type-list \{ max-height: 260px; overflow-y: auto; \}/)
  assert.match(page, /<AdminTableFrame label="字典项列表" has-actions :pin-actions="false">/)
})

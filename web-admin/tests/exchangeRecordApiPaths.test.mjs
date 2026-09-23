import assert from 'node:assert/strict'
import test from 'node:test'
import { exchangeRecordsPath } from '../src/api/exchange/exchangeRecordApiPaths.ts'

test('HIS调用记录查询始终携带机构范围并安全编码可选筛选', () => {
  assert.equal(
    exchangeRecordsPath({ organizationCode: ' ORG001 ' }),
    '/exchange-records?organizationCode=ORG001&limit=100',
  )
  assert.equal(
    exchangeRecordsPath({
      organizationCode: 'ORG001', interfaceCode: ' 100-008 ', sourceRecordId: 'EXTERNAL_ENDPOINT:9',
      result: 'NO_RESPONSE', receivedFrom: '2026-09-23T01:00:00',
      receivedTo: '2026-09-23T02:00:00',
    }),
    '/exchange-records?organizationCode=ORG001&receivedFrom=2026-09-23T01%3A00%3A00&receivedTo=2026-09-23T02%3A00%3A00&interfaceCode=100-008&sourceRecordId=EXTERNAL_ENDPOINT%3A9&result=NO_RESPONSE&limit=100',
  )
})

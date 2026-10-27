import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const load = name => JSON.parse(readFileSync(new URL(`../../../contracts/openapi/v1/${name}.yaml`, import.meta.url), 'utf8'))
const payment = load('payment')

test('channel credentials are write-only and absent from every read projection', () => {
  for (const key of ['clientId', 'apiKey', 'checksumKey']) {
    assert.equal(payment.components.schemas.PaymentChannelInput.properties[key].writeOnly, true)
    assert.equal(key in payment.components.schemas.PaymentChannel.properties, false)
    assert.equal(JSON.stringify(load('facility').components.schemas).includes(`"${key}"`), false)
  }
  assert.equal(payment.components.schemas.PaymentChannel.properties.credentialsConfigured.type, 'boolean')
})

test('native webhook is the only new public money command and description fits VA', () => {
  assert.deepEqual(payment.paths['/payments/providers/payos/{channelId}/webhook'].post.security, [])
  for (const path of ['/payments/facilities/{id}/channel', '/payments/{id}/refresh']) {
    for (const operation of Object.values(payment.paths[path])) assert.deepEqual(operation.security, [{ bearerAuth: [] }])
  }
  const description = payment.components.schemas.PaymentQrSession.properties.description
  assert.equal(description.maxLength, 9)
  assert.match('SH0000001', new RegExp(description.pattern))
  assert.doesNotMatch('SH 12345678', new RegExp(description.pattern))
})

test('manual completion cannot choose outcome, money or payee; fee has no default', () => {
  const command = load('booking').components.schemas.ManualDisbursementCommand
  assert.equal(command.additionalProperties, false)
  assert.deepEqual(command.required, ['version', 'bankTransactionReference'])
  const fee = load('transfer').components.schemas.FacilityTransferTerms.properties.feeBps
  assert.equal(fee.minimum, 0)
  assert.equal(fee.maximum, 10000)
  assert.equal('default' in fee, false)
})

test('local OpenAPI references and required fields resolve', () => {
  for (const name of ['payment', 'booking', 'transfer', 'facility', 'admin']) {
    const document = load(name)
    function visit(node) {
      if (!node || typeof node !== 'object') return
      if (node.$ref?.startsWith('#/')) assert.ok(node.$ref.slice(2).split('/').reduce((value, key) => value?.[key], document), node.$ref)
      if (node.additionalProperties === false && Array.isArray(node.required)) for (const key of node.required) assert.ok(node.properties?.[key], key)
      Object.values(node).forEach(visit)
    }
    visit(document)
  }
})

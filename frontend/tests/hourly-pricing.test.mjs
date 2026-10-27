import assert from 'node:assert/strict'
import test from 'node:test'
import { moduleLoader } from './support/load-module.mjs'

test('Owner saves and previews explicit hourly rates while keeping legacy prices in their own unit', () => {
  const { pricingInput } = moduleLoader()('features/schedule/pricing-input.ts')
  const form = new FormData()
  for (const [key, value] of Object.entries({
    pricingUnit: 'HOURLY',
    price: '100000',
    dayOfWeek: '1',
    startsAt: '15:00',
    endsAt: '16:00',
    priority: '0',
    effectiveFrom: '2026-10-26',
    label: 'Giá giờ',
  }))
    form.set(key, value)
  const hourly = pricingInput(form, false)
  assert.equal(hourly.pricePerHour, 100000)
  assert.equal(hourly.pricePerSlot, null)
  form.set('pricingUnit', 'LEGACY_SLOT')
  form.set('price', '100000.25')
  const legacy = pricingInput(form, false)
  assert.equal(legacy.pricePerSlot, 100000.25)
  assert.equal(legacy.pricePerHour, null)
  form.set('pricingUnit', 'HOURLY')
  assert.throws(() => pricingInput(form, false))
  form.delete('pricingUnit')
  assert.throws(() => pricingInput(form, false))
})

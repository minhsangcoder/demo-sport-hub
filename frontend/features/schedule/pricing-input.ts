export function pricingInput(form: FormData, specificDate: boolean) {
  const value = (name: string) => String(form.get(name) || '')
  const pricingUnit = value('pricingUnit')
  const price = Number(value('price'))
  if (
    !['HOURLY', 'LEGACY_SLOT'].includes(pricingUnit) ||
    !Number.isFinite(price) ||
    price <= 0 ||
    (pricingUnit === 'HOURLY' && !Number.isInteger(price))
  )
    throw new Error('Chọn đơn vị và nhập mức giá hợp lệ; giá theo giờ phải là số nguyên đồng.')
  return {
    courtId: value('courtId') || null,
    sportCategoryId: value('sportCategoryId') || null,
    courtTypeId: value('courtTypeId') || null,
    dayOfWeek: specificDate ? null : Number(value('dayOfWeek')),
    specificDate: specificDate ? value('specificDate') : null,
    startsAt: value('startsAt'),
    endsAt: value('endsAt'),
    pricingUnit,
    pricePerSlot: pricingUnit === 'LEGACY_SLOT' ? price : null,
    pricePerHour: pricingUnit === 'HOURLY' ? price : null,
    priority: Number(value('priority')),
    label: value('label'),
    effectiveFrom: value('effectiveFrom'),
    effectiveTo: value('effectiveTo') || null,
    currency: 'VND',
  }
}

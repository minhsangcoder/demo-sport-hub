import { provinceByCode, wardByCode } from './administrative-divisions'
import type { FacilityLocation } from './location-picker'
import type { FacilityInput } from './types'
export function facilityFrom(
  form: FormData,
  location: FacilityLocation,
  nameField = 'name',
): FacilityInput {
  const text = (name: string) => String(form.get(name) ?? '').trim()
  const province = provinceByCode(text('provinceCode'))
  const ward = wardByCode(province?.code, text('wardCode'))
  return {
    name: text(nameField),
    phone: text('phone'),
    contactEmail: text('contactEmail') || null,
    addressLine: text('addressLine'),
    provinceCode: text('provinceCode'),
    province: province?.name ?? text('province'),
    wardCode: text('wardCode'),
    ward: ward?.name ?? text('ward'),
    description: text('description'),
    timezone: text('timezone') || 'Asia/Ho_Chi_Minh',
    amenities: text('amenities')
      .split(',')
      .map((x) => x.trim())
      .filter(Boolean),
    ...(location ?? {}),
  }
}

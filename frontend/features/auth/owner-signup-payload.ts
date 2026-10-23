import { facilityFrom } from '@/features/facility/facility-payload'
import type { FacilityLocation } from '@/features/facility/location-picker'
import { validateSignupFiles } from '@/features/facility/signup-files'
import { legalFrom } from '@/features/owner-application/legal-payload'
import { accountFrom } from './account-payload'

export class OwnerSignupError extends Error {
  constructor(
    message: string,
    public field: string,
  ) {
    super(message)
  }
}

export function ownerSignupFrom(form: FormData, location: FacilityLocation) {
  if (!location) throw new Error('Chọn vị trí cơ sở trên bản đồ trước khi gửi.')
  try {
    validateSignupFiles(form)
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Tệp không hợp lệ'
    const field = message.startsWith('Giấy tờ định danh')
      ? 'identityDocument'
      : message.startsWith('Giấy tờ địa điểm')
        ? 'locationDocument'
        : 'facilityImage'
    throw new OwnerSignupError(message, field)
  }
  const text = (name: string) => String(form.get(name) ?? '').trim()
  const setup = {
    courtCode: text('courtCode'),
    courtName: text('courtName'),
    sportCategoryId: text('sportCategoryId'),
    days: form.getAll('days').map(Number),
    opensAt: text('opensAt'),
    closesAt: text('closesAt'),
    slotMinutes: Number(text('slotMinutes')),
    pricePerSlot: Number(text('pricePerSlot')),
  }
  if (!setup.days.length) throw new Error('Chọn ít nhất một ngày mở cửa.')
  if (setup.opensAt >= setup.closesAt) throw new Error('Giờ mở cửa phải trước giờ đóng cửa.')
  const body = {
    ...accountFrom(form),
    applyAsOwner: true,
    ownerApplication: {
      legal: legalFrom(form),
      facility: facilityFrom(form, location, 'facilityName'),
      setup,
    },
  }
  const names = ['identityDocument', 'locationDocument', 'facilityImage']
  const fingerprint =
    JSON.stringify(body) +
    names
      .map((name) => {
        const file = form.get(name) as File
        return `${file.name}:${file.size}:${file.lastModified}`
      })
      .join('|')
  const multipart = new FormData()
  multipart.set('request', new Blob([JSON.stringify(body)], { type: 'application/json' }))
  for (const name of names) multipart.set(name, form.get(name) as File)
  return { body, fingerprint, multipart }
}

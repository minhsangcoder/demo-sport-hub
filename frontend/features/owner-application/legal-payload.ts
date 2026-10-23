import type { Legal } from './types'
export const legalFields: [keyof Legal, string, number, boolean][] = [
  ['representativeName', 'Họ tên người đại diện', 180, true],
  ['identityNumber', 'Số CCCD / giấy tờ định danh', 50, true],
  ['businessName', 'Tên hộ kinh doanh / doanh nghiệp', 180, true],
  ['businessLicense', 'Số giấy phép kinh doanh', 100, false],
  ['taxCode', 'Mã số thuế', 50, false],
  ['bankName', 'Ngân hàng nhận thanh toán', 100, true],
  ['bankAccountHolder', 'Chủ tài khoản ngân hàng', 100, true],
  ['bankAccountNumber', 'Số tài khoản ngân hàng', 50, true],
]
export function legalFrom(form: FormData): Legal {
  return Object.fromEntries(
    legalFields.map(([key, , , required]) => [
      key,
      String(form.get(key) || '').trim() || (required ? '' : null),
    ]),
  ) as Legal
}

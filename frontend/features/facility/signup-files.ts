export function validateSignupFiles(form: FormData) {
  for (const [key, label, image] of [
    ['identityDocument', 'Giấy tờ định danh', false],
    ['locationDocument', 'Giấy tờ địa điểm', false],
    ['facilityImage', 'Ảnh cơ sở', true],
  ] as const) {
    const file = form.get(key)
    if (!(file instanceof File) || !file.size || file.size > 10 * 1024 * 1024)
      throw new Error(`${label}: chọn tệp từ 1 byte đến 10 MB.`)
    if (!['image/png', 'image/jpeg', ...(image ? [] : ['application/pdf'])].includes(file.type))
      throw new Error(`${label}: định dạng tệp không được hỗ trợ.`)
  }
}

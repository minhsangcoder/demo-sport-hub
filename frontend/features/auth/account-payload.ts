export function accountFrom(form: FormData) {
  const contact = String(form.get('identifier') ?? '').trim()
  return {
    fullName: String(form.get('fullName') ?? '').trim(),
    password: String(form.get('password') ?? ''),
    ...(contact.includes('@') ? { email: contact } : { phone: contact }),
  }
}

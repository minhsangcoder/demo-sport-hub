import type { InputHTMLAttributes } from 'react'
export function DocumentFileField({
  label,
  image = false,
  error,
  ...props
}: { label: string; image?: boolean; error?: string } & InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className="grid min-w-0 gap-3 text-sm font-medium">
      {label}
      <span className="text-xs font-normal text-slate-500">
        {image ? 'JPEG hoặc PNG' : 'PDF, JPEG hoặc PNG'} · tối đa 10 MB
      </span>
      <input
        className="block w-full min-w-0 rounded-lg border p-3 text-sm"
        type="file"
        accept={image ? 'image/jpeg,image/png' : 'application/pdf,image/jpeg,image/png'}
        required
        aria-invalid={!!error}
        {...props}
      />
      {error && <span className="text-xs text-red-700">{error}</span>}
    </label>
  )
}

export { validateSignupFiles } from './signup-files'

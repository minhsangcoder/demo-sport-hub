'use client'
import { Input } from '@/components/ui/input'
import type { Legal } from './types'
import { legalFields } from './legal-payload'
export function LegalFields({
  legal,
  busy = false,
  fields = {},
}: {
  legal?: Legal
  busy?: boolean
  fields?: Record<string, string>
}) {
  return (
    <>
      {legalFields.map(([name, label, max, required]) => (
        <label
          key={name}
          className="flex flex-col justify-start min-w-0 gap-2 text-sm font-medium self-start w-full"
        >
          <span>
            {label}
            {required ? (
              <span className="ml-1 font-bold text-red-500">*</span>
            ) : (
              <span className="font-normal text-slate-400"> (tùy chọn)</span>
            )}
          </span>
          <Input
            name={name}
            defaultValue={legal?.[name] ?? ''}
            maxLength={max}
            required={required}
            disabled={busy}
            autoComplete="off"
            aria-invalid={!!fields[name]}
          />
          {fields[name] && <span className="text-xs text-red-700">{fields[name]}</span>}
        </label>
      ))}
    </>
  )
}

export { legalFields, legalFrom } from './legal-payload'

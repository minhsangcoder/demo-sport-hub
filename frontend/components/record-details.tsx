import { displayLabel, fieldLabel, timezoneLabel } from '@/lib/display-labels'
import { translateError } from '@/lib/error-translator'

const enumFields = new Set(['state', 'status', 'previousstatus', 'previousstate', 'newstate', 'action', 'source', 'changesource',
  'roles', 'permissions', 'purpose', 'workflow', 'outcome', 'mode', 'method', 'provider', 'kind', 'direction', 'category', 'rounding'])

/** Display system records with Vietnamese labels, preserving identifiers and user content. */
export function RecordDetails({ value, field = '' }: { value: unknown; field?: string }) {
  if (value == null) return <span>Chưa có thông tin</span>
  if (typeof value === 'string' && /^[\[{]/.test(value.trim())) {
    let parsed: unknown
    try { parsed = JSON.parse(value) } catch { /* A user note may start with a bracket. */ }
    if (parsed && typeof parsed === 'object') return <RecordDetails value={parsed} field={field} />
  }
  if (Array.isArray(value)) return value.length ? <ol className="space-y-3">{value.map((item, index) => <li key={index}><RecordDetails value={item} field={field} /></li>)}</ol> : <span>Không có mục nào</span>
  if (typeof value === 'object') return <dl className="space-y-3">{Object.entries(value).map(([key, item]) => <div key={key} className="grid gap-1 sm:grid-cols-[minmax(8rem,1fr)_3fr]"><dt className="font-medium text-slate-500">{fieldLabel(key)}</dt><dd className="min-w-0 break-words"><RecordDetails value={item} field={key} /></dd></div>)}</dl>
  if (typeof value === 'boolean') return <span>{value ? 'Có' : 'Không'}</span>
  if (typeof value === 'number') return <span>{value.toLocaleString('vi-VN', { maximumFractionDigits: 20 })}</span>
  const text = String(value)
  const key = field.replace(/_/g, '').toLowerCase()
  if (key === 'timezone') return <span>{timezoneLabel(text)}</span>
  if (enumFields.has(key)) return <span>{displayLabel(text, key === 'provider' ? text : 'Chưa xác định')}</span>
  if (['reason', 'message', 'lasterror', 'lasterrorcode'].includes(key)) return <span>{translateError(text, { preserveUnknown: true })}</span>
  return <span className="whitespace-pre-wrap">{text || 'Chưa có thông tin'}</span>
}

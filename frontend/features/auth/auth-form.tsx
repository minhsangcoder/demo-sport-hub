'use client'
import dynamic from 'next/dynamic'
import { AuthAccountFields } from './auth-account-fields'
import { AuthFeedback } from './auth-feedback'
import { AuthHeading } from './auth-heading'
import type { Mode } from './auth-mode'
import { AuthOptions } from './auth-options'
import { AuthRecoveryFields } from './auth-recovery-fields'
import { AuthShell } from './auth-shell'
import { useAuthForm } from './use-auth-form'
const OwnerRegistration = dynamic(
  () => import('./owner-registration').then((module) => module.OwnerRegistration),
  { loading: () => <p role="status">Đang mở lựa chọn chủ cơ sở…</p> },
)
export function AuthForm({ mode = 'login' }: { mode?: Mode }) {
  const model = useAuthForm({ mode })
  return (
    <AuthShell mode={model.mode} isExpandedOwner={model.isExpandedOwner}>
      <AuthHeading model={model} />
      <form onSubmit={model.submit} className="space-y-4">
        <AuthAccountFields model={model} />
        {model.mode === 'register' && <OwnerRegistration model={model} />}
        <AuthRecoveryFields model={model} />
        <AuthFeedback model={model} />
        <AuthOptions model={model} />
      </form>
    </AuthShell>
  )
}

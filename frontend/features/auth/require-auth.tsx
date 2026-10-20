'use client'
import Link from 'next/link'
import { usePathname,useRouter } from 'next/navigation'
import { useAuth } from '@/lib/auth-context'
import type { Role } from '@/lib/api'
export function RequireAuth({ children, roles }: { children: React.ReactNode; roles?: Role[] }) {
  const { user, ready } = useAuth()
  const pathname=usePathname();const router=useRouter()
  if (!ready) return <p role="status" className="p-8">Đang kiểm tra phiên đăng nhập…</p>
  if (!user) return <div className="p-8"><h1 className="text-xl font-bold">Vui lòng đăng nhập</h1><Link className="mt-3 inline-block text-emerald-700 underline" href={`/login?next=${encodeURIComponent(pathname)}`} onClick={e=>{e.preventDefault();router.push(`/login?next=${encodeURIComponent(window.location.pathname+window.location.search)}`)}}>Đăng nhập để tiếp tục</Link></div>
  if (roles && !roles.some(role => user.roles.includes(role))) {
    if (user.roles.includes('STAFF') && !user.roles.includes('OWNER') && !user.roles.includes('ADMIN')) {
      return (
        <div className="mx-auto max-w-xl p-8 text-center space-y-4">
          <p role="alert" className="text-amber-800 font-semibold">
            Tài khoản nhân viên không có quyền truy cập tính năng khách hàng.
          </p>
          <Link
            className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-5 py-2.5 text-sm font-bold text-white hover:bg-emerald-700 shadow-sm"
            href="/staff/bookings"
          >
            Đến màn hình Kiểm Tra Booking
          </Link>
        </div>
      )
    }
    return <p role="alert" className="p-8 text-red-700">Tài khoản không có quyền truy cập khu vực này.</p>
  }
  return children
}

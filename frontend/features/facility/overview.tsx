'use client'

import Image from 'next/image'
import Link from 'next/link'
import { useState } from 'react'
import { api } from '@/lib/api'
import { useApi } from '@/lib/use-api'
import { Button } from '@/components/ui/button'
import { useFacility } from './facility-context'
import type { Media, Court } from './types'
import {
  Layers,
  MapPin,
  Phone,
  Mail,
  Upload,
  Trash2,
  RefreshCw,
  PlusCircle,
  Tag,
  Wrench,
  Clock,
  Sparkles,
  CheckCircle2,
  AlertCircle,
  ImageIcon,
} from 'lucide-react'

export function FacilityOverview() {
  const { facility, basePath, editable, applicationId } = useFacility()
  const images = useApi<Media[]>(`/owner/facilities/${facility.id}/images`)
  const courts = useApi<Court[]>(`/owner/facilities/${facility.id}/courts`)

  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [replacement, setReplacement] = useState<string | null>(null)

  async function upload(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    setBusy(true)
    setError('')
    setMessage('')
    const formElement = event.currentTarget

    try {
      await api(`/owner/facilities/${facility.id}/images${replacement ? '/' + replacement : ''}`, {
        method: replacement ? 'PUT' : 'POST',
        body: new FormData(formElement),
      })
      images.reload()
      formElement.reset()
      setMessage(replacement ? 'Đã thay thế ảnh thành công.' : 'Đã tải ảnh cơ sở lên thành công.')
      setReplacement(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể tải ảnh lên.')
    } finally {
      setBusy(false)
    }
  }

  async function remove(id: string) {
    if (!window.confirm('Bạn có chắc muốn xóa ảnh này khỏi cơ sở?')) return
    setBusy(true)
    setError('')
    try {
      await api(`/owner/facilities/${facility.id}/images/${id}`, { method: 'DELETE' })
      images.reload()
      setMessage('Đã xóa ảnh.')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể xóa ảnh.')
    } finally {
      setBusy(false)
    }
  }

  const activeCourtsCount = courts.data?.filter((c) => c.enabled).length ?? 0
  const totalCourtsCount = courts.data?.length ?? 0

  return (
    <div className="space-y-8">
      {/* 1. TOP KPI CARDS */}
      <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
        {/* KPI 1: Courts */}
        <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">Sân vận hành</span>
            <div className="flex size-9 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600 dark:bg-emerald-950 dark:text-emerald-400">
              <Layers size={18} />
            </div>
          </div>
          <div className="mt-4 flex items-baseline gap-2">
            <span className="text-3xl font-black text-slate-900 dark:text-white">
              {courts.loading ? '…' : totalCourtsCount}
            </span>
            <span className="text-xs font-semibold text-emerald-600 dark:text-emerald-400">
              ({activeCourtsCount} sân đang bật)
            </span>
          </div>
          <p className="mt-2 text-xs text-slate-500">
            {totalCourtsCount > 0 ? 'Sẵn sàng phục vụ khách đặt trực tuyến' : 'Chưa có sân nào được thêm'}
          </p>
        </div>

        {/* KPI 2: Status */}
        <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">Trạng thái cơ sở</span>
            <div className="flex size-9 items-center justify-center rounded-xl bg-amber-50 text-amber-600 dark:bg-amber-950 dark:text-amber-400">
              <CheckCircle2 size={18} />
            </div>
          </div>
          <div className="mt-4">
            <span className="text-xl font-black text-slate-900 dark:text-white">
              {facility.status === 'ACTIVE'
                ? 'Đang hoạt động'
                : facility.status === 'PENDING_APPROVAL'
                ? 'Đang chờ duyệt'
                : facility.status === 'DRAFT'
                ? 'Bản nháp'
                : facility.status}
            </span>
          </div>
          <p className="mt-2 text-xs text-slate-500">
            {facility.status === 'ACTIVE'
              ? 'Đã hiển thị trên tìm kiếm công khai'
              : 'Hồ sơ cần được phê duyệt để mở bán'}
          </p>
        </div>

        {/* KPI 3: Location & Timezone */}
        <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">Khu vực & Múi giờ</span>
            <div className="flex size-9 items-center justify-center rounded-xl bg-blue-50 text-blue-600 dark:bg-blue-950 dark:text-blue-400">
              <MapPin size={18} />
            </div>
          </div>
          <div className="mt-4">
            <span className="text-xl font-black text-slate-900 dark:text-white truncate block">
              {facility.province || 'Toàn quốc'}
            </span>
          </div>
          <p className="mt-2 text-xs text-slate-500 truncate">Múi giờ vận hành: {facility.timezone}</p>
        </div>

        {/* KPI 4: Photos */}
        <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">Hình ảnh cơ sở</span>
            <div className="flex size-9 items-center justify-center rounded-xl bg-purple-50 text-purple-600 dark:bg-purple-950 dark:text-purple-400">
              <ImageIcon size={18} />
            </div>
          </div>
          <div className="mt-4 flex items-baseline gap-2">
            <span className="text-3xl font-black text-slate-900 dark:text-white">
              {images.loading ? '…' : images.data?.length ?? 0}
            </span>
            <span className="text-xs text-slate-400">ảnh</span>
          </div>
          <p className="mt-2 text-xs text-slate-500">Ảnh thực tế giúp tăng 40% tỷ lệ đặt sân</p>
        </div>
      </div>

      {/* 2. QUICK ACTION BAR */}
      <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400 mb-3">Thao tác nhanh</h3>
        <div className="flex flex-wrap gap-3">
          <Link
            href={`${basePath}/courts/new`}
            className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 text-xs font-bold text-white shadow-sm hover:bg-emerald-500 transition"
          >
            <PlusCircle size={15} />
            <span>Thêm sân mới</span>
          </Link>
          <Link
            href={`${basePath}/pricing`}
            className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-xs font-bold text-slate-700 shadow-sm hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200"
          >
            <Tag size={15} className="text-emerald-600" />
            <span>Cấu hình bảng giá</span>
          </Link>
          <Link
            href={`${basePath}/schedule`}
            className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-xs font-bold text-slate-700 shadow-sm hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200"
          >
            <Clock size={15} className="text-blue-600" />
            <span>Lịch mở cửa</span>
          </Link>
          <Link
            href={`${basePath}/maintenance`}
            className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-xs font-bold text-slate-700 shadow-sm hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200"
          >
            <Wrench size={15} className="text-amber-600" />
            <span>Khóa bảo trì sân</span>
          </Link>
        </div>
      </div>

      {/* 3. COURTS TABLE SUMMARY */}
      <div className="rounded-2xl border border-slate-200/80 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h3 className="text-lg font-bold text-slate-900 dark:text-white">Danh sách sân trực thuộc</h3>
            <p className="text-xs text-slate-500">Các sân đang được cấu hình trong cơ sở</p>
          </div>
          <Link
            href={`${basePath}/courts`}
            className="text-xs font-bold text-emerald-600 hover:underline dark:text-emerald-400"
          >
            Quản lý chi tiết →
          </Link>
        </div>

        {courts.loading && <p className="text-xs text-slate-400 py-4">Đang tải danh sách sân…</p>}

        {!courts.loading && courts.data?.length === 0 && (
          <div className="rounded-xl border border-dashed border-slate-300 p-8 text-center dark:border-slate-700">
            <Layers className="mx-auto size-8 text-slate-400" />
            <p className="mt-2 text-sm font-bold text-slate-700 dark:text-slate-300">Chưa có sân nào trong cơ sở</p>
            <p className="text-xs text-slate-500 mt-1">Hãy thêm sân đầu tiên để khách có thể đặt lịch.</p>
            <Link
              href={`${basePath}/courts/new`}
              className="mt-4 inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-3.5 py-1.5 text-xs font-bold text-white hover:bg-emerald-500"
            >
              <PlusCircle size={14} />
              <span>Thêm sân ngay</span>
            </Link>
          </div>
        )}

        {!courts.loading && courts.data && courts.data.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-slate-100 text-slate-400 dark:border-slate-800">
                  <th className="py-2.5 font-bold uppercase tracking-wider">Mã sân</th>
                  <th className="py-2.5 font-bold uppercase tracking-wider">Tên sân đấu</th>
                  <th className="py-2.5 font-bold uppercase tracking-wider">Trạng thái</th>
                  <th className="py-2.5 font-bold uppercase tracking-wider text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                {courts.data.map((c) => (
                  <tr key={c.id} className="hover:bg-slate-50 dark:hover:bg-slate-800/50">
                    <td className="py-3 font-mono font-bold text-slate-600 dark:text-slate-300">{c.code}</td>
                    <td className="py-3 font-bold text-slate-900 dark:text-white">{c.name}</td>
                    <td className="py-3">
                      <span
                        className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-[10px] font-bold ${
                          c.enabled
                            ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-400'
                            : 'bg-slate-100 text-slate-500 dark:bg-slate-800'
                        }`}
                      >
                        <span className={`size-1.5 rounded-full ${c.enabled ? 'bg-emerald-500' : 'bg-slate-400'}`} />
                        <span>{c.enabled ? 'Đang bật' : 'Tạm tắt'}</span>
                      </span>
                    </td>
                    <td className="py-3 text-right">
                      <Link
                        href={`${basePath}/courts/${c.id}/edit`}
                        className="font-bold text-emerald-600 hover:underline dark:text-emerald-400"
                      >
                        Chỉnh sửa
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* 4. FACILITY PROFILE DETAILS & AMENITIES */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="rounded-2xl border border-slate-200/80 bg-white p-6 shadow-sm lg:col-span-2 dark:border-slate-800 dark:bg-slate-900">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-lg font-bold text-slate-900 dark:text-white">Thông tin liên hệ & Giới thiệu</h3>
            <Link href={`${basePath}/edit`} className="text-xs font-bold text-emerald-600 hover:underline">
              Sửa thông tin
            </Link>
          </div>
          <p className="text-sm leading-relaxed text-slate-600 dark:text-slate-300">
            {facility.description || 'Chưa cập nhật mô tả chi tiết cho cơ sở.'}
          </p>

          <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 border-t border-slate-100 pt-5 dark:border-slate-800 text-xs">
            <div className="flex items-start gap-2.5">
              <MapPin size={16} className="text-slate-400 shrink-0 mt-0.5" />
              <div>
                <span className="font-bold text-slate-700 dark:text-slate-200">Địa chỉ:</span>
                <p className="text-slate-500">{facility.addressLine}, {facility.ward}, {facility.province}</p>
              </div>
            </div>
            <div className="flex items-start gap-2.5">
              <Phone size={16} className="text-slate-400 shrink-0 mt-0.5" />
              <div>
                <span className="font-bold text-slate-700 dark:text-slate-200">Hotline:</span>
                <p className="text-slate-500">{facility.phone}</p>
              </div>
            </div>
            <div className="flex items-start gap-2.5">
              <Mail size={16} className="text-slate-400 shrink-0 mt-0.5" />
              <div>
                <span className="font-bold text-slate-700 dark:text-slate-200">Email cơ sở:</span>
                <p className="text-slate-500">{facility.contactEmail || 'Chưa cung cấp'}</p>
              </div>
            </div>
            <div className="flex items-start gap-2.5">
              <Clock size={16} className="text-slate-400 shrink-0 mt-0.5" />
              <div>
                <span className="font-bold text-slate-700 dark:text-slate-200">Múi giờ:</span>
                <p className="text-slate-500">{facility.timezone}</p>
              </div>
            </div>
          </div>

          {/* Amenities Pills */}
          <div className="mt-6 border-t border-slate-100 pt-5 dark:border-slate-800">
            <span className="text-xs font-bold text-slate-700 dark:text-slate-200 block mb-2">Tiện ích phục vụ người chơi:</span>
            <div className="flex flex-wrap gap-2">
              {facility.amenities.map((a) => (
                <span
                  key={a}
                  className="rounded-lg bg-emerald-50 px-3 py-1 text-xs font-bold text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300"
                >
                  ✓ {a}
                </span>
              ))}
              {facility.amenities.length === 0 && (
                <span className="text-xs text-slate-400">Chưa khai báo tiện ích nào.</span>
              )}
            </div>
          </div>
        </div>

        {/* Status Callout Card */}
        <div className="rounded-2xl border border-slate-200/80 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900 flex flex-col justify-between">
          <div>
            <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-emerald-600">
              <Sparkles size={14} />
              <span>Quy trình vận hành</span>
            </div>
            <h4 className="mt-2 text-base font-bold text-slate-900 dark:text-white">
              Hướng dẫn mở bán sân hiệu quả
            </h4>
            <ul className="mt-4 space-y-3 text-xs text-slate-500 dark:text-slate-400">
              <li className="flex items-start gap-2">
                <span className="flex size-5 shrink-0 items-center justify-center rounded-full bg-emerald-100 text-[10px] font-bold text-emerald-800">1</span>
                <span>Thêm tối thiểu 1 sân và cấu hình kích hoạt</span>
              </li>
              <li className="flex items-start gap-2">
                <span className="flex size-5 shrink-0 items-center justify-center rounded-full bg-emerald-100 text-[10px] font-bold text-emerald-800">2</span>
                <span>Cài đặt giờ mở cửa trong tuần và bảng giá theo giờ</span>
              </li>
              <li className="flex items-start gap-2">
                <span className="flex size-5 shrink-0 items-center justify-center rounded-full bg-emerald-100 text-[10px] font-bold text-emerald-800">3</span>
                <span>Tải lên ít nhất 2 ảnh thực tế để khách dễ hình dung</span>
              </li>
              <li className="flex items-start gap-2">
                <span className="flex size-5 shrink-0 items-center justify-center rounded-full bg-emerald-100 text-[10px] font-bold text-emerald-800">4</span>
                <span>Gửi thẩm định hồ sơ để kích hoạt trạng thái mở bán</span>
              </li>
            </ul>
          </div>
          <Link
            href={applicationId ? `/owner/application/${applicationId}` : `${basePath}/review`}
            className="mt-6 flex items-center justify-center gap-1.5 rounded-xl bg-slate-900 py-2.5 text-xs font-bold text-white shadow-sm hover:bg-slate-800 dark:bg-emerald-600 dark:hover:bg-emerald-500"
          >
            <span>Kiểm tra tiến độ thẩm định</span>
          </Link>
        </div>
      </div>

      {/* 5. PHOTOS & MEDIA MANAGEMENT */}
      <div className="rounded-2xl border border-slate-200/80 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <div className="flex flex-col justify-between gap-2 sm:flex-row sm:items-center">
          <div>
            <h3 className="text-lg font-bold text-slate-900 dark:text-white">Thư viện ảnh cơ sở</h3>
            <p className="text-xs text-slate-500">Định dạng JPG/PNG, tối đa 10 MB mỗi ảnh.</p>
          </div>
          {replacement && (
            <div className="inline-flex items-center gap-2 rounded-lg bg-amber-50 px-3 py-1.5 text-xs font-bold text-amber-800">
              <AlertCircle size={14} />
              <span>Đang ở chế độ thay thế ảnh</span>
            </div>
          )}
        </div>

        {/* Upload Form */}
        <form onSubmit={upload} className="mt-5 rounded-xl border border-dashed border-slate-300 bg-slate-50/50 p-5 dark:border-slate-700 dark:bg-slate-800/30">
          <div className="flex flex-wrap items-center gap-3">
            <input
              name="file"
              type="file"
              accept="image/png,image/jpeg"
              required
              disabled={busy}
              className="text-xs text-slate-600 file:mr-3 file:rounded-lg file:border-0 file:bg-slate-200 file:px-3 file:py-1.5 file:text-xs file:font-bold file:text-slate-800 hover:file:bg-slate-300"
            />
            <Button type="submit" disabled={busy} className="bg-emerald-600 hover:bg-emerald-500 text-xs font-bold">
              <Upload size={14} className="mr-1.5" />
              <span>{busy ? 'Đang xử lý…' : replacement ? 'Lưu ảnh thay thế' : 'Tải ảnh lên'}</span>
            </Button>
            {replacement && (
              <Button
                variant="outline"
                type="button"
                disabled={busy}
                onClick={() => setReplacement(null)}
                className="text-xs"
              >
                Hủy thay ảnh
              </Button>
            )}
          </div>
        </form>

        {/* Feedback Messages */}
        {message && (
          <p role="status" className="mt-4 rounded-lg bg-emerald-50 p-3 text-xs font-bold text-emerald-700">
            ✓ {message}
          </p>
        )}
        {(error || images.error) && (
          <p role="alert" className="mt-4 rounded-lg bg-red-50 p-3 text-xs font-bold text-red-700">
            ⚠ {error || images.error}
          </p>
        )}

        {/* Images Grid */}
        {images.loading && <p className="mt-6 text-xs text-slate-400">Đang tải thư viện ảnh…</p>}

        {!images.loading && images.data?.length === 0 && (
          <p className="mt-6 text-xs text-slate-400">Chưa có ảnh nào được tải lên cho cơ sở này.</p>
        )}

        <div className="mt-6 grid grid-cols-1 gap-5 sm:grid-cols-2 md:grid-cols-3">
          {images.data?.map((image) => (
            <figure
              key={image.id}
              className="group relative overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm transition hover:shadow-md dark:border-slate-700 dark:bg-slate-800"
            >
              <div className="relative h-44 w-full bg-slate-100 dark:bg-slate-900">
                <Image
                  unoptimized
                  src={image.url}
                  alt="Ảnh cơ sở"
                  width={400}
                  height={240}
                  className="h-44 w-full object-cover"
                />
              </div>
              <figcaption className="flex items-center justify-between border-t border-slate-100 p-3 dark:border-slate-700">
                <button
                  type="button"
                  onClick={() => setReplacement(image.id)}
                  disabled={busy}
                  className="inline-flex items-center gap-1 text-xs font-bold text-emerald-600 hover:text-emerald-700"
                >
                  <RefreshCw size={12} />
                  <span>Thay ảnh</span>
                </button>
                <button
                  type="button"
                  onClick={() => remove(image.id)}
                  disabled={busy}
                  className="inline-flex items-center gap-1 text-xs font-bold text-red-600 hover:text-red-700"
                >
                  <Trash2 size={12} />
                  <span>Xóa</span>
                </button>
              </figcaption>
            </figure>
          ))}
        </div>
      </div>
    </div>
  )
}

'use client'
import { Component, type ReactNode } from 'react'
import { Button } from '@/components/ui/button'

export class FeatureBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }
  static getDerivedStateFromError() {
    return { failed: true }
  }
  render() {
    if (!this.state.failed) return this.props.children
    return (
      <div role="alert" className="mx-auto max-w-xl space-y-4 rounded-xl border bg-white p-6">
        <p>Không thể mở màn hình. Vui lòng kiểm tra kết nối rồi tải lại.</p>
        <Button onClick={() => window.location.reload()}>Tải lại màn hình</Button>
      </div>
    )
  }
}

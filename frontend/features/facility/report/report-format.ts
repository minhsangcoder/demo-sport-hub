'use client'

export const hours = (minutes: string) =>
  (Number(minutes) / 60).toLocaleString('vi-VN', { maximumFractionDigits: 2 })

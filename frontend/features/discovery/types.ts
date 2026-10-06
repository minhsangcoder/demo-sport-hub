export type DiscoveryResult = {
  facility: {
    id: string
    name: string
    addressLine: string
    provinceCode: string | null
    province: string
    wardCode: string | null
    ward: string
    timezone: string
    amenities: string[]
    latitude: number | null
    longitude: number | null
  }
  courtId: string
  courtName: string
  sportCategoryId: string
  sportName: string
  date: string
  fromPrice: number
  currency: string
  availableSlots: number
  firstStartsAt: string
  firstEndsAt: string
  distanceKm: number | null
}

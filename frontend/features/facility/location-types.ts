export type FacilityLocation = { latitude: number; longitude: number } | null
export type LocationPoint = NonNullable<FacilityLocation>
export type LocationMap = {
  setPosition(point: LocationPoint): void
  sync(value: FacilityLocation, disabled: boolean): void
  focus(point: LocationPoint): void
  destroy(): void
}
export type LocationMapOptions = {
  container: HTMLDivElement
  signal: AbortSignal
  selected: FacilityLocation
  disabled(): boolean
  onChange(point: LocationPoint): void
}

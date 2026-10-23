export function parseCoordinatesLocally(
  text: string,
): { latitude: number; longitude: number } | null {
  const trimmed = text.trim()
  if (!trimmed) return null

  // 1. Raw coordinates "10.776889, 106.700806" or "10.776889 106.700806"
  const rawMatch = trimmed.match(/^(-?\d{1,2}(?:\.\d+)?)[,\s]+(-?\d{1,3}(?:\.\d+)?)$/)
  if (rawMatch) {
    const lat = parseFloat(rawMatch[1])
    const lng = parseFloat(rawMatch[2])
    if (lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) {
      return { latitude: Number(lat.toFixed(7)), longitude: Number(lng.toFixed(7)) }
    }
  }

  // 2. Google Maps @lat,lng
  const atMatch = trimmed.match(/@(-?\d+\.\d+),(-?\d+\.\d+)/)
  if (atMatch) {
    return {
      latitude: Number(parseFloat(atMatch[1]).toFixed(7)),
      longitude: Number(parseFloat(atMatch[2]).toFixed(7)),
    }
  }

  // 3. Google Maps data=!3dlat!4dlng
  const dataMatch = trimmed.match(/!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)/)
  if (dataMatch) {
    return {
      latitude: Number(parseFloat(dataMatch[1]).toFixed(7)),
      longitude: Number(parseFloat(dataMatch[2]).toFixed(7)),
    }
  }

  // 4. Query param ?q=lat,lng or &query=lat,lng or &ll=lat,lng or &destination=lat,lng
  const qMatch = trimmed.match(/[?&](?:q|query|ll|destination|center)=(-?\d+\.\d+),(-?\d+\.\d+)/)
  if (qMatch) {
    return {
      latitude: Number(parseFloat(qMatch[1]).toFixed(7)),
      longitude: Number(parseFloat(qMatch[2]).toFixed(7)),
    }
  }

  return null
}

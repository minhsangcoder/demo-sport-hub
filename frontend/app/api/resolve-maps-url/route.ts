import { type NextRequest, NextResponse } from 'next/server'

export const runtime = 'nodejs'
export const dynamic = 'force-dynamic'

export async function GET(request: NextRequest) {
  const urlParam = request.nextUrl.searchParams.get('url')
  if (!urlParam) {
    return NextResponse.json({ error: 'Thiếu tham số url.' }, { status: 400 })
  }

  try {
    const trimmed = urlParam.trim()
    const parsedUrl = new URL(trimmed)
    if (!['http:', 'https:'].includes(parsedUrl.protocol)) {
      return NextResponse.json({ error: 'URL không hợp lệ.' }, { status: 400 })
    }

    const res = await fetch(trimmed, {
      method: 'GET',
      redirect: 'follow',
      headers: {
        'User-Agent':
          'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        Accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
      },
    })

    const finalUrl = res.url || trimmed
    let lat: number | null = null
    let lng: number | null = null

    // Check @lat,lng in URL
    const atMatch = finalUrl.match(/@(-?\d+\.\d+),(-?\d+\.\d+)/)
    if (atMatch) {
      lat = parseFloat(atMatch[1])
      lng = parseFloat(atMatch[2])
    }

    // Check !3dlat!4dlng in URL
    if (lat === null || lng === null) {
      const dataMatch = finalUrl.match(/!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)/)
      if (dataMatch) {
        lat = parseFloat(dataMatch[1])
        lng = parseFloat(dataMatch[2])
      }
    }

    // Check query params in URL
    if (lat === null || lng === null) {
      const qMatch = finalUrl.match(/[?&](?:q|query|ll|destination|center)=(-?\d+\.\d+),(-?\d+\.\d+)/)
      if (qMatch) {
        lat = parseFloat(qMatch[1])
        lng = parseFloat(qMatch[2])
      }
    }

    // Check HTML body for staticmap or initialization state
    if (lat === null || lng === null) {
      const text = await res.text()
      const metaMatch = text.match(/center=(-?\d+\.\d+)%2C(-?\d+\.\d+)/)
      if (metaMatch) {
        lat = parseFloat(metaMatch[1])
        lng = parseFloat(metaMatch[2])
      } else {
        const stateMatch = text.match(/window\.APP_INITIALIZATION_STATE=\[\[\[[^,]+,[^,]+,(-?\d+\.\d+),(-?\d+\.\d+)\]/)
        if (stateMatch) {
          lat = parseFloat(stateMatch[1])
          lng = parseFloat(stateMatch[2])
        }
      }
    }

    if (lat !== null && lng !== null) {
      return NextResponse.json({
        latitude: Number(lat.toFixed(7)),
        longitude: Number(lng.toFixed(7)),
        finalUrl,
      })
    }

    return NextResponse.json(
      {
        error: 'Không trích xuất được tọa độ từ đường link này.',
        finalUrl,
      },
      { status: 422 }
    )
  } catch (err) {
    return NextResponse.json(
      {
        error: 'Không thể kết nối hoặc phân giải đường dẫn Google Maps này.',
      },
      { status: 500 }
    )
  }
}

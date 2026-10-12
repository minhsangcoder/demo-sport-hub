import type { NextRequest } from "next/server";
export const runtime = "nodejs";
export const dynamic = "force-dynamic";
const limit = 31 * 1024 * 1024;
const hopHeaders = [
  "connection",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailer",
  "transfer-encoding",
  "upgrade",
  "host",
  "content-length",
];
const failure = (status: number, message: string) =>
  Response.json({ data: null, message }, { status });
async function forward(
  request: NextRequest,
  { params }: { params: Promise<{ segments: string[] }> },
) {
  const { segments } = await params;
  // This browser-facing BFF accepts writes only from its own origin. Gateway CORS
  // applies to direct browser clients; server-to-server forwarding has no Origin.
  if (!["GET", "HEAD"].includes(request.method)) {
    const origin = request.headers.get("origin");
    if (request.headers.get("sec-fetch-site") === "cross-site")
      return failure(403, "Yêu cầu phải được gửi từ SportHub.");
    if (origin) {
      try {
        const source = new URL(origin);
        const protocol =
          request.headers.get("x-forwarded-proto")?.split(",")[0].trim() ??
          request.nextUrl.protocol.replace(":", "");
        if (
          source.host !== request.headers.get("host") ||
          source.protocol !== protocol + ":"
        )
          return failure(403, "Origin của yêu cầu không hợp lệ.");
      } catch {
        return failure(403, "Origin của yêu cầu không hợp lệ.");
      }
    }
  }
  if (
    segments.some(
      (p) => p === "." || p === ".." || p.includes("/") || p.includes("\\"),
    )
  )
    return failure(400, "Đường dẫn API không hợp lệ.");
  const gateway = process.env.GATEWAY_URL ?? "http://localhost:8080";
  const url = new URL(
    "/api/v1/" + segments.map(encodeURIComponent).join("/"),
    gateway,
  );
  url.search = request.nextUrl.search;
  const headers = new Headers(request.headers);
  for (const name of [...hopHeaders, "origin", "referer"]) headers.delete(name);
  let body: ArrayBuffer | undefined;
  if (!["GET", "HEAD"].includes(request.method) && request.body) {
    if (Number(request.headers.get("content-length")) > limit)
      return failure(413, "Dung lượng yêu cầu vượt quá 31 MB.");
    const reader = request.body.getReader(),
      chunks: Uint8Array[] = [];
    let total = 0;
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      total += value.byteLength;
      if (total > limit) {
        await reader.cancel();
        return failure(413, "Dung lượng yêu cầu vượt quá 31 MB.");
      }
      chunks.push(value);
    }
    const bytes = new Uint8Array(total);
    let offset = 0;
    for (const chunk of chunks) {
      bytes.set(chunk, offset);
      offset += chunk.length;
    }
    body = bytes.buffer;
  }
  try {
    const upstream = await fetch(url, {
      method: request.method,
      headers,
      body,
      redirect: "manual",
      cache: "no-store",
      signal: AbortSignal.timeout(30000),
    });
    const responseHeaders = new Headers(upstream.headers);
    // fetch decompresses the body. Signed media redirects and all Set-Cookie values
    // otherwise remain intact, including HttpOnly refresh-session cookies.
    for (const name of [
      ...hopHeaders,
      "content-encoding",
      "access-control-allow-origin",
      "access-control-allow-credentials",
    ])
      responseHeaders.delete(name);
    responseHeaders.set("cache-control", "no-store");
    return new Response(upstream.body, {
      status: upstream.status,
      headers: responseHeaders,
    });
  } catch {
    return failure(
      502,
      "Không kết nối được dịch vụ SportHub. Vui lòng thử lại.",
    );
  }
}
export {
  forward as GET,
  forward as HEAD,
  forward as POST,
  forward as PUT,
  forward as PATCH,
  forward as DELETE,
};

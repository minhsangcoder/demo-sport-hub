"use client";
import { useEffect, useRef, useState } from "react";
import { MapPin } from "lucide-react";
import { Button, Field, Notice } from "./ui";
import "leaflet/dist/leaflet.css";
export function LocationPicker({
  latitude,
  longitude,
  required = false,
}: {
  latitude?: number | null;
  longitude?: number | null;
  required?: boolean;
}) {
  const [lat, setLat] = useState(
      latitude === null || latitude === undefined ? "" : String(latitude),
    ),
    [lon, setLon] = useState(
      longitude === null || longitude === undefined ? "" : String(longitude),
    ),
    [open, setOpen] = useState(false),
    [error, setError] = useState("");
  const container = useRef<HTMLDivElement>(null),
    position = useRef({ lat, lon });
  useEffect(() => {
    position.current = { lat, lon };
  }, [lat, lon]);
  useEffect(() => {
    if (!open) return;
    let active = true,
      map: import("leaflet").Map | undefined;
    void import("leaflet")
      .then(({ default: L }) => {
        if (!active || !container.current) return;
        const p = position.current;
        map = L.map(container.current, { scrollWheelZoom: false }).setView(
          p.lat && p.lon ? [Number(p.lat), Number(p.lon)] : [16.0471, 108.2068],
          p.lat && p.lon ? 15 : 5,
        );
        L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
          attribution: "© OpenStreetMap contributors",
          referrerPolicy: "origin",
        }).addTo(map);
        const icon = L.divIcon({
          className: "map-pin",
          html: '<span style="display:block;width:20px;height:20px;background:#164b35;border:3px solid white;border-radius:50%"></span>',
          iconSize: [20, 20],
        });
        let marker: import("leaflet").Marker | undefined;
        if (p.lat && p.lon)
          marker = L.marker([Number(p.lat), Number(p.lon)], { icon }).addTo(
            map,
          );
        map.on("click", (event: import("leaflet").LeafletMouseEvent) => {
          setLat(event.latlng.lat.toFixed(7));
          setLon(event.latlng.lng.toFixed(7));
          marker?.remove();
          marker = L.marker(event.latlng, { icon }).addTo(map!);
        });
        map.on("tileerror", () => {
          if (active)
            setError(
              "Không tải được bản đồ. Bạn vẫn có thể nhập tọa độ trực tiếp.",
            );
        });
      })
      .catch(() => setError("Không mở được bản đồ. Hãy nhập tọa độ."));
    return () => {
      active = false;
      map?.remove();
    };
  }, [open]);
  return (
    <>
      <Field
        name="latitude"
        label="Vĩ độ"
        type="number"
        min={-90}
        max={90}
        step="any"
        value={lat}
        onChange={(e) => setLat(e.target.value)}
        required={required}
      />
      <Field
        name="longitude"
        label="Kinh độ"
        type="number"
        min={-180}
        max={180}
        step="any"
        value={lon}
        onChange={(e) => setLon(e.target.value)}
        required={required}
      />
      <div className="span-2">
        <Button
          type="button"
          variant="secondary"
          onClick={() => setOpen(!open)}
        >
          <MapPin size={15} />
          {open ? "Ẩn bản đồ" : "Chọn vị trí trên bản đồ"}
        </Button>
        {open && (
          <>
            <p className="small muted" style={{ margin: "12px 0" }}>
              Di chuyển bản đồ và nhấp vào vị trí cơ sở để điền tọa độ.
            </p>
            <div
              ref={container}
              className="map-container"
              style={{ height: 320 }}
              aria-label="Nhấp bản đồ để chọn vị trí cơ sở"
            />
          </>
        )}
        {error && <Notice tone="warning">{error}</Notice>}
      </div>
    </>
  );
}

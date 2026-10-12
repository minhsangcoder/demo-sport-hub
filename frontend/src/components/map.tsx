"use client";
import { useEffect, useRef, useState } from "react";
import type { DiscoveryItem } from "@/lib/domain";
import { Notice } from "./ui";
import "leaflet/dist/leaflet.css";
export function CourtMap({ items }: { items: DiscoveryItem[] }) {
  const container = useRef<HTMLDivElement>(null),
    [failed, setFailed] = useState(false);
  useEffect(() => {
    let active = true;
    let map: import("leaflet").Map | undefined;
    void import("leaflet")
      .then(({ default: L }) => {
        if (!active || !container.current) return;
        const points = items.filter(
          (x) => x.facility.latitude !== null && x.facility.longitude !== null,
        );
        if (!points.length) return;
        map = L.map(container.current, { scrollWheelZoom: false });
        const layer = L.tileLayer(
          "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
          {
            maxZoom: 19,
            referrerPolicy: "origin",
            attribution:
              '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
          },
        );
        layer.on("tileerror", () => {
          if (active) setFailed(true);
        });
        layer.addTo(map);
        const icon = L.divIcon({
          className: "map-pin",
          html: '<span style="display:block;width:20px;height:20px;background:#164b35;border:3px solid white;border-radius:50%;box-shadow:0 1px 6px #0003"></span>',
          iconSize: [20, 20],
        });
        const seen = new Set<string>();
        points.forEach((item) => {
          if (seen.has(item.facility.id)) return;
          seen.add(item.facility.id);
          const popup = document.createElement("div");
          const title = document.createElement("strong");
          title.textContent = item.facility.name;
          const address = document.createElement("p");
          address.textContent = item.facility.addressLine;
          const link = document.createElement("a");
          link.href = "/facilities/" + encodeURIComponent(item.facility.id);
          link.textContent = "Xem cơ sở →";
          popup.append(title, address, link);
          L.marker([item.facility.latitude!, item.facility.longitude!], {
            icon,
          })
            .addTo(map!)
            .bindPopup(popup);
        });
        map.fitBounds(
          L.latLngBounds(
            points.map(
              (x) =>
                [x.facility.latitude!, x.facility.longitude!] as [
                  number,
                  number,
                ],
            ),
          ),
          { padding: [45, 45], maxZoom: 14 },
        );
      })
      .catch(() => {
        if (active) setFailed(true);
      });
    return () => {
      active = false;
      map?.remove();
    };
  }, [items]);
  return (
    <>
      {failed && (
        <Notice tone="warning">
          Bản đồ đang gặp sự cố. Bạn vẫn có thể xem địa chỉ và đặt sân trong
          danh sách.
        </Notice>
      )}
      <div
        className="map-container"
        ref={container}
        aria-label="Bản đồ các cơ sở có tọa độ"
      />
    </>
  );
}

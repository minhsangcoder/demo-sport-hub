import Image from "next/image";
import type { ComponentProps } from "react";
// Facility media uses short-lived signed URLs; the gateway's existing image policy is unoptimized.
export function Photo({
  width = 1200,
  height = 800,
  alt,
  ...props
}: Omit<ComponentProps<typeof Image>, "width" | "height"> & {
  width?: number;
  height?: number;
}) {
  return (
    <Image {...props} alt={alt} width={width} height={height} unoptimized />
  );
}

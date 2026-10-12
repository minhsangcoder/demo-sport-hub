import type { Slot } from "./domain";
export function selectedInterval(slots: Slot[], selected: string[]) {
  const chosen = slots
    .filter((s) => selected.includes(s.startsAt))
    .sort((a, b) => a.startsAt.localeCompare(b.startsAt));
  if (
    !chosen.length ||
    chosen.length !== selected.length ||
    chosen.some((s) => s.state !== "AVAILABLE" || s.amount === null) ||
    chosen.some((s, i) => i > 0 && s.startsAt !== chosen[i - 1].endsAt)
  )
    return null;
  return {
    startsAt: chosen[0].startsAt,
    endsAt: chosen[chosen.length - 1].endsAt,
  };
}
export function safeNext(
  value: string | null,
  fallback = "/customer/bookings",
) {
  return value &&
    value.startsWith("/") &&
    !value.startsWith("//") &&
    !/[\\\r\n]/.test(value) &&
    !/^\/(login|register|verify)([/?]|$)/.test(value)
    ? value
    : fallback;
}

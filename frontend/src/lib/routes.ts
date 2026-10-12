// Public route grammar is separate from API routes. Unknown paths return an HTTP 404.
const id =
  "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
const facilityScreens = `(?:edit|courts(?:/new|/${id}/edit)?|schedule(?:/preview)?|pricing|maintenance|staff|documents|review|transfer-policy|reports)`;
const patterns = [
  "^/$",
  "^/(?:search|login|register|verify|forgot-password|reset-password)$",
  `^/facilities/${id}(?:/courts/${id})?$`,
  `^/transfers(?:/${id})?$`,
  "^/customer/(?:bookings|groups|groups/join|transfers|profile|profile/contact)$",
  `^/customer/bookings/${id}(?:/transfer)?$`,
  `^/customer/booking/holds/${id}$`,
  `^/customer/payments/${id}$`,
  `^/customer/groups/${id}(?:/invite|/split)?$`,
  `^/customer/transfers/${id}/edit$`,
  `^/customer/transfers/acquisitions/${id}$`,
  "^/owner(?:/apply|/application|/facilities|/facilities/new|/bookings)?$",
  `^/owner/application/${id}(?:/legal|/facility(?:/(?:edit|courts(?:/new|/${id}/edit)?|schedule(?:/preview)?|pricing|documents))?)?$`,
  `^/owner/facilities/${id}(?:/${facilityScreens})?$`,
  `^/owner/bookings/${id}$`,
  "^/staff(?:/bookings|/schedule|/counter-booking)?$",
  `^/staff/(?:bookings|counter-booking)/${id}$`,
  "^/admin(?:/accounts|/owner-applications|/facilities|/sports|/bookings|/bookings/reconciliation|/payments|/reports|/disputes)?$",
  `^/admin/(?:accounts|owner-applications|facilities|bookings|payments)/${id}$`,
].map((p) => new RegExp(p));
export function isFrontendRoute(path: string) {
  return patterns.some((p) => p.test(path));
}

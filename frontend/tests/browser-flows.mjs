// Isolated UI contract tests: every /api/v1 request is intercepted.
// These fixtures never create bookings, payments, files or account changes in a database.
import assert from "node:assert/strict";
import { createRequire } from "node:module";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
const { chromium } = createRequire(import.meta.url)(
  process.env.PLAYWRIGHT_MODULE ?? "playwright",
);
const origin = process.env.FRONTEND_QA_URL ?? "http://localhost:3000";
const ids = Object.fromEntries(
  [
    "facility",
    "court",
    "category",
    "hold",
    "booking",
    "group",
    "member",
    "otherMember",
    "listing",
    "acquisition",
    "payment",
    "user",
    "otherUser",
    "application",
    "document",
  ].map((key, i) => [
    key,
    `00000000-0000-4000-8000-${String(i + 1).padStart(12, "0")}`,
  ]),
);
const day = new Date(Date.now() + 2 * 86400000).toISOString().slice(0, 10),
  future = new Date(Date.now() + 86400000).toISOString();
const slots = Array.from({ length: 5 }, (_, i) => ({
  startsAt: `${day}T${String(i + 6).padStart(2, "0")}:00:00+07:00`,
  endsAt: `${day}T${String(i + 7).padStart(2, "0")}:00:00+07:00`,
  state: i < 2 ? "AVAILABLE" : "BLOCKED",
  reason:
    i === 2 ? "HELD" : i === 3 ? "BOOKED" : i === 4 ? "MAINTENANCE" : null,
  amount: 100000,
  currency: "VND",
  ruleId: null,
  ruleVersion: 1,
  ruleLabel: "Giá kiểm thử",
}));
const facility = {
  id: ids.facility,
  ownerId: ids.user,
  name: "Cơ sở kiểm thử",
  phone: "+84901234567",
  contactEmail: "fixture@example.com",
  addressLine: "Địa chỉ kiểm thử",
  province: "Đà Nẵng",
  district: "Sơn Trà",
  ward: "An Hải",
  description: "Fixture hợp đồng API",
  timezone: "Asia/Ho_Chi_Minh",
  latitude: 16.05,
  longitude: 108.2,
  status: "ACTIVE",
  amenities: [],
  version: 1,
};
const court = {
  id: ids.court,
  facilityId: ids.facility,
  sportCategoryId: ids.category,
  name: "Sân kiểm thử",
  code: "FIXTURE-01",
  description: null,
  enabled: true,
  version: 1,
};
const category = { id: ids.category, name: "Tennis", active: true };
const quote = {
  facilityId: ids.facility,
  courtId: ids.court,
  startsAt: slots[0].startsAt,
  endsAt: slots[1].endsAt,
  amount: 200000,
  currency: "VND",
  timezone: facility.timezone,
  segments: slots.slice(0, 2),
};
const booking = {
  id: ids.booking,
  facilityId: ids.facility,
  courtId: ids.court,
  customerId: ids.user,
  currentHolderId: ids.user,
  createdBy: ids.user,
  startsAt: quote.startsAt,
  endsAt: quote.endsAt,
  status: "PENDING",
  source: "ONLINE",
  guestName: null,
  guestPhone: null,
  amount: quote.amount,
  currency: "VND",
  priceSnapshot: quote,
  holdExpiresAt: future,
  paymentId: null,
  paidAt: null,
  checkedInAt: null,
  completedAt: null,
  version: 1,
};
const hold = {
  id: ids.hold,
  facilityId: ids.facility,
  courtId: ids.court,
  holderId: ids.user,
  startsAt: quote.startsAt,
  endsAt: quote.endsAt,
  expiresAt: future,
  state: "HOLD",
  quote,
};
const member = (id, userId, displayName) => ({
  id,
  userId,
  displayName,
  active: true,
  amountDue: 0,
  amountPaid: 0,
  paymentState: "UNPAID",
  paymentRequestedAt: null,
  lastReminderAt: null,
  nextReminderAt: null,
  reminderEligible: false,
  reminderBlockedReason: "ALLOCATION_NOT_LOCKED",
});
const groupInitial = {
  id: ids.group,
  ownerId: ids.user,
  name: "Nhóm kiểm thử",
  state: "GROUP_PENDING",
  deadline: future,
  maxMembers: 4,
  allocationsLocked: false,
  booking: { ...booking, id: ids.group },
  members: [
    member(ids.member, ids.user, "Người tổ chức"),
    member(ids.otherMember, ids.otherUser, "Đồng đội"),
  ],
  totalPaid: 0,
};
const listing = {
  id: ids.listing,
  facilityId: ids.facility,
  courtId: ids.court,
  venue: {
    facilityName: facility.name,
    courtName: court.name,
    sportCategoryId: ids.category,
    address: facility.addressLine,
    province: facility.province,
    district: facility.district,
    timezone: facility.timezone,
    amenities: [],
  },
  originalAmount: 200000,
  price: 150000,
  currency: "VND",
  startsAt: quote.startsAt,
  endsAt: quote.endsAt,
  deadline: future,
  state: "ACTIVE",
  own: false,
  available: true,
  version: 1,
};
const acquisitionInitial = {
  id: ids.acquisition,
  listingId: ids.listing,
  bookingId: ids.booking,
  buyerId: ids.user,
  amount: listing.price,
  currency: "VND",
  expiresAt: future,
  state: "PENDING",
  paymentId: null,
};
const paymentInitial = {
  id: ids.payment,
  bookingId: ids.booking,
  payerId: ids.user,
  memberId: null,
  acquisitionId: null,
  purpose: "BOOKING",
  amount: 200000,
  currency: "VND",
  provider: "DEMO",
  providerReference: "FIXTURE-REF",
  status: "PENDING",
  expiresAt: future,
  paidAt: null,
};
const browser = await chromium.launch({
  headless: true,
  ...(process.env.PLAYWRIGHT_CHANNEL
    ? { channel: process.env.PLAYWRIGHT_CHANNEL }
    : {}),
});
const output = path.join(
  os.tmpdir(),
  "sporthub-frontend-qa",
  "browser-fixtures",
);
fs.mkdirSync(output, { recursive: true });
let assertions = 0;
async function workspace(role, work) {
  const context = await browser.newContext({
      viewport: { width: 1280, height: 900 },
    }),
    page = await context.newPage(),
    writes = [],
    errors = [];
  let group = structuredClone(groupInitial),
    payment = structuredClone(paymentInitial),
    acquisition = structuredClone(acquisitionInitial),
    images = [],
    holdAttempts = 0;
  const user = {
    id: ids.user,
    fullName: "Tài khoản kiểm thử",
    email: "fixture@example.com",
    phone: null,
    status: "ACTIVE",
    emailVerified: true,
    phoneVerified: false,
    roles:
      role === "GUEST"
        ? []
        : role === "CUSTOMER"
          ? ["CUSTOMER"]
          : [role, "CUSTOMER"],
  };
  page.on("pageerror", (e) => errors.push(e.message));
  await context.route("**/api/v1/**", async (route) => {
    const req = route.request(),
      url = new URL(req.url()),
      p = url.pathname.replace("/api/v1", ""),
      method = req.method();
    const send = (data, status = 200) =>
      route.fulfill({
        status,
        contentType: "application/json",
        body: JSON.stringify({ data, message: "Success" }),
      });
    if (method !== "GET") {
      let body = req.headers()["content-type"]?.includes("multipart")
        ? req.postDataBuffer()
        : req.postData();
      try {
        body = JSON.parse(body);
      } catch {}
      writes.push({ path: p, method, body, headers: req.headers() });
    }
    if (p === "/auth/refresh")
      return role === "GUEST"
        ? route.fulfill({
            status: 401,
            contentType: "application/json",
            body: JSON.stringify({ message: "Not signed in" }),
          })
        : send({
            accessToken: "isolated-fixture-token",
            tokenType: "Bearer",
            expiresIn: 900,
            user,
          });
    if (p === "/auth/register")
      return send({
        userId: ids.user,
        accountStatus: "PENDING_VERIFICATION",
        verificationChallengeId: ids.document,
        verificationExpiresAt: future,
        ownerApplication: { id: ids.application, state: "PENDING_APPROVAL" },
      });
    if (p === "/sport-categories") return send([category]);
    if (p === "/users/me") return send(user);
    if (p === "/users/me/access")
      return send({
        facilityBindings: [
          {
            facilityId: ids.facility,
            permissions:
              role === "STAFF"
                ? ["SCHEDULE_READ"]
                : [
                    "BOOKING_READ",
                    "BOOKING_CREATE_COUNTER",
                    "BOOKING_PAYMENT_COUNTER",
                    "BOOKING_CHECK_IN",
                    "SCHEDULE_READ",
                  ],
          },
        ],
      });
    if (p === "/owner/facilities" || p === "/facilities")
      return send([facility]);
    if (
      p === `/owner/facilities/${ids.facility}` ||
      p === `/facilities/${ids.facility}`
    )
      return send(facility);
    if (p.endsWith("/transfer-policy"))
      return send({ configured: true, enabled: true, minLeadSeconds: 3600 });
    if (p.endsWith("/courts")) return send([court]);
    if (p === `/facilities/courts/${ids.court}/context`)
      return send({ facility, court, maintenance: [] });
    if (p.endsWith("/images")) {
      if (method === "POST") {
        images = [
          {
            id: ids.document,
            courtId: null,
            url:
              "data:image/svg+xml," +
              encodeURIComponent(
                '<svg xmlns="http://www.w3.org/2000/svg" width="200" height="120"><rect width="200" height="120" fill="#164b35"/></svg>',
              ),
            contentType: "image/png",
            sizeBytes: 68,
          },
        ];
        return send(images[0], 201);
      }
      return send(images);
    }
    if (p === "/bookings/availability" || p.endsWith("/preview"))
      return send({
        facilityId: ids.facility,
        courtId: ids.court,
        date: day,
        timezone: facility.timezone,
        slots,
      });
    if (p === "/schedules/public/quote") {
      const input = req.postDataJSON();
      return send({
        ...quote,
        startsAt: input.startsAt,
        endsAt: input.endsAt,
        segments: slots.filter(
          (s) => s.startsAt >= input.startsAt && s.endsAt <= input.endsAt,
        ),
        amount:
          slots.filter(
            (s) => s.startsAt >= input.startsAt && s.endsAt <= input.endsAt,
          ).length * 100000,
      });
    }
    if (p === "/bookings/holds") {
      holdAttempts++;
      if (holdAttempts === 1)
        return route.fulfill({
          status: 503,
          contentType: "application/json",
          body: JSON.stringify({ message: "Lỗi kết nối kiểm thử" }),
        });
      return send(hold, 201);
    }
    if (p === `/bookings/holds/${ids.hold}`) return send(hold);
    if (p === "/groups" && method === "POST") return send(group, 201);
    if (p === `/groups/${ids.group}`) return send(group);
    if (p === `/groups/${ids.group}/allocations`) {
      group.allocationsLocked = true;
      group.members.forEach((m) => {
        m.amountDue = 100000;
        m.paymentState = "WAITING_FOR_PAYMENT";
        m.reminderEligible = m.id !== ids.member;
      });
      return send(group);
    }
    if (p === `/groups/${ids.group}/invite`)
      return send({
        code: "fixture-code",
        path: "/customer/groups/join?code=fixture-code",
        qrSvg:
          '<svg xmlns="http://www.w3.org/2000/svg" width="120" height="120"><rect width="120" height="120" fill="black"/></svg>',
        expiresAt: future,
      });
    if (p === "/payments" && method === "POST") {
      const input = req.postDataJSON();
      payment = {
        ...paymentInitial,
        ...input,
        purpose: input.acquisitionId
          ? "TRANSFER"
          : input.memberId
            ? "GROUP_CONTRIBUTION"
            : "BOOKING",
        amount: input.acquisitionId ? 150000 : input.memberId ? 100000 : 200000,
      };
      return send(payment, 201);
    }
    if (p === `/payments/${ids.payment}`) return send(payment);
    if (p === `/payments/${ids.payment}/demo/complete`) {
      payment.status = req.postDataJSON().outcome;
      payment.paidAt = new Date().toISOString();
      if (payment.acquisitionId) acquisition.state = "PENDING_HANDOFF";
      return send(payment);
    }
    if (p === `/transfers/${ids.listing}`) return send(listing);
    if (p === `/transfers/${ids.listing}/acquisitions`)
      return send(acquisition, 201);
    if (p === `/transfers/acquisitions/${ids.acquisition}`)
      return send(acquisition);
    if (p.endsWith("/hours"))
      return send([
        {
          id: ids.document,
          facilityId: ids.facility,
          courtId: null,
          dayOfWeek: 1,
          opensAt: "06:00:00",
          closesAt: "22:00:00",
          slotMinutes: 60,
          version: 1,
        },
      ]);
    if (p.endsWith("/exceptions") || p.endsWith("/pricing")) return send([]);
    if (p.startsWith("/bookings/facility")) {
      assert.notEqual(
        role,
        "STAFF",
        "Staff with only SCHEDULE_READ must not fetch private booking lists",
      );
      return send([]);
    }
    if (p === `/admin/accounts/${ids.otherUser}`) {
      return send({
        account: {
          ...user,
          id: ids.otherUser,
          roles: ["CUSTOMER"],
          createdAt: new Date().toISOString(),
          lastLoginAt: null,
          lockReason: null,
          lockedUntil: null,
        },
        sessions: [],
        audit: [],
      });
    }
    if (p === `/admin/accounts/${ids.otherUser}/lock`)
      return send({ ...user, id: ids.otherUser, status: "LOCKED" });
    // Unknown endpoints fail the test rather than leaking a request to the real gateway.
    errors.push(`Unexpected fixture API: ${method} ${p}`);
    return route.fulfill({
      status: 501,
      contentType: "application/json",
      body: JSON.stringify({ message: "Unknown test contract" }),
    });
  });
  try {
    await work({ page, writes });
    assert.deepEqual(errors, []);
    assertions++;
  } finally {
    await context.close();
  }
}
const navigate = async (page, url) => {
  await page.goto(origin + url, { waitUntil: "networkidle" });
  await page.locator("main h1, main h3").first().waitFor();
};
try {
  await workspace("CUSTOMER", async ({ page, writes }) => {
    await navigate(
      page,
      `/facilities/${ids.facility}/courts/${ids.court}?date=${day}`,
    );
    await page.locator('.slot[data-reason="HELD"]').waitFor();
    assert.equal(
      await page.locator('.slot[data-reason="HELD"]').isDisabled(),
      true,
    );
    assert.match(
      await page.locator('.slot[data-reason="HELD"]').innerText(),
      /Đang giữ/,
    );
    assert.match(
      await page.locator('.slot[data-reason="MAINTENANCE"]').innerText(),
      /Bảo trì/,
    );
    await page.locator('.slot[data-state="AVAILABLE"]').nth(0).click();
    await page.locator('.slot[data-state="AVAILABLE"]').nth(1).click();
    const holdButton = page.getByRole("button", { name: "Tiếp tục đặt sân" });
    await holdButton.waitFor();
    await holdButton.click();
    await page.getByText("Lỗi kết nối kiểm thử").waitFor();
    await holdButton.click();
    await page.waitForURL("**/customer/booking/holds/*");
    const held = writes.filter((x) => x.path === "/bookings/holds");
    assert.equal(held.length, 2);
    assert.equal(
      held[0].headers["idempotency-key"],
      held[1].headers["idempotency-key"],
    );
    assert.equal(held[1].body.expectedAmount, 200000);
    assert.equal(held[1].body.endsAt, quote.endsAt);
    await page
      .getByRole("button", { name: "Đặt sân theo nhóm", exact: true })
      .click();
    await page.locator('[name="name"]').fill("Nhóm kiểm thử");
    await page.getByRole("button", { name: "Xác nhận tạo nhóm" }).click();
    await page.waitForURL("**/customer/groups/*");
    assert.equal(
      writes.find((x) => x.path === "/groups").body.holdId,
      ids.hold,
    );
    await navigate(page, `/customer/groups/${ids.group}/split`);
    await page.getByRole("button", { name: "Chia tùy chỉnh" }).click();
    await page.getByLabel("Người tổ chức (VND)").fill("90000");
    await page.getByLabel("Đồng đội (VND)").fill("90000");
    await page.getByRole("button", { name: "Xác nhận phân chia" }).click();
    await page
      .getByText("Tổng chi phí phân bổ phải bằng giá trị booking.")
      .first()
      .waitFor();
    assert.equal(
      writes.filter((x) => x.path.endsWith("/allocations")).length,
      0,
    );
    await page.getByLabel("Người tổ chức (VND)").fill("100000");
    await page.getByLabel("Đồng đội (VND)").fill("100000");
    await page.getByRole("button", { name: "Xác nhận phân chia" }).click();
    await page.waitForURL((u) => u.pathname.endsWith(ids.group));
    assert.equal(
      writes.find((x) => x.path.endsWith("/allocations")).body.mode,
      "CUSTOM",
    );
    await page.getByRole("button", { name: "Thanh toán phần của tôi" }).click();
    await page.waitForURL("**/customer/payments/*");
    assert.equal(
      writes.find((x) => x.path === "/payments").body.memberId,
      ids.member,
    );
    await page
      .getByRole("button", { name: "Xác nhận thanh toán demo" })
      .click();
    await page.getByText("Thanh toán đã được ghi nhận.").waitFor();
    assert.match(
      await page.locator("main").innerText(),
      /không chuyển tiền thật/i,
    );
  });
  await workspace("CUSTOMER", async ({ page, writes }) => {
    await navigate(page, `/transfers/${ids.listing}`);
    await page.getByRole("button", { name: "Nhận lịch chơi này" }).click();
    await page.waitForURL("**/customer/transfers/acquisitions/*");
    await page.getByRole("button", { name: "Tiếp tục thanh toán" }).click();
    await page.waitForURL("**/customer/payments/*");
    assert.equal(
      writes.find((x) => x.path === "/payments").body.acquisitionId,
      ids.acquisition,
    );
    await page
      .getByRole("button", { name: "Xác nhận thanh toán demo" })
      .click();
    await page.getByText("Thanh toán đã được ghi nhận.").waitFor();
    await navigate(page, `/customer/transfers/acquisitions/${ids.acquisition}`);
    await page.getByText("Đang bàn giao", { exact: true }).waitFor();
    assert.equal(
      await page.getByRole("link", { name: "Mở vé chơi của bạn" }).count(),
      0,
    );
  });
  await workspace("OWNER", async ({ page, writes }) => {
    await navigate(page, `/owner/facilities/${ids.facility}`);
    await page
      .locator('input[type="file"]')
      .setInputFiles({
        name: "fixture.png",
        mimeType: "image/png",
        buffer: Buffer.from(
          "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aUKUAAAAASUVORK5CYII=",
          "base64",
        ),
      });
    await page.getByRole("button", { name: "Tải ảnh lên" }).click();
    await page.getByText("Đã tải ảnh lên cơ sở.").first().waitFor();
    const upload = writes.find((x) => x.path.endsWith("/images"));
    assert.equal(upload.path, `/owner/facilities/${ids.facility}/images`);
    assert.match(
      upload.headers["content-type"],
      /multipart\/form-data; boundary=/,
    );
    assert.ok(!upload.body.includes('name="courtId"'));
    await navigate(page, `/owner/facilities/${ids.facility}/schedule`);
    await page.getByRole("button", { name: "Lưu giờ hoạt động" }).click();
    await page
      .getByRole("dialog")
      .getByRole("button", { name: "Đóng", exact: true })
      .click();
    assert.equal(await page.getByRole("dialog").isVisible(), false);
    await page.getByRole("button", { name: "Lưu giờ hoạt động" }).click();
    await page
      .getByRole("button", { name: "Áp dụng cấu hình", exact: true })
      .click();
    await page.getByRole("dialog").waitFor({ state: "hidden" });
    const saved = writes.find(
      (x) => x.method === "PUT" && x.path.endsWith("/hours"),
    );
    assert.equal(saved.body.courtId, null);
    assert.equal(saved.body.intervals[0].slotMinutes, 60);
    await page.setViewportSize({ width: 360, height: 800 });
    assert.equal(
      await page.evaluate(
        () => document.documentElement.scrollWidth > innerWidth,
      ),
      false,
    );
    await page.screenshot({
      path: path.join(output, "schedule-mobile.png"),
      fullPage: true,
    });
  });
  await workspace("STAFF", async ({ page, writes }) => {
    await navigate(page, "/staff/schedule");
    await page.getByText("Bạn được xem lịch trống.").waitFor();
    assert.equal(writes.length, 1);
    await page.locator(".slot").first().waitFor();
    await navigate(page, "/staff/counter-booking");
    await page.getByText("Chưa có cơ sở được phân quyền").waitFor();
  });
  await workspace("ADMIN", async ({ page, writes }) => {
    await navigate(page, `/admin/accounts/${ids.otherUser}`);
    await page
      .getByRole("button", { name: "Khóa tài khoản", exact: true })
      .click();
    await page
      .getByRole("dialog")
      .locator('[name="duration"]')
      .selectOption("THIRTY_DAYS");
    await page
      .getByRole("dialog")
      .locator('[name="reason"]')
      .fill("Lý do khóa tài khoản kiểm thử");
    await page.getByRole("button", { name: "Xác nhận thay đổi" }).click();
    await page.getByText("Đã cập nhật tài khoản.").first().waitFor();
    const lock = writes.find((x) => x.path.endsWith("/lock"));
    assert.equal(lock.body.duration, "THIRTY_DAYS");
    assert.match(lock.body.reason, /kiểm thử/);
  });
  await workspace("GUEST", async ({ page, writes }) => {
    await navigate(page, "/customer/groups/join?code=fixture-code");
    await page.getByRole("button", { name: "Đăng nhập để tiếp tục" }).click();
    await page.waitForURL("**/login?next=*");
    assert.equal(
      new URL(page.url()).searchParams.get("next"),
      "/customer/groups/join?code=fixture-code",
    );
    assert.equal(writes.length, 1);
  });
  await workspace("GUEST", async ({ page, writes }) => {
    await navigate(page, "/register");
    await page.getByText("Tôi muốn đăng ký trở thành chủ sân").click();
    const fields = {
      fullName: "Người đăng ký kiểm thử",
      identifier: "new-fixture@example.com",
      password: "Fixture-password-123",
      confirmPassword: "Fixture-password-123",
      representativeName: "Người đại diện",
      identityNumber: "012345678901",
      businessName: "Cơ sở fixture",
      bankName: "Ngân hàng fixture",
      bankAccountHolder: "Nguoi dang ky",
      bankAccountNumber: "1234567890",
      facilityName: "Cơ sở đầu tiên",
      facilityPhone: "+84901234567",
      addressLine: "Địa chỉ fixture",
      province: "Đà Nẵng",
      district: "Sơn Trà",
      ward: "An Hải",
      latitude: "16.05",
      longitude: "108.2",
      courtCode: "NEW-01",
      courtName: "Sân đầu tiên",
      pricePerSlot: "100000",
    };
    for (const [key, value] of Object.entries(fields))
      await page.locator(`[name="${key}"]`).fill(value);
    await page.locator('[name="sportCategoryId"]').selectOption(ids.category);
    for (const name of [
      "identityDocument",
      "locationDocument",
      "facilityImage",
    ])
      await page
        .locator(`[name="${name}"]`)
        .setInputFiles({
          name: "fixture.png",
          mimeType: "image/png",
          buffer: Buffer.from(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aUKUAAAAASUVORK5CYII=",
            "base64",
          ),
        });
    await page
      .getByRole("button", { name: "Tạo tài khoản", exact: true })
      .click();
    await page.waitForURL("**/verify?*");
    const signup = writes.find((x) => x.path === "/auth/register");
    assert.match(signup.headers["content-type"], /multipart/);
    const parsed = await new Request("http://fixture", {
      method: "POST",
      headers: { "Content-Type": signup.headers["content-type"] },
      body: Buffer.from(signup.body, "utf8"),
    }).formData();
    const data = JSON.parse(await parsed.get("request").text());
    assert.equal(data.applyAsOwner, true);
    assert.equal(data.ownerApplication.setup.days.length, 7);
    assert.equal(data.ownerApplication.facility.name, "Cơ sở đầu tiên");
    assert.equal(parsed.get("identityDocument").name, "fixture.png");
    assert.ok(signup.headers["idempotency-key"]);
  });
  console.log(
    JSON.stringify({
      fixtureWorkspaces: assertions,
      status: "PASS",
      businessWritesToRealBackend: 0,
      output,
    }),
  );
} finally {
  await browser.close();
}

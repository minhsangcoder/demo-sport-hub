"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState, type ReactNode } from "react";
import {
  Activity,
  ArrowUpRight,
  Building2,
  CalendarDays,
  ChartNoAxesCombined,
  ChevronDown,
  Compass,
  ContactRound,
  LayoutDashboard,
  LogOut,
  Menu,
  Settings2,
  ShieldCheck,
  Ticket,
  Users,
  Wallet,
  X,
} from "lucide-react";
import { authApi, type Role } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { useResource } from "@/lib/hooks";
import type { Facility } from "@/lib/domain";
import { Button, Empty, LinkButton, Notice } from "./ui";
export function Brand({ light = false }: { light?: boolean }) {
  return (
    <Link
      href="/"
      className={`brand ${light ? "brand-light" : ""}`}
      aria-label="SportHub — Trang chủ"
    >
      <span className="brand-mark">
        <i />
        <i />
        <i />
      </span>
      sporthub<span className="brand-period">.</span>
    </Link>
  );
}
export function Guard({
  children,
  roles,
}: {
  children: ReactNode;
  roles?: Role[];
}) {
  const { user, ready } = useAuth(),
    router = useRouter();
  if (!ready)
    return (
      <div className="loading-state" role="status">
        Đang kiểm tra phiên đăng nhập…
      </div>
    );
  if (!user)
    return (
      <Empty
        title="Một tài khoản, mọi cuộc chơi."
        description="Đăng nhập để đặt sân và tiếp tục theo dõi lịch chơi của bạn."
        action={
          <Button
            onClick={() =>
              router.push(
                `/login?next=${encodeURIComponent(window.location.pathname + window.location.search)}`,
              )
            }
          >
            Đăng nhập để tiếp tục
          </Button>
        }
      />
    );
  if (roles && !roles.some((x) => user.roles.includes(x)))
    return (
      <Notice tone="error">
        Tài khoản của bạn chưa được cấp quyền truy cập khu vực này.{" "}
        <Link href="/">Về trang chủ</Link>
      </Notice>
    );
  return <>{children}</>;
}
export function Shell({ children }: { children: ReactNode }) {
  const path = usePathname();
  if (/^\/(owner|staff|admin)(\/|$)/.test(path))
    return <ManagementShell>{children}</ManagementShell>;
  return <PublicShell>{children}</PublicShell>;
}
function UserMenu() {
  const { user } = useAuth();
  const [open, setOpen] = useState(false),
    [error, setError] = useState(""),
    router = useRouter();
  if (!user)
    return (
      <div className="header-auth">
        <Link className="text-link" href="/login">
          Đăng nhập
        </Link>
        <LinkButton href="/register" variant="dark">
          Bắt đầu ngay <ArrowUpRight size={15} />
        </LinkButton>
      </div>
    );
  return (
    <div className="user-menu">
      <button
        className="user-trigger"
        onClick={() => setOpen(!open)}
        aria-expanded={open}
      >
        <span className="avatar">{user.fullName.slice(0, 1)}</span>
        <span>{user.fullName.split(" ").slice(-2).join(" ")}</span>
        <ChevronDown size={14} />
      </button>
      {open && (
        <>
          <button
            className="menu-dismiss"
            aria-label="Đóng menu"
            onClick={() => setOpen(false)}
          />
          <div className="dropdown">
            <Link onClick={() => setOpen(false)} href="/customer/bookings">
              <Ticket size={17} />
              Lịch đặt của tôi
            </Link>
            <Link onClick={() => setOpen(false)} href="/customer/profile">
              <ContactRound size={17} />
              Tài khoản
            </Link>
            {(["OWNER", "STAFF", "ADMIN"] as Role[])
              .filter((x) => user.roles.includes(x))
              .map((role) => (
                <Link
                  onClick={() => setOpen(false)}
                  key={role}
                  href={`/${role.toLowerCase()}`}
                >
                  <LayoutDashboard size={17} />
                  {role === "OWNER"
                    ? "Quản lý cơ sở"
                    : role === "STAFF"
                      ? "Vận hành sân"
                      : "Quản trị nền tảng"}
                </Link>
              ))}
            <button
              onClick={() => {
                void authApi
                  .logout()
                  .then(() => {
                    setOpen(false);
                    router.push("/");
                  })
                  .catch((e) => setError(e.message));
              }}
            >
              <LogOut size={17} />
              Đăng xuất
            </button>
            {error && <p role="alert">{error}</p>}
          </div>
        </>
      )}
    </div>
  );
}
function PublicShell({ children }: { children: ReactNode }) {
  const path = usePathname(),
    [open, setOpen] = useState(false);
  const auth = [
    "/login",
    "/register",
    "/forgot-password",
    "/reset-password",
    "/verify",
  ].includes(path);
  return (
    <div className="public-shell">
      <a className="skip-link" href="#content">
        Đến nội dung chính
      </a>
      <header className="public-header">
        <div className="header-inner">
          <Brand />
          <nav
            className={`public-nav ${open ? "is-open" : ""}`}
            aria-label="Điều hướng chính"
          >
            <Link
              onClick={() => setOpen(false)}
              className={path === "/search" ? "active" : ""}
              href="/search"
            >
              Khám phá sân
            </Link>
            <Link
              onClick={() => setOpen(false)}
              className={path.startsWith("/transfers") ? "active" : ""}
              href="/transfers"
            >
              Chuyển nhượng
            </Link>
            <Link onClick={() => setOpen(false)} href="/customer/groups">
              Chơi cùng nhóm
            </Link>
            <Link onClick={() => setOpen(false)} href="/owner/apply">
              Dành cho chủ sân <ArrowUpRight size={13} />
            </Link>
          </nav>
          <UserMenu />
          <button
            className="icon-btn mobile-menu"
            aria-label={open ? "Đóng điều hướng" : "Mở điều hướng"}
            aria-expanded={open}
            onClick={() => setOpen(!open)}
          >
            {open ? <X /> : <Menu />}
          </button>
        </div>
      </header>
      {path.startsWith("/customer/") && (
        <nav className="customer-nav" aria-label="Không gian cá nhân">
          <Link
            href="/customer/bookings"
            className={path.includes("/booking") ? "active" : ""}
          >
            <Ticket size={17} />
            Lịch của tôi
          </Link>
          <Link
            href="/customer/groups"
            className={path.includes("/groups") ? "active" : ""}
          >
            <Users size={17} />
            Nhóm chơi
          </Link>
          <Link
            href="/customer/transfers"
            className={path.includes("/transfers") ? "active" : ""}
          >
            <Activity size={17} />
            Chuyển nhượng
          </Link>
          <Link
            href="/customer/profile"
            className={path.includes("/profile") ? "active" : ""}
          >
            <ContactRound size={17} />
            Tài khoản
          </Link>
        </nav>
      )}
      <main id="content" className={auth ? "auth-main" : ""}>
        {children}
      </main>
      {!auth && (
        <footer className="footer">
          <div>
            <Brand />
            <p>
              Thêm vận động. Thêm kết nối.
              <br />
              Tìm cuộc chơi tiếp theo của bạn.
            </p>
          </div>
          <div>
            <span>KHÁM PHÁ</span>
            <Link href="/search">Tìm sân thể thao</Link>
            <Link href="/transfers">Chợ chuyển nhượng</Link>
            <Link href="/customer/groups">Đặt sân cùng nhóm</Link>
          </div>
          <div>
            <span>SPORTHUB</span>
            <Link href="/owner/apply">Trở thành đối tác</Link>
            <Link href="/customer/profile">Tài khoản của bạn</Link>
            <Link href="/login">Đăng nhập quản lý</Link>
          </div>
          <div className="footer-bottom">
            <span>© {new Date().getFullYear()} SportHub</span>
            <span>Đến sân. Vào cuộc.</span>
          </div>
        </footer>
      )}
    </div>
  );
}
function ManagementShell({ children }: { children: ReactNode }) {
  const path = usePathname(),
    router = useRouter(),
    [open, setOpen] = useState(false),
    { user, ready } = useAuth();
  const kind = path.split("/")[1],
    isOwner = kind === "owner",
    isStaff = kind === "staff";
  const facilities = useResource<Facility[]>(
    ready && user?.roles.includes("OWNER") && isOwner
      ? "/owner/facilities"
      : null,
  );
  const facilityId = path.match(/\/facilities\/([^/]+)/)?.[1];
  const selected =
    facilities.data?.find((f) => f.id === facilityId) || facilities.data?.[0];
  const base = selected
    ? `/owner/facilities/${selected.id}`
    : "/owner/facilities";
  const iconSize = 18;
  const items: NavItem[] = isOwner
    ? [
        ["TỔNG QUAN", ""],
        [
          "Không gian quản lý",
          "/owner",
          <LayoutDashboard size={iconSize} key="icon" />,
        ],
        [
          "Cơ sở của tôi",
          "/owner/facilities",
          <Building2 size={iconSize} key="icon" />,
        ],
        ["VẬN HÀNH", ""],
        [
          "Lịch đặt sân",
          "/owner/bookings",
          <CalendarDays size={iconSize} key="icon" />,
        ],
        [
          "Sân & bảo trì",
          base + "/courts",
          <Activity size={iconSize} key="icon" />,
        ],
        [
          "Giờ hoạt động",
          base + "/schedule",
          <CalendarDays size={iconSize} key="icon" />,
        ],
        ["Bảng giá", base + "/pricing", <Wallet size={iconSize} key="icon" />],
        ["QUẢN LÝ", ""],
        ["Nhân viên", base + "/staff", <Users size={iconSize} key="icon" />],
        [
          "Báo cáo",
          base + "/reports",
          <ChartNoAxesCombined size={iconSize} key="icon" />,
        ],
        [
          "Cài đặt cơ sở",
          base + "/edit",
          <Settings2 size={iconSize} key="icon" />,
        ],
        [
          "Hồ sơ chủ sân",
          "/owner/application",
          <ShieldCheck size={iconSize} key="icon" />,
        ],
      ]
    : isStaff
      ? [
          ["VẬN HÀNH", ""],
          [
            "Tổng quan",
            "/staff",
            <LayoutDashboard size={iconSize} key="icon" />,
          ],
          [
            "Lịch sân",
            "/staff/schedule",
            <CalendarDays size={iconSize} key="icon" />,
          ],
          [
            "Booking tại cơ sở",
            "/staff/bookings",
            <Ticket size={iconSize} key="icon" />,
          ],
          [
            "Đặt sân tại quầy",
            "/staff/counter-booking",
            <ContactRound size={iconSize} key="icon" />,
          ],
        ]
      : [
          ["NỀN TẢNG", ""],
          [
            "Tổng quan",
            "/admin",
            <LayoutDashboard size={iconSize} key="icon" />,
          ],
          [
            "Tài khoản",
            "/admin/accounts",
            <Users size={iconSize} key="icon" />,
          ],
          [
            "Hồ sơ chủ sân",
            "/admin/owner-applications",
            <ShieldCheck size={iconSize} key="icon" />,
          ],
          [
            "Cơ sở thể thao",
            "/admin/facilities",
            <Building2 size={iconSize} key="icon" />,
          ],
          [
            "Danh mục thể thao",
            "/admin/sports",
            <Activity size={iconSize} key="icon" />,
          ],
          ["GIÁM SÁT", ""],
          ["Booking", "/admin/bookings", <Ticket size={iconSize} key="icon" />],
          [
            "Thanh toán",
            "/admin/payments",
            <Wallet size={iconSize} key="icon" />,
          ],
          [
            "Đối soát",
            "/admin/bookings/reconciliation",
            <Activity size={iconSize} key="icon" />,
          ],
          [
            "Báo cáo nền tảng",
            "/admin/reports",
            <ChartNoAxesCombined size={iconSize} key="icon" />,
          ],
          [
            "Khiếu nại",
            "/admin/disputes",
            <ContactRound size={iconSize} key="icon" />,
          ],
        ];
  return (
    <div className="management-shell">
      <a className="skip-link" href="#content">
        Đến nội dung chính
      </a>
      {open && (
        <button
          className="sidebar-overlay"
          aria-label="Đóng điều hướng"
          onClick={() => setOpen(false)}
        />
      )}
      <aside className={`sidebar ${open ? "is-open" : ""}`}>
        <Brand light />
        <span className="workspace-label">
          {isOwner
            ? "PARTNER WORKSPACE"
            : isStaff
              ? "OPERATIONS WORKSPACE"
              : "PLATFORM ADMIN"}
        </span>
        {isOwner && facilities.data && (
          <div className="facility-switch">
            <Building2 size={17} />
            <select
              aria-label="Chọn cơ sở"
              value={selected?.id ?? ""}
              onChange={(e) =>
                router.push(`/owner/facilities/${e.target.value}`)
              }
            >
              {facilities.data.length === 0 && (
                <option value="">Chưa có cơ sở</option>
              )}
              {facilities.data.map((f) => (
                <option key={f.id} value={f.id}>
                  {f.name}
                </option>
              ))}
            </select>
          </div>
        )}
        <nav aria-label="Điều hướng quản lý">
          {items.map(([text, href, icon]) =>
            href ? (
              <Link
                onClick={() => setOpen(false)}
                href={href}
                key={text}
                className={path === href ? "active" : ""}
              >
                {icon}
                {text}
              </Link>
            ) : (
              <span className="nav-section" key={text}>
                {text}
              </span>
            ),
          )}
        </nav>
        <div className="sidebar-bottom">
          <Link href="/">
            <Compass size={18} />
            Khám phá SportHub <ArrowUpRight size={15} />
          </Link>
          <div className="sidebar-user">
            <span className="avatar">{user?.fullName.slice(0, 1) ?? "S"}</span>
            <div>
              <strong>{user?.fullName ?? "SportHub"}</strong>
              <span>
                {isOwner
                  ? "Đối tác sân thể thao"
                  : isStaff
                    ? "Nhân viên vận hành"
                    : "Quản trị nền tảng"}
              </span>
            </div>
          </div>
        </div>
      </aside>
      <div className="workspace">
        <header className="workspace-header">
          <button
            className="icon-btn mobile-menu"
            onClick={() => setOpen(true)}
            aria-label="Mở điều hướng"
          >
            <Menu />
          </button>
          <div className="breadcrumb">
            {isOwner
              ? "Quản lý cơ sở"
              : isStaff
                ? "Vận hành sân"
                : "Quản trị nền tảng"}
            <span>/</span>
            <strong>
              {items.find((x) => x[1] === path)?.[0] ?? "Chi tiết"}
            </strong>
          </div>
          <UserMenu />
        </header>
        <main id="content" className="workspace-content">
          {children}
        </main>
      </div>
    </div>
  );
}
type NavItem = [string, string, ReactNode?];

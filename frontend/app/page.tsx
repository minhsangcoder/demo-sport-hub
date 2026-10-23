import { Footer } from '@/components/layout/footer'
import { HomeRoleRedirect } from '@/features/home/home-role-redirect'
import { HomeHero } from '@/features/home/home-hero'
import { HomeSports } from '@/features/home/home-sports'
import { HomeAvailability } from '@/features/home/home-availability'
import { HomeBenefits } from '@/features/home/home-benefits'
// Only a public calendar value is calculated per request; account state stays in browser memory.
export const dynamic = 'force-dynamic'
export default function Home() {
  const today = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' })
  return (
    <div className="flex flex-col min-h-screen bg-slate-50 dark:bg-slate-950">
      <HomeRoleRedirect />
      <HomeHero today={today} />
      <HomeSports today={today} />
      <HomeAvailability today={today} />
      <HomeBenefits />
      <Footer />
    </div>
  )
}

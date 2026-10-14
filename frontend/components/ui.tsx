'use client'
import Link from 'next/link'
import type { ReactNode,ButtonHTMLAttributes } from 'react'
import {Button as BaseButton} from './ui/button'
export function Button({variant, ...props}:ButtonHTMLAttributes<HTMLButtonElement>&{variant?:'secondary'}){return <BaseButton variant={variant?'outline':'default'} {...props}/>}
export function LinkButton({href,children}: {href:string;children:ReactNode;variant?:string}){return <Link href={href} className="inline-flex rounded-full border px-5 py-3 text-sm font-semibold text-emerald-700">{children}</Link>}
export function Panel({children}:{children:ReactNode}){return <section className="mt-6 rounded-2xl border p-6">{children}</section>}
export function PageHead({title,description}:{title:string;description?:string}){return <div><h1 className="text-3xl font-black">{title}</h1><p className="mt-3 text-slate-500">{description}</p></div>}

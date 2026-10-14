'use client'
import type {ReactNode} from 'react'
import {useState} from 'react'
export function Frame({title,children}:{title:string;children:ReactNode}){return <div className="mx-auto max-w-5xl space-y-6 px-5 py-10"><h1 className="text-3xl font-black">{title}</h1>{children}</div>}
export function Card({title,children}:{title:string;children:ReactNode}){return <section className="space-y-4 rounded-2xl border p-6"><h2 className="text-xl font-bold">{title}</h2>{children}</section>}
export function Status({loading,error,empty=false}:{loading?:boolean;error?:string;empty?:boolean}){return <>{loading&&<p role="status">Đang tải…</p>}{error&&<p role="alert" className="rounded-lg bg-red-50 p-4 text-red-700">{error}</p>}{!loading&&!error&&empty&&<p className="text-slate-500">Chưa có bản ghi.</p>}</>}
export function useMutation(){const [busy,setBusy]=useState(false),[error,setError]=useState(''),[message,setMessage]=useState('');async function run<T>(work:()=>Promise<T>,done?:(result:T)=>void){setBusy(true);setError('');setMessage('');try{const result=await work();done?.(result);return result}catch(e){setError(e instanceof Error?e.message:'Không thể thực hiện yêu cầu.')}finally{setBusy(false)}}return {busy,error,message,setMessage,run}}
export const selectStyle='rounded-lg border bg-white p-3'

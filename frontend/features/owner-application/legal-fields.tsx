'use client'
import { Input } from '@/components/ui/input'
import type { Legal } from './types'
export const legalFields:[keyof Legal,string,number,boolean][]=[['representativeName','Họ tên người đại diện',180,true],['identityNumber','Số CCCD / giấy tờ định danh',50,true],['businessName','Tên hộ kinh doanh / doanh nghiệp',180,true],['businessLicense','Số giấy phép kinh doanh',100,false],['taxCode','Mã số thuế',50,false],['bankName','Ngân hàng nhận thanh toán',100,true],['bankAccountHolder','Chủ tài khoản ngân hàng',100,true],['bankAccountNumber','Số tài khoản ngân hàng',50,true]]
export function legalFrom(form:FormData):Legal{return Object.fromEntries(legalFields.map(([key,,,required])=>[key,String(form.get(key)||'').trim()||(required?'':null)])) as Legal}
export function LegalFields({legal,busy=false,fields={}}:{legal?:Legal;busy?:boolean;fields?:Record<string,string>}){
 return <>{legalFields.map(([name,label,max,required])=> (
  <label key={name} className="flex flex-col justify-start min-w-0 gap-2 text-sm font-medium self-start w-full">
   <span>
    {label}
    {required ? (
     <span className="ml-1 font-bold text-red-500">*</span>
    ) : (
     <span className="font-normal text-slate-400"> (tùy chọn)</span>
    )}
   </span>
   <Input name={name} defaultValue={legal?.[name]??''} maxLength={max} required={required} disabled={busy} autoComplete="off" aria-invalid={!!fields[name]}/>
   {fields[name]&&<span className="text-xs text-red-700">{fields[name]}</span>}
  </label>
 ))}</>
}

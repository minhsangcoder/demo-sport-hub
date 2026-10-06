'use client'
import dynamic from 'next/dynamic'
import { useState } from 'react'
import { Input } from '@/components/ui/input'
import type { FacilityProfile,FacilityInput } from './types'
import type { FacilityLocation } from './location-picker'
import { AddressAutocomplete, type FacilityPlaceSelection } from './address-autocomplete'
import { provinceByCode, provinceByName, provinces, wardByCode } from './administrative-divisions'
type FacilityAddress = { addressLine: string; provinceCode: string; wardCode: string }
const LocationPicker=dynamic(()=>import('./location-picker').then(module=>module.LocationPicker),{ssr:false,loading:()=> <p role="status">Đang mở bản đồ…</p>})
export function facilityFrom(form:FormData,location:FacilityLocation,nameField='name'):FacilityInput {
 const text=(name:string)=>String(form.get(name)??'').trim()
 const province=provinceByCode(text('provinceCode'))
 const ward=wardByCode(province?.code,text('wardCode'))
 return {name:text(nameField),phone:text('phone'),contactEmail:text('contactEmail')||null,addressLine:text('addressLine'),provinceCode:text('provinceCode'),province:province?.name??text('province'),wardCode:text('wardCode'),ward:ward?.name??text('ward'),description:text('description'),timezone:text('timezone'),amenities:text('amenities').split(',').map(x=>x.trim()).filter(Boolean),...(location??{})}
}
export function FacilityFields({facility,location,onLocationChange,busy=false,frozen=false,fields={},nameField='name'}:{facility?:FacilityProfile;location:FacilityLocation;onLocationChange:(value:FacilityLocation)=>void;busy?:boolean;frozen?:boolean;fields?:Record<string,string>;nameField?:string}){
 const [focusLocation,setFocusLocation]=useState<FacilityLocation>(null)
 const [address,setAddress]=useState<FacilityAddress>(()=>{const province=facility?.provinceCode?provinceByCode(facility.provinceCode):provinceByName(facility?.province);const ward=wardByCode(province?.code,facility?.wardCode);return {addressLine:facility?.addressLine??'',provinceCode:province?.code??'',wardCode:ward?.code??''}})
 const province=provinceByCode(address.provinceCode)
 const ward=wardByCode(address.provinceCode,address.wardCode)
 const provinceError=fields.provinceCode||fields.province
 const wardError=fields.wardCode||fields.ward
 function selectPlace(selection:FacilityPlaceSelection){setAddress({...selection.address});const point={latitude:selection.latitude,longitude:selection.longitude};onLocationChange(point);setFocusLocation(point)}
 function field(name:keyof FacilityInput,label:string,max:number,required=true,type='text'){return <label className="grid min-w-0 gap-2 text-sm font-medium">{label}<Input type={type} name={name==='name'?nameField:name} defaultValue={String(facility?.[name]??(name==='timezone'?'Asia/Ho_Chi_Minh':''))} maxLength={max} required={required} disabled={busy} aria-invalid={!!fields[name]}/>{fields[name]&&<span className="text-xs text-red-700">{fields[name]}</span>}</label>}
 return <>{field('name','Tên cơ sở',180)}{field('phone','Số điện thoại cơ sở',30)}{field('contactEmail','Email liên hệ cơ sở (tùy chọn)',254,false,'email')}
 <AddressAutocomplete value={address.addressLine} onChange={addressLine=>setAddress(current=>({...current,addressLine}))} onSelect={selectPlace} disabled={busy||frozen} error={fields.addressLine}/>
 <label className="grid min-w-0 gap-2 text-sm font-medium">Tỉnh / thành phố<select name="provinceCode" value={address.provinceCode} onChange={event=>setAddress(current=>({...current,provinceCode:event.target.value,wardCode:''}))} className="h-10 w-full min-w-0 rounded-lg border px-3" required disabled={busy||frozen} aria-invalid={!!provinceError}><option value="">Chọn tỉnh / thành phố</option>{provinces.map(item=><option value={item.code} key={item.code}>{item.name}</option>)}</select><input type="hidden" name="province" value={frozen?(facility?.province??''):(province?.name??'')}/>{provinceError&&<span className="text-xs text-red-700">{provinceError}</span>}</label>
 <label className="grid min-w-0 gap-2 text-sm font-medium">Xã / phường<select name="wardCode" value={address.wardCode} onChange={event=>setAddress(current=>({...current,wardCode:event.target.value}))} className="h-10 w-full min-w-0 rounded-lg border px-3" required disabled={!province||busy||frozen} aria-invalid={!!wardError}><option value="">{province?'Chọn xã / phường':'Chọn tỉnh / thành phố trước'}</option>{province?.wards.map(item=><option value={item.code} key={item.code}>{item.name}</option>)}</select><input type="hidden" name="ward" value={frozen?(facility?.ward??''):(ward?.name??'')}/>{wardError&&<span className="text-xs text-red-700">{wardError}</span>}</label>
 {facility&&!frozen&&(!province||!ward)&&<p role="status" className="text-xs text-amber-800 md:col-span-2">Địa chỉ đã lưu: {facility.ward}, {facility.province}. Chọn lại tỉnh / thành phố và xã / phường theo danh sách hiện hành trước khi lưu.</p>}
 {frozen&&<><input type="hidden" name="addressLine" value={facility?.addressLine??''}/><input type="hidden" name="provinceCode" value={facility?.provinceCode??''}/><input type="hidden" name="wardCode" value={facility?.wardCode??''}/></>}
 {field('timezone','Múi giờ IANA',80)}
 <label className="grid min-w-0 gap-2 text-sm font-medium">Tiện ích, phân cách bằng dấu phẩy<Input name="amenities" defaultValue={facility?.amenities.join(', ')} maxLength={1200} disabled={busy}/></label>
 <label className="grid min-w-0 gap-2 text-sm md:col-span-2">Mô tả<textarea className="min-h-28 rounded-lg border p-3" name="description" defaultValue={facility?.description} maxLength={5000} disabled={busy}/></label>
 <LocationPicker value={location} focusLocation={focusLocation} onChange={onLocationChange} disabled={busy||frozen}/>
 {(fields.latitude||fields.longitude||fields.locationComplete)&&<p role="alert" className="text-sm text-red-700 md:col-span-2">{fields.latitude||fields.longitude||fields.locationComplete}</p>}</>
}

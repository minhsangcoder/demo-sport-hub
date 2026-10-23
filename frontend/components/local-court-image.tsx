'use client'
import Image, { type ImageProps } from 'next/image'
import { preload } from 'react-dom'

const localPhoto = /^\/images\/(hero-pickleball|hero-paddle|hero-tennis|court-aerial)\.(png|jpg)$/
// Native picture selection works with the existing global unoptimized setting.
// Uploaded/private media retain Next Image's original URL and behavior.
export function LocalCourtImage(props: ImageProps) {
  const match = typeof props.src === 'string' ? localPhoto.exec(props.src) : null
  if (!match) return <Image {...props} alt={props.alt} />
  const src = `/images/${match[1]}-1280.webp`
  const srcSet = `/images/${match[1]}-640.webp 640w, ${src} 1280w`
  const sizes = props.sizes ?? '(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 33vw'
  const eager = Boolean(props.preload || props.priority)
  if (eager)
    preload(src, { as: 'image', imageSrcSet: srcSet, imageSizes: sizes, fetchPriority: 'high' })
  return (
    <picture>
      <source type="image/webp" srcSet={srcSet} sizes={sizes} />
      <Image
        {...props}
        alt={props.alt}
        src={src}
        sizes={sizes}
        unoptimized
        preload={false}
        priority={false}
        loading={eager ? 'eager' : props.loading}
        fetchPriority={eager ? 'high' : props.fetchPriority}
      />
    </picture>
  )
}

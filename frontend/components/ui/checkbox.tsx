'use client'

import * as React from 'react'
import { Checkbox as CheckboxPrimitive } from '@base-ui/react/checkbox'
import { cn } from '@/lib/utils'

export interface CheckboxProps
  extends Omit<CheckboxPrimitive.Root.Props, 'onChange'> {
  size?: 'sm' | 'default' | 'lg'
  onChange?: (e: React.ChangeEvent<HTMLInputElement>) => void
}

const Checkbox = React.forwardRef<HTMLElement, CheckboxProps>(
  (
    {
      className,
      size = 'default',
      checked,
      defaultChecked,
      onCheckedChange,
      onChange,
      disabled,
      name,
      value,
      ...props
    },
    ref
  ) => {
    const handleCheckedChange = React.useCallback(
      (newChecked: boolean, event: any) => {
        onCheckedChange?.(newChecked, event)
        if (onChange) {
          const fakeEvent = {
            target: { checked: newChecked, name, value },
            currentTarget: { checked: newChecked, name, value },
          } as unknown as React.ChangeEvent<HTMLInputElement>
          onChange(fakeEvent)
        }
      },
      [onCheckedChange, onChange, name, value]
    )

    return (
      <CheckboxPrimitive.Root
        ref={ref}
        data-slot="checkbox"
        data-size={size}
        checked={checked}
        defaultChecked={defaultChecked}
        onCheckedChange={handleCheckedChange}
        disabled={disabled}
        name={name}
        value={value}
        className={cn(
          'peer group/checkbox relative inline-flex shrink-0 items-center justify-center border border-slate-300 bg-white transition-all duration-200 outline-none select-none cursor-pointer',
          'hover:border-emerald-500 hover:bg-slate-50/80',
          'focus-visible:border-emerald-600 focus-visible:ring-3 focus-visible:ring-emerald-500/25',
          'data-checked:bg-emerald-600 data-checked:border-emerald-600 data-checked:text-white data-checked:shadow-sm data-checked:shadow-emerald-600/30',
          'data-disabled:cursor-not-allowed data-disabled:opacity-50 data-disabled:hover:border-slate-300',
          'active:scale-90',
          'dark:border-slate-700 dark:bg-slate-900 dark:hover:border-emerald-500 dark:hover:bg-slate-800/80 dark:data-checked:bg-emerald-600 dark:data-checked:border-emerald-600',
          size === 'sm' && 'size-4 rounded-sm',
          size === 'default' && 'size-5 rounded-[6px]',
          size === 'lg' && 'size-6 rounded-md',
          className
        )}
        {...props}
      >
        <CheckboxPrimitive.Indicator
          data-slot="checkbox-indicator"
          className="flex items-center justify-center text-current"
        >
          <svg
            viewBox="0 0 16 16"
            fill="none"
            className={cn(
              'stroke-white stroke-[2.75] stroke-linecap-round stroke-linejoin-round animate-check-pop pointer-events-none',
              size === 'sm' && 'size-2.5',
              size === 'default' && 'size-3.5',
              size === 'lg' && 'size-4.5'
            )}
            aria-hidden="true"
          >
            <polyline points="3.5 8.5 6.5 11.5 12.5 4.5" />
          </svg>
        </CheckboxPrimitive.Indicator>
      </CheckboxPrimitive.Root>
    )
  }
)

Checkbox.displayName = 'Checkbox'

export { Checkbox }

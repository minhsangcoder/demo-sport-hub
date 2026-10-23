import { readFileSync, existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import vm from 'node:vm'
import ts from 'typescript'

const root = fileURLToPath(new URL('../../', import.meta.url))
export function moduleLoader(globals = {}, overrides = {}) {
  const cache = new Map()
  function load(relative) {
    const file = path.resolve(root, relative)
    if (cache.has(file)) return cache.get(file)
    const exports = {}
    cache.set(file, exports)
    vm.runInNewContext(ts.transpileModule(readFileSync(file, 'utf8'), {
      fileName: file,
      compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX },
    }).outputText, {
      exports, Error, Headers, FormData, File, Blob, Response, Request, URL, URLSearchParams, AbortController, Buffer, atob, btoa,
      AbortSignal, DOMException, crypto: globalThis.crypto, Event, queueMicrotask, setTimeout, clearTimeout, ...globals,
      require(name) {
        if (Object.hasOwn(overrides, name)) return overrides[name]
        let target = name.startsWith('@/') ? path.resolve(root, name.slice(2)) : path.resolve(path.dirname(file), name)
        if (!existsSync(target)) target += existsSync(target + '.ts') ? '.ts' : '.tsx'
        if (target.endsWith('.json')) return { default: JSON.parse(readFileSync(target, 'utf8')) }
        return load(path.relative(root, target))
      },
    }, { filename: file })
    return exports
  }
  return load
}

export const flush = () => new Promise(resolve => setImmediate(resolve))
export function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

// A hook lifetime driver, not a substitute implementation of the request store.
export function hookDriver() {
  let cursor = 0
  const slots = [], effects = [], cleanups = []
  const equal = (a, b) => a?.length === b?.length && a.every((value, i) => Object.is(value, b[i]))
  const react = {
    useRef(initial) {
      const i = cursor++
      if (!(i in slots)) slots[i] = { current: initial }
      return slots[i]
    },
    useState(initial) {
      const i = cursor++
      if (!(i in slots)) slots[i] = typeof initial === 'function' ? initial() : initial
      return [slots[i], value => { slots[i] = typeof value === 'function' ? value(slots[i]) : value }]
    },
    useEffect(effect, dependencies) {
      const i = cursor++
      if (!equal(slots[i], dependencies)) effects.push(() => { cleanups[i]?.(); cleanups[i] = effect() })
      slots[i] = dependencies
    },
    useCallback(callback, dependencies) {
      const i = cursor++
      if (!equal(slots[i]?.dependencies, dependencies)) slots[i] = { callback, dependencies }
      return slots[i].callback
    },
    useSyncExternalStore(subscribe, snapshot) {
      const i = cursor++
      if (!cleanups[i]) cleanups[i] = subscribe(() => {})
      return snapshot()
    },
  }
  return {
    react,
    render(hook, path) { cursor = 0; const result = hook(path); for (const run of effects.splice(0)) run(); return result },
    unmount() { for (const cleanup of cleanups) cleanup?.() },
  }
}

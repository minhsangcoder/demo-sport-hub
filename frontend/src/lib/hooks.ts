"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { api, ApiError } from "./api";
import { useAuth } from "./auth-context";
const resourceFlights = new Map<string, Promise<unknown>>();
export function useResource<T>(path: string | null, body?: string) {
  const { user } = useAuth();
  const [epoch, setEpoch] = useState(0);
  const [result, setResult] = useState<{
    key: string;
    request: string;
    data: T | null;
    error: string;
  } | null>(null);
  const request = (user?.id ?? "guest") + "|" + path + "|" + (body ?? "");
  const key = request + "|" + epoch;
  useEffect(() => {
    if (!path) return;
    let active = true;
    let flight = resourceFlights.get(request) as Promise<T> | undefined;
    if (!flight) {
      flight = api<T>(path, body ? { method: "POST", body } : {});
      resourceFlights.set(request, flight);
      void flight
        .finally(() => {
          if (resourceFlights.get(request) === flight)
            resourceFlights.delete(request);
        })
        .catch(() => {});
    }
    flight
      .then((data) => {
        if (active) setResult({ key, request, data, error: "" });
      })
      .catch((error) => {
        if (active)
          setResult({
            key,
            request,
            data: null,
            error:
              error instanceof Error
                ? error.message
                : "Không tải được dữ liệu.",
          });
      });
    return () => {
      active = false;
    };
  }, [path, key, request, body]);
  const reload = useCallback(() => setEpoch((x) => x + 1), []);
  return {
    data: path && result?.request === request ? result.data : null,
    error: result?.key === key ? result.error : "",
    loading:
      !!path &&
      result?.key !== key &&
      !(result?.request === request && result.data !== null),
    refreshing: !!path && result?.key !== key,
    reload,
  };
}
export function useAction() {
  const lock = useRef(false);
  const [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [message, setMessage] = useState("");
  const [fields, setFields] = useState<Record<string, string>>({});
  async function run<T>(fn: () => Promise<T>, success?: (value: T) => void) {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError("");
    setMessage("");
    setFields({});
    try {
      const value = await fn();
      success?.(value);
      return value;
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Không thể thực hiện yêu cầu.",
      );
      if (err instanceof ApiError)
        setFields(
          Object.fromEntries(
            (err.fieldErrors ?? []).map((x) => [x.field, x.message]),
          ),
        );
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }
  return { busy, error, message, fields, run, setError, setMessage };
}
// Same intent and body retain their key after an uncertain network failure.
export function useIdempotency() {
  const keys = useRef(new Map<string, string>());
  return (intent: string) => {
    if (!keys.current.has(intent))
      keys.current.set(intent, crypto.randomUUID());
    return keys.current.get(intent)!;
  };
}
export function useCountdown(expiresAt: string) {
  const [now, setNow] = useState<number | null>(null);
  useEffect(() => {
    const tick = () => setNow(Date.now());
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, []);
  const seconds =
    now === null
      ? null
      : Math.max(0, Math.ceil((new Date(expiresAt).getTime() - now) / 1000));
  return {
    expired: seconds === 0,
    text:
      seconds === null
        ? "--:--"
        : `${Math.floor(seconds / 60)
            .toString()
            .padStart(2, "0")}:${(seconds % 60).toString().padStart(2, "0")}`,
  };
}
export function useCurrentTime() {
  const [now, setNow] = useState<number | null>(null);
  useEffect(() => {
    const tick = () => setNow(Date.now());
    tick();
    const timer = setInterval(tick, 15000);
    return () => clearInterval(timer);
  }, []);
  return now;
}
export function formQuery(form: HTMLFormElement) {
  const params = new URLSearchParams();
  new FormData(form).forEach((v, k) => {
    if (typeof v === "string" && v.trim()) params.set(k, v.trim());
  });
  return params.toString();
}

import { useCallback, useEffect, useRef, useState } from 'react';
import type { DependencyList } from 'react';
import { getErrorMessage } from '../api/client';

export interface AsyncState<T> {
  /** null while loading for the first time (or after the deps changed), or on error. */
  data: T | null;
  loading: boolean;
  error: string | null;
  /** Re-run the loader, keeping the current data visible until the new result arrives. */
  reload: () => void;
  /** Replace the data without a request (e.g. with the body a mutation returned). */
  setData: (value: T) => void;
}

/**
 * Minimal data-loading hook: runs `load` whenever `deps` change, ignores stale results,
 * and exposes loading / error state so every page can render all three states.
 */
export function useAsync<T>(load: () => Promise<T>, deps: DependencyList, fallbackError = 'Could not load data'): AsyncState<T> {
  const key = JSON.stringify(deps);
  const [result, setResult] = useState<{ key: string; data: T } | null>(null);
  const [error, setError] = useState<{ key: string; message: string } | null>(null);
  const [tick, setTick] = useState(0);
  const loadRef = useRef(load);
  loadRef.current = load;

  useEffect(() => {
    let cancelled = false;
    setError(null);
    loadRef
      .current()
      .then((data) => {
        if (!cancelled) setResult({ key, data });
      })
      .catch((e) => {
        if (!cancelled) setError({ key, message: getErrorMessage(e, fallbackError) });
      });
    return () => {
      cancelled = true;
    };
    // `key` encodes deps; `tick` forces a reload.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, tick]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  const setData = useCallback((data: T) => setResult({ key, data }), [key]);

  const current = result && result.key === key ? result.data : null;
  const currentError = error && error.key === key ? error.message : null;
  return {
    data: currentError ? null : current,
    error: currentError,
    loading: current === null && currentError === null,
    reload,
    setData,
  };
}

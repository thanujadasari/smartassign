import { useEffect, useState } from 'react';
import { api } from './api';
import type { ReferenceOptions } from './types';

let cache: Promise<ReferenceOptions> | null = null;

/** Option lists for forms and filters, loaded once from the backend (never hard-coded). */
export function useOptions() {
  const [options, setOptions] = useState<ReferenceOptions | null>(null);
  useEffect(() => {
    let alive = true;
    cache ??= api.reference.options().catch((e) => {
      cache = null;
      throw e;
    });
    cache.then((o) => alive && setOptions(o)).catch(() => undefined);
    return () => {
      alive = false;
    };
  }, []);
  return options;
}

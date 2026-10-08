/** Drop empty/undefined values so they are not sent as "?search=&status=". */
export function cleanParams<T extends object>(params: T): Record<string, string | number | boolean> {
  const out: Record<string, string | number | boolean> = {};
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue;
    out[key] = value as string | number | boolean;
  }
  return out;
}

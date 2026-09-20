/**
 * "1 hotel", "4 hotels".
 *
 * <p>Small, but the alternative is "1 hotels" on every dashboard that happens to
 * have one of something — which is exactly the state a new platform is in, and
 * exactly when it can least afford to look unfinished.
 *
 * @param plural the plural form, when adding "s" is wrong
 */
export function plural(count: number, noun: string, plural?: string): string {
  return `${count} ${count === 1 ? noun : plural ?? `${noun}s`}`
}

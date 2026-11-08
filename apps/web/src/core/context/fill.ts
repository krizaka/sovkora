/** Fills the `{name}` placeholders of a translated message: `fill("{done} of {total}", { done: 2, total: 5 })`. */
export function fill(message: string, values: Readonly<Record<string, string | number>>): string {
  return message.replace(/\{(\w+)\}/g, (match, key: string) => (key in values ? String(values[key]) : match));
}

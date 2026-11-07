import { formatDistanceToNow, type Locale } from "date-fns";

/** « 3 hours ago » as a `<time>` the table cells share: the exact moment is its title. A plain function, not a component, so a column definition can call it. */
export const timeAgoCell = (iso: string, locale: Locale) => (
  <time dateTime={iso} title={iso}>
    {formatDistanceToNow(new Date(iso), { addSuffix: true, locale })}
  </time>
);

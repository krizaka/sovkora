// What run-api-contracts reads out of an api-contracts `.http` source, in linear time on any input.

/** An assertion line in a .http source (`?? <expression>`): its expression, or null. */
export function assertionOf(line) {
  return /^\?\?\s+(\S.*)$/.exec(line)?.[1].trim() ?? null;
}

/** A request line: what makes a `###` region an actual request rather than a banner. */
export function hasRequestLine(region) {
  return /^[^\S\n]*(?:GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\s+\S/m.test(region);
}

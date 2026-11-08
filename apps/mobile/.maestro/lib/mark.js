// Tells the recorder (scripts/record-story.mjs) that a named moment just happened, so the waits can be cut and the
// typing sped up afterwards. Without MARK_URL (a plain test run) it does nothing.
var base = typeof MARK_URL === "undefined" ? "" : MARK_URL;
if (base !== "") {
  var label = typeof LABEL === "undefined" ? "" : LABEL;
  http.get(base + "/mark?name=" + encodeURIComponent(NAME) + "&label=" + encodeURIComponent(label));
}

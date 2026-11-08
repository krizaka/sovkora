import React from "react";
import { StyleSheet, View } from "react-native";
import { Card, Txt, useTheme } from "@krizaka/ui/native";
import type { RunArtefact } from "@krizaka/orazaka-shared";
import { asReport, asTextResult } from "../core/report";
import { Markdown } from "./Markdown";
import { ReportCards } from "./ReportCards";
import { ImageArtefact, VideoArtefact } from "./MediaArtefact";
import { useT, type Key } from "../i18n";

const MEDIA_TITLES: Partial<Record<RunArtefact["type"], Key>> = {
  IMAGE: "artefact.image",
  VIDEO: "artefact.video",
  AUDIO: "artefact.audio",
};

/** A picture, a clip or a sound is named by what it is; a text keeps the platform's own title. */
function titleOf(artefact: RunArtefact, factName: (key: string) => string, t: (key: Key) => string): string {
  if (artefact.type === "TEXT" && ["seed", "model", "generationMs"].includes(artefact.key)) {
    return factName(artefact.key);
  }
  const mediaKey = MEDIA_TITLES[artefact.type];
  return mediaKey ? t(mediaKey) : artefact.label;
}

/**
 * One thing a run produced, as the blueprint typed it: a picture shown, a clip played, a rules report as cards, a
 * model's text as Markdown with the run's facts apart. Never the raw value printed as a label and a path.
 */
export function ArtefactCard({
  artefact,
  token,
}: Readonly<{ artefact: RunArtefact; token: string | null }>): React.JSX.Element {
  const { theme } = useTheme();
  const t = useT();
  const factName = (key: string): string => {
    const k = `artefact.facts.${key}` as Key;
    const text = t(k);
    return text === k ? key : text;
  };
  const media = artefact.type !== "TEXT" && artefact.value !== "";
  const value =
    artefact.key === "generationMs" && /^\d+$/.test(artefact.value)
      ? `${(Number(artefact.value) / 1000).toFixed(1)} s`
      : artefact.value;
  const report = artefact.type === "TEXT" ? asReport(value) : null;
  const text = artefact.type === "TEXT" && report === null ? asTextResult(value) : null;
  const title = titleOf(artefact, factName, t);
  const factValue = (key: string, value: string): string => {
    if (key !== "provider") return value;
    const k = `provider.${value}` as Key;
    const text = t(k);
    return text === k ? t("provider.other") : text;
  };
  return (
    <Card.Root>
      <Card.Body padding="sm" style={styles.body}>
        <Txt variant="caption" style={[styles.label, { color: theme.textMuted }]}>
          {title}
        </Txt>
        {media && artefact.type === "VIDEO" && (
          <VideoArtefact value={artefact.value} token={token} label={artefact.label} />
        )}
        {media && artefact.type === "AUDIO" && (
          <VideoArtefact audio value={artefact.value} token={token} label={artefact.label} />
        )}
        {media && artefact.type === "IMAGE" && (
          <ImageArtefact value={artefact.value} token={token} label={artefact.label} />
        )}
        {report !== null && <ReportCards report={report} />}
        {text !== null && <Markdown text={text.content} />}
        {text !== null && text.facts.length > 0 && (
          <View style={styles.facts}>
            {text.facts.map(([key, value]) => (
              <Txt
                key={key}
                variant="caption"
                style={{ color: theme.textMuted }}
              >{`${factName(key)} · ${factValue(key, value)}`}</Txt>
            ))}
          </View>
        )}
      </Card.Body>
    </Card.Root>
  );
}

const styles = StyleSheet.create({
  body: { gap: 10 },
  label: { textTransform: "uppercase", letterSpacing: 0.6 },
  facts: { flexDirection: "row", flexWrap: "wrap", gap: 12 },
});

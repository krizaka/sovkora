import React from "react";
import { StyleSheet, View } from "react-native";
import { Chip, Txt, useTheme } from "@krizaka/ui/native";
import { ChevronDownIcon, ShieldIcon } from "@krizaka/icons/native";
import type { CatalogModel } from "../../core/sovereignty";
import { staysOnNetwork } from "../../core/sovereignty";
import { useT } from "../../i18n";

interface Props {
  /** The model that will answer (the picked one, else the catalogue's default); null while the catalogue loads. */
  readonly model: CatalogModel | null;
  readonly onPickModel: () => void;
}

/**
 * Above the composer: which model answers (a chip that opens the choice) and, only when it is computed true, that
 * nothing left the network. The line reads the model's own provider — a hosted-inference model says nothing.
 */
export function ChatMeta({ model, onPickModel }: Readonly<Props>): React.JSX.Element | null {
  const { theme } = useTheme();
  const t = useT();
  if (model === null) return null;
  return (
    <View style={styles.row}>
      <Chip
        size="sm"
        selected={false}
        onSelectedChange={onPickModel}
        accessibilityLabel={`${t("chat.model")}: ${model.modelLabel ?? model.modelName}`}
      >
        <View style={styles.chip}>
          <Txt variant="caption" numberOfLines={1} style={styles.chipLabel}>
            {model.modelLabel ?? model.modelName}
          </Txt>
          <ChevronDownIcon size={14} color={theme.textSecondary} />
        </View>
      </Chip>
      {staysOnNetwork(model) ? (
        <View style={styles.egress} accessible accessibilityLabel={t("chat.noEgress")}>
          <ShieldIcon size={14} color={theme.accentText} nodeColor={theme.accent} />
          <Txt variant="caption" tone="secondary" numberOfLines={1}>
            {t("chat.noEgress")}
          </Txt>
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: "row", alignItems: "center", gap: 10, paddingHorizontal: 12, paddingTop: 8 },
  chip: { flexDirection: "row", alignItems: "center", gap: 4, maxWidth: 190 },
  chipLabel: { flexShrink: 1 },
  egress: { flexDirection: "row", alignItems: "center", gap: 6, flexShrink: 1 },
});

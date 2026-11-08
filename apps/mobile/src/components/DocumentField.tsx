import React, { useCallback, useRef, useState } from "react";
import { StyleSheet, View } from "react-native";
import * as DocumentPicker from "expo-document-picker";
import * as FileSystem from "expo-file-system";
import { Button, Txt } from "@krizaka/ui/native";
import { base64ToBytes, createInspector, documentRules, maxPdfBytesFor, reasonKey } from "@krizaka/orazaka-shared";
import { useI18n } from "../i18n";

interface Props {
  /** The field's title, read by assistive technology. */
  readonly title: string;
  /** What the Studio's text field can hold, in base64 characters: the file may not be larger than that carries. */
  readonly maxLength?: number;
  /** Called with the file as base64 once it passes the pre-flight, and with "" when it is removed, replaced or refused. */
  readonly onValueChange: (base64: string) => void;
  /** True while the file is being read and checked: the run cannot be launched meanwhile. */
  readonly onBusyChange: (busy: boolean) => void;
}

const MB = 1024 * 1024;

/**
 * A Studio input that takes a PDF: the phone's file picker (Files, iCloud, Drive), then the PRE-FLIGHT before a run that
 * costs credits — the real type, the size the field can hold, a PDF that is whole, not encrypted, carrying nothing active.
 * A file that fails says why in words and sends nothing; one that passes is held as base64, which is what the Studio's
 * input is (no upload route: the document travels in the run, as the pack declares).
 */
export function DocumentField({ title, maxLength, onValueChange, onBusyChange }: Props): React.JSX.Element {
  const { t, locale } = useI18n();
  const [name, setName] = useState<string | null>(null);
  const [megabytes, setMegabytes] = useState<number | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);
  const attempt = useRef(0);
  const maxBytes = maxPdfBytesFor(maxLength);
  const number = (value: number) =>
    value.toLocaleString(locale === "fr" ? "fr-CA" : "en-CA", { maximumFractionDigits: 1 });

  const clear = useCallback(() => {
    attempt.current++;
    setName(null);
    setMegabytes(null);
    setProblem(null);
    setChecking(false);
    onValueChange("");
    onBusyChange(false);
  }, [onBusyChange, onValueChange]);

  const choose = useCallback(async () => {
    const picked = await DocumentPicker.getDocumentAsync({
      type: "application/pdf",
      copyToCacheDirectory: true,
      multiple: false,
    });
    const asset = picked.canceled ? undefined : picked.assets[0];
    if (asset === undefined) return;
    const mine = ++attempt.current;
    onValueChange("");
    setName(null);
    setProblem(null);
    setChecking(true);
    onBusyChange(true);
    const refuse = (message: string) => {
      if (mine !== attempt.current) return;
      setProblem(message);
      setChecking(false);
      onBusyChange(false);
    };
    const tooLarge = t("studios.document.refused.TOO_LARGE", { max: number(maxBytes / MB) });
    // The size is known before a byte is read: a 300 MB scan is refused without loading it into memory.
    if (asset.size !== undefined && asset.size > maxBytes) return refuse(tooLarge);
    try {
      const base64 = await FileSystem.readAsStringAsync(asset.uri, { encoding: FileSystem.EncodingType.Base64 });
      const result = await createInspector(documentRules({ maxBytes })).inspect(base64ToBytes(base64), {
        name: asset.name,
        type: asset.mimeType ?? "",
      });
      if (mine !== attempt.current) return;
      if (result.kind === "rejected")
        return refuse(result.reason === "TOO_LARGE" ? tooLarge : t(reasonKey(result.reason)));
      setName(asset.name);
      setMegabytes(Math.round(((asset.size ?? base64.length * 0.75) / MB) * 10) / 10);
      setChecking(false);
      onBusyChange(false);
      onValueChange(base64);
    } catch {
      refuse(t("studios.document.readFailed"));
    }
  }, [maxBytes, onBusyChange, onValueChange, t, locale]);

  const sizeText = megabytes === null ? "" : ` · ${t("studios.document.size", { size: number(megabytes) })}`;
  const readyText = name === null ? "" : `${t("studios.document.ready", { name })}${sizeText}`;
  return (
    <View style={styles.box} accessibilityLabel={title}>
      <Button
        variant="secondary"
        label={name === null ? t("studios.document.choose") : t("studios.document.replace")}
        disabled={checking}
        onPress={() => void choose()}
      />
      {checking && (
        <Txt variant="caption" tone="secondary" accessibilityLiveRegion="polite">
          {t("studios.document.reading")}
        </Txt>
      )}
      {name !== null && (
        <>
          <Txt variant="caption" tone="secondary" accessibilityLiveRegion="polite">
            {readyText}
          </Txt>
          <Button variant="ghost" label={t("studios.document.remove")} onPress={clear} />
        </>
      )}
      {problem !== null && (
        <Txt variant="caption" tone="danger" accessibilityRole="alert">
          {problem}
        </Txt>
      )}
    </View>
  );
}

const styles = StyleSheet.create({ box: { gap: 8 } });

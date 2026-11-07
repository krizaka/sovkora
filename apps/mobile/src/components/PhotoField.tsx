import React, { useCallback, useRef, useState } from "react";
import { StyleSheet, View } from "react-native";
import { Txt } from "@krizaka/ui/native";
import { MediaPicker, type PickedMedia } from "@krizaka/ui/native/media-picker";
import { uploadAsset } from "../core/assetUpload";
import { useI18n } from "../i18n";

interface Props {
  /** The field's title, read by assistive technology. */
  readonly title: string;
  readonly token: string | null;
  /** Called with the asset id once the photo is uploaded, and with "" when it is removed or being replaced. */
  readonly onAssetChange: (assetId: string) => void;
  /** True while the photo is being sent: the run cannot be launched meanwhile. */
  readonly onBusyChange: (busy: boolean) => void;
}

/**
 * A Studio input that takes a photo: the camera roll or the camera (expo-image-picker, through the kit's MediaPicker),
 * a thumbnail once picked, a remove button. The photo is uploaded at once to the authenticated upload route and the
 * asset id it returns is what the run is started with — the person never sees it.
 */
export function PhotoField({ title, token, onAssetChange, onBusyChange }: Props): React.JSX.Element {
  const { t } = useI18n();
  const [media, setMedia] = useState<readonly PickedMedia[]>([]);
  const [failed, setFailed] = useState(false);
  const [sending, setSending] = useState(false);
  const attempt = useRef(0);

  const change = useCallback(
    (next: PickedMedia[]) => {
      const mine = ++attempt.current;
      const photo = next.at(-1);
      setFailed(false);
      onAssetChange("");
      if (photo === undefined) {
        setMedia([]);
        setSending(false);
        onBusyChange(false);
        return;
      }
      setMedia([photo]);
      setSending(true);
      onBusyChange(true);
      uploadAsset({ uri: photo.uri, fileName: photo.fileName, token })
        .then((assetId) => {
          if (mine === attempt.current) onAssetChange(assetId);
        })
        .catch(() => {
          if (mine === attempt.current) setFailed(true);
        })
        .finally(() => {
          if (mine === attempt.current) {
            setSending(false);
            onBusyChange(false);
          }
        });
    },
    [onAssetChange, onBusyChange, token],
  );

  return (
    <View style={styles.box} accessibilityLabel={title}>
      <MediaPicker
        value={media}
        onValueChange={change}
        accept="images"
        max={1}
        labels={{
          add: t("studios.photoAdd"),
          source: t("studios.photoSource"),
          library: t("studios.photoLibrary"),
          camera: t("studios.photoCamera"),
          cancel: t("studios.photoCancel"),
          remove: () => t("studios.photoRemove"),
        }}
      />
      {sending && (
        <Txt variant="caption" tone="secondary" accessibilityLiveRegion="polite">
          {t("studios.photoUploading")}
        </Txt>
      )}
      {failed && (
        <Txt variant="caption" tone="danger" accessibilityRole="alert">
          {t("studios.photoFailed")}
        </Txt>
      )}
    </View>
  );
}

const styles = StyleSheet.create({ box: { gap: 8 } });

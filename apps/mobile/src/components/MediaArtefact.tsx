import React from "react";
import { Image, StyleSheet, View } from "react-native";
import { useVideoPlayer, VideoView } from "expo-video";
import { useTheme } from "@krizaka/ui/native";
import { mediaSource } from "../core/assets";

interface MediaProps {
  readonly value: string;
  readonly token: string | null;
  readonly label: string;
}

/**
 * A finished clip, playing: expo-video with the Bearer on the request (the edge's assets are private), the native
 * controls, started as soon as the run is done — the person came to see it. Audio is the same player, short.
 */
export function VideoArtefact({
  value,
  token,
  label,
  audio = false,
}: Readonly<MediaProps & { audio?: boolean }>): React.JSX.Element {
  const { theme } = useTheme();
  const source = mediaSource(value, token);
  const player = useVideoPlayer(
    { uri: source.uri, ...(source.headers ? { headers: source.headers } : {}) },
    (instance) => {
      instance.loop = !audio;
      instance.play();
    },
  );
  return (
    <View style={[audio ? styles.audio : styles.video, { backgroundColor: theme.media }]}>
      <VideoView
        player={player}
        style={styles.fill}
        nativeControls
        contentFit="contain"
        fullscreenOptions={{ enable: true }}
        accessibilityLabel={label}
      />
    </View>
  );
}

/** A generated picture, shown whole (never cropped) on the media colour. */
export function ImageArtefact({ value, token, label }: Readonly<MediaProps>): React.JSX.Element {
  const { theme } = useTheme();
  const source = mediaSource(value, token);
  return (
    <View style={[styles.image, { backgroundColor: theme.media }]}>
      <Image
        source={{ uri: source.uri, ...(source.headers ? { headers: source.headers } : {}) }}
        style={styles.fill}
        resizeMode="contain"
        accessibilityLabel={label}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  fill: { width: "100%", height: "100%" },
  video: { width: "100%", aspectRatio: 16 / 9, borderRadius: 12, overflow: "hidden" },
  image: { width: "100%", aspectRatio: 1, borderRadius: 12, overflow: "hidden" },
  audio: { width: "100%", height: 56, borderRadius: 12, overflow: "hidden" },
});

/** The two encodings of a recording, WebM first then MP4 (the browser takes the first it can play). */
export function VideoSources({ webm, mp4 }: Readonly<{ webm: string; mp4: string }>) {
  return (
    <>
      <source src={webm} type="video/webm" />
      <source src={mp4} type="video/mp4" />
    </>
  );
}

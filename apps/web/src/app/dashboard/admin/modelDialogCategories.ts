import { MODEL_CATEGORY } from "@/core/constants/capability.constants";
import type { TranslationDictionary } from "@/core/context/LocaleContext";

/** Category options (value + localized label) for the model dialog selector. */
export function buildModelCategories(t: TranslationDictionary) {
  return [
    { value: MODEL_CATEGORY.SPEECH, label: t.admin.optSpeech },
    { value: MODEL_CATEGORY.IMAGE, label: t.admin.optImage },
    { value: MODEL_CATEGORY.VIDEO, label: t.admin.optVideo },
    { value: MODEL_CATEGORY.VISION, label: t.admin.optVision },
    { value: MODEL_CATEGORY.AUDIO, label: t.admin.optAudio },
    { value: "theme", label: t.admin.optTheme },
    { value: "code", label: t.admin.optCode },
  ];
}

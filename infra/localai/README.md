# LocalAI model files — the speech half of the model catalogue

LocalAI (`:8085`, native, Metal) serves transcription and speech **under the names the model
catalogue declares** (`orazaka_models.model_name`). Since ADR-076 the engine no longer rewrites
`whisper-base` to `whisper-1`: the model the catalogue prices and reports is the model that runs,
so each catalogued name needs its file here.

Copy these files into the LocalAI models directory (`~/models/stable-diffusion/` — the CLI's
`resolveModelsDir`) and download the weights beside them:

| File | Weights | Source | Licence |
|:---|:---|:---|:---|
| `whisper-base.yaml` | `ggml-base.bin` | https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin | MIT |
| `whisper-tiny-en.yaml` | `ggml-tiny.en.bin` | https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin | MIT |
| `piper-fr-siwis-medium.yaml` | `fr/fr_FR/siwis/medium/fr_FR-siwis-medium.onnx` (+ `.onnx.json`) | https://huggingface.co/rhasspy/piper-voices/tree/v1.0.0/fr/fr_FR/siwis/medium | CC-BY 4.0 (SIWIS) |
| `piper-fr-tom-medium.yaml` | `fr/fr_FR/tom/medium/fr_FR-tom-medium.onnx` (+ `.onnx.json`) | https://huggingface.co/rhasspy/piper-voices/tree/v1.0.0/fr/fr_FR/tom/medium | AGPL-3.0 (weights, used unmodified, never redistributed by Sovkora) |
| `piper-fr-medium.yaml` | `fr/fr_FR/upmc/medium/fr_FR-upmc-medium.onnx` (+ `.onnx.json`) | https://huggingface.co/rhasspy/piper-voices/tree/v1.0.0/fr/fr_FR/upmc/medium | CC-BY-SA 4.0 |
| `piper-en-medium-ljspeech.yaml` | `en/en_US/ljspeech/medium/en_US-ljspeech-medium.onnx` (+ `.onnx.json`) | https://huggingface.co/rhasspy/piper-voices/tree/v1.0.0/en/en_US/ljspeech/medium | Public domain (LJSpeech — the English default) |
| `piper-en-medium-ryan.yaml` | `en/en_US/ryan/medium/en_US-ryan-medium.onnx` (+ `.onnx.json`) | https://huggingface.co/rhasspy/piper-voices/tree/v1.0.0/en/en_US/ryan/medium | CC BY-NC-SA 4.0 (RyanSpeech dataset — non-commercial: never a default) |
| `piper-en-low.yaml` | `en/en_US/lessac/low/en_US-lessac-low.onnx` (+ `.onnx.json`) | https://huggingface.co/rhasspy/piper-voices/tree/v1.0.0/en/en_US/lessac/low | Lessac Blizzard 2013 data licence (research licence — non-commercial: never a default) |

LocalAI 4.x finds its backends (`piper`, `whisper`) in `--backends-path`, which defaults to
`./backends` **relative to the directory it was started from**. Start it with the models
directory's own backends:

```bash
local-ai run --address=127.0.0.1:8085 \
  --models-path="$HOME/models/stable-diffusion" \
  --backends-path="$HOME/models/stable-diffusion/backends"
```

Without `--backends-path`, every speech and transcription call answers
`backend not found: piper` / `whisper` — measured on 2026-10-11 with LocalAI 4.3.1.

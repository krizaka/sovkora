# Document-reader worker

> `workers/reader` of [Sovkora](../../README.md), one repository (python, worker layer). Depends on nothing else of Sovkora. Licence: [FSL-1.1-ALv2](../../LICENSE).

Sovkora's document readers behind one port, `DocumentReader` (krizaka-internal#4198): a product sends a prepared page and
its kind, and gets back its text, its lines with their boxes and confidence, which reader read it and in which version,
and what the call cost and took. Which reader reads which kind of page is a **routing setting**, never a product's choice.

## What it provides

| Reader (`id`) | What | Licence | Where it runs |
|---|---|---|---|
| `paddleocr` | PaddleOCR 3.7, PP-OCRv5 detection and recognition | Apache-2.0 | this host, CPU |
| `doctr` | docTR 1.1, `db_resnet50` + `crnn_vgg16_bn` (PyTorch, CPU wheels) | Apache-2.0 | this host, CPU |
| `tesseract` | Tesseract 5, the reference (`fra+eng` when installed) | Apache-2.0 | this host, the `tesseract` command |
| `textract` | AWS Textract `DetectDocumentText`, Montréal (`ca-central-1`) | AWS service | AWS, behind the account (not opened yet) |

- **The port** (`document_reader/port.py`): `Page` (image bytes, at most 2 MB, and its kind `pdf-scan | screenshot |
  photo`; a PDF page is rasterised by the product), `Transcript` (lines in reading order, the reader's exact version),
  `Reading` (what the service answers). No vendor type crosses it.
- **Typed errors**: `READER_UNAVAILABLE`, `READER_TIMEOUT`, `READER_REFUSED`, `BUDGET_EXHAUSTED`, `INPUT_INVALID`. An
  error's message is its code: an engine's own message can quote the page.
- **Idempotent by page hash**: `Reading.key` is the page's SHA-256, the reader and its version. The same page read again
  by the same reader version is the same reading (the contract proves each reader deterministic), which is what lets a
  replay be answered without being billed twice.
- **Measured per call**: `Reading.cost_micros` (the routing's price of the reader that answered, millionths of a US
  dollar) and `Reading.latency_ms` (the whole call, fallback included).
- **Nothing is kept**: the page lives in the call; no reader or the service writes it anywhere.

## The routing settings

Flat keys, the shape of the platform's runtime configuration; every value is checked at load and a wrong one is refused
by its key (`routing.py`):

| Key | Bounds | Seed |
|---|---|---|
| `reader.default.pdf-scan` · `.screenshot` · `.photo` | a known reader; a generative one only with the switch on | `paddleocr` until the measurement (krizaka-internal#4060) chooses |
| `reader.fallback` | a known reader, or empty | `tesseract` |
| `reader.vlm.enabled` | `true` · `false` | `false` (off in production) |
| `reader.page-budget` | 1 to 12 pages per call | `12` |
| `reader.timeout-ms` | 100 to 600 000 | `30000` |
| `reader.cost-micros.<reader>` | 0 to 1 000 000 | `textract`: AWS's list price, to confirm for `ca-central-1` |

The fallback is tried once, only when the default is unavailable or too slow; a refusal or a bad page is the answer.

## Run it

```bash
python3.12 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt pytest   # brew install tesseract tesseract-lang · apt-get install tesseract-ocr tesseract-ocr-fra
python -m pytest -q test_reader_contract.py test_routing.py test_service.py test_textract.py
```

`test_reader_contract.py` is the contract every reader passes: PaddleOCR, docTR and Tesseract read a fictional page for
real (their models download on first use); Textract answers from `fixtures/textract-detect-document-text.json`, written
in the documented response shape until the account exists and a real answer can be recorded. No AWS call leaves CI.

"""Textract's transport failures, said by code: a refusal is never retried, an outage lets the fallback read."""

import pytest

from document_reader.adapters.textract import TextractReader, TextractRefused, TextractUnavailable
from document_reader.port import READER_REFUSED, READER_UNAVAILABLE, Page, PageKind, ReaderError


def failing(error):
    def detect(_image):
        raise error

    return detect


@pytest.mark.parametrize(
    ("error", "code"),
    [
        (TextractRefused(), READER_REFUSED),
        (TextractUnavailable(), READER_UNAVAILABLE),
        (ConnectionError("network down"), READER_UNAVAILABLE),
    ],
)
def test_a_transport_failure_is_a_code(error, code, fixture_page_bytes):
    with pytest.raises(ReaderError) as failed:
        TextractReader(detect=failing(error)).read(Page(data=fixture_page_bytes, kind=PageKind.PDF_SCAN))
    assert failed.value.code == code
    assert str(failed.value) == code


def test_the_page_sent_is_the_decoded_image_never_other_bytes(fixture_page_bytes):
    sent = []

    def detect(image):
        sent.append(image)
        return {"Blocks": [], "DetectDocumentTextModelVersion": "1.0"}

    transcript = TextractReader(detect=detect).read(Page(data=fixture_page_bytes, kind=PageKind.PHOTO))
    assert sent and sent[0].startswith(b"\x89PNG")
    assert transcript.lines == ()
    assert transcript.version == "textract-detect-document-text 1.0"

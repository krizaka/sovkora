"""The fixture page every reader is judged on: fictional words drawn on a white page, never a real document."""

import io

import pytest
from PIL import Image, ImageDraw, ImageFont

#: The words drawn on the fixture page, one line each (the fictional cast, no real person or account).
PAGE_LINES = ("INVOICE 4198", "TOTAL 1234.56", "MONTREAL QUEBEC")


def page_png(lines=PAGE_LINES) -> bytes:
    """A 1400 x 700 white PNG with each line in large black type: a prepared page, as a product sends it."""
    image = Image.new("RGB", (1400, 700), "white")
    draw = ImageDraw.Draw(image)
    font = ImageFont.load_default(size=72)
    for index, line in enumerate(lines):
        draw.text((90, 90 + index * 190), line, fill="black", font=font)
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    return buffer.getvalue()


@pytest.fixture(scope="session")
def fixture_page_bytes() -> bytes:
    return page_png()

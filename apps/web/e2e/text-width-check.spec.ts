import { test, expect } from "@playwright/test";
import { assertTextReadable, textSqueezedOnScreen } from "../scripts/text-width-on-screen.mjs";

/** The text width check every recording shares (krizaka-internal#3834) refuses a squeezed column and lets a readable one pass. */
const SENTENCE = "Every step runs on your machine and you can leave this page while it carries on";
const html = (inner: string) => `<body style="margin:0"><main style="width:360px">${inner}</main></body>`;

test.use({ viewport: { width: 360, height: 700 } });

test.describe("the text width check of a screen", () => {
  test("passes a heading and a paragraph that take the width", async ({ page }) => {
    await page.setContent(html(`<h1>Run in progress</h1><p>${SENTENCE}</p>`));
    expect(await textSqueezedOnScreen(page)).toEqual([]);
  });

  test("refuses a heading squeezed beside a fixed block (one word per line)", async ({ page }) => {
    await page.setContent(
      html(`<div style="display:flex"><div style="width:200px;flex:none"></div><h1>Run in progress</h1></div>`),
    );
    expect(await textSqueezedOnScreen(page)).toHaveLength(1);
    await expect(assertTextReadable(page, "demo")).rejects.toThrow(/demo: the text is squeezed/);
  });

  test("refuses a paragraph that reads three words a line", async ({ page }) => {
    await page.setContent(html(`<p style="width:90px">${SENTENCE}</p>`));
    expect(await textSqueezedOnScreen(page)).toHaveLength(1);
  });

  test("holds only the words per line of a list item, whose icon column is the kit's", async ({ page }) => {
    await page.setContent(html(`<ol><li style="margin-left:100px"><p>${SENTENCE}</p></li></ol>`));
    expect(await textSqueezedOnScreen(page)).toEqual([]);
  });

  test("lets a label cut to one line with an ellipsis pass, as a thread title in a sidebar", async ({ page }) => {
    await page.setContent(
      html(
        `<span style="display:block;width:200px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${SENTENCE}</span>`,
      ),
    );
    expect(await textSqueezedOnScreen(page)).toEqual([]);
  });

  test("still refuses the same narrow text when it wraps", async ({ page }) => {
    await page.setContent(html(`<span style="display:block;width:200px">${SENTENCE}</span>`));
    expect(await textSqueezedOnScreen(page)).toHaveLength(1);
  });

  test("names every squeezed block in one failure, not only the first", async ({ page }) => {
    await page.setContent(
      html(
        `<p style="width:90px">${SENTENCE}</p><p style="width:90px">Second block that is squeezed too, as narrow as the first</p>`,
      ),
    );
    await expect(assertTextReadable(page, "demo")).rejects.toThrow(/2 squeezed blocks[\s\S]*Second block/);
  });
});

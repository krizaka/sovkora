import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";
import { chromium } from "@playwright/test";
import { assertTextReadable, textSqueezedOnScreen } from "./text-width-on-screen.mjs";

const SENTENCE = "Every step runs on your machine and you can leave this page while it carries on";
const page = (inner) => `<body style="margin:0"><main style="width:360px">${inner}</main></body>`;

describe("the text width check of a screen (krizaka-internal#3834)", () => {
  let browser;
  let tab;
  before(async () => {
    browser = await chromium.launch();
    tab = await browser.newPage({ viewport: { width: 360, height: 700 } });
  });
  after(async () => browser.close());

  it("passes a heading and a paragraph that take the width", async () => {
    await tab.setContent(page(`<h1>Run in progress</h1><p>${SENTENCE}</p>`));
    assert.deepEqual(await textSqueezedOnScreen(tab), []);
  });

  it("refuses a heading squeezed beside a fixed block (« En cours… » one word per line)", async () => {
    await tab.setContent(
      page(`<div style="display:flex"><div style="width:200px;flex:none"></div><h1>Run in progress</h1></div>`),
    );
    const squeezed = await textSqueezedOnScreen(tab);
    assert.equal(squeezed.length, 1);
    await assert.rejects(() => assertTextReadable(tab, "demo"), /demo: the text is squeezed/);
  });

  it("refuses a paragraph that reads three words a line", async () => {
    await tab.setContent(page(`<p style="width:90px">${SENTENCE}</p>`));
    assert.equal((await textSqueezedOnScreen(tab)).length, 1);
  });

  it("holds only the words per line of a list item, whose icon column is the kit's", async () => {
    await tab.setContent(page(`<ol><li style="margin-left:100px"><p>${SENTENCE}</p></li></ol>`));
    assert.deepEqual(await textSqueezedOnScreen(tab), []);
  });
});

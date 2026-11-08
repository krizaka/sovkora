package com.krizaka.sovkora.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

/**
 * Shared Playwright lifecycle for UI end-to-end tests.
 *
 * <p><b>One</b> Playwright + Chromium browser is created for the whole suite and reused across
 * every UI IT (opt. #1 — browser reuse: launching Chromium is the expensive step). Each test class
 * gets its <b>own isolated</b> {@link BrowserContext}/{@link Page}
 * ({@code @TestInstance(PER_CLASS)} + instance fields), so classes can run <b>concurrently</b>
 * (opt. #3) while the {@code @Order}'d methods inside a class still share one page/session and run
 * sequentially.
 *
 * <p>The UI URL comes from the {@code ui.base.url} system property (wired from {@code
 * NEXT_PUBLIC_UI_URL} in the root {@code .env}).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractUiE2eTest {

  protected static final String UI_BASE_URL = System.getProperty("ui.base.url");

  /** Suite-wide Chromium — created once on first use, closed at JVM shutdown. */
  private static final class SharedBrowser {
    static final Playwright PLAYWRIGHT;
    static final Browser BROWSER;

    static {
      if (UI_BASE_URL == null || UI_BASE_URL.isBlank()) {
        throw new IllegalStateException(
            "UI URL missing. Ensure NEXT_PUBLIC_UI_URL is set in the root .env file.");
      }
      PLAYWRIGHT = Playwright.create();
      BROWSER = PLAYWRIGHT.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    BROWSER.close();
                    PLAYWRIGHT.close();
                  }));
    }

    private SharedBrowser() {}
  }

  protected BrowserContext context;
  protected Page page;

  @BeforeAll
  void openContext() {
    context =
        SharedBrowser.BROWSER.newContext(
            new Browser.NewContextOptions().setViewportSize(1440, 900).setLocale("en-US"));
    page = context.newPage();
  }

  @AfterAll
  void closeContext() {
    if (context != null) {
      context.close();
    }
  }
}

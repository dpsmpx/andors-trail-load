// Browser regression tests for the world map page (audit finding H4).
//
// Loads the pages built by build_pages.py in headless Chromium with an Android phone viewport and
// drives them the way DisplayWorldMapActivity does: load the page, scroll the visual viewport to
// the player (WebView.scrollTo), then evaluate START_LOADING_MAP_IMAGES_JS, which is read from
// DisplayWorldMapActivity.java so that the test always uses the app's current script.
//
// Usage: node scenarios.js <pages-dir> [--allow-eager]
//   --allow-eager  only record metrics, do not assert lazy loading (used for the "before" baseline)
// Environment: CHROMIUM_PATH selects a Chromium binary instead of Playwright's own.
// Prints a JSON report and exits with status 1 when an assertion fails.
const { chromium } = require('playwright');
const assert = require('assert');
const fs = require('fs');
const path = require('path');

const pagesDir = path.resolve(process.argv[2]);
const allowEager = process.argv.includes('--allow-eager');
const activitySource = fs.readFileSync(path.join(__dirname, '../../../AndorsTrail/app/src/main/java/com/gpl/rpg/AndorsTrail/activity/DisplayWorldMapActivity.java'), 'utf8');
const startJsMatch = /START_LOADING_MAP_IMAGES_JS\s*=\s*([\s\S]*?);\n/.exec(activitySource);
assert.ok(startJsMatch, 'START_LOADING_MAP_IMAGES_JS not found in DisplayWorldMapActivity.java');
const START_JS = [...startJsMatch[1].matchAll(/"((?:[^"\\]|\\.)*)"/g)].map(m => JSON.parse('"' + m[1] + '"')).join('');

async function open(browser, scenario, { query, noVisualViewport } = {}) {
  const dir = path.join(pagesDir, scenario);
  const meta = JSON.parse(fs.readFileSync(path.join(dir, 'scenario.json'), 'utf8'));
  const context = await browser.newContext({ viewport: { width: 412, height: 780 }, deviceScaleFactor: 2.625, isMobile: true, hasTouch: true });
  const page = await context.newPage();
  const errors = [];
  let requested = 0;
  page.on('pageerror', e => errors.push(e.message));
  page.on('request', r => { if (r.url().endsWith('.png')) requested++; });
  if (noVisualViewport) await page.addInitScript(() => Object.defineProperty(window, 'visualViewport', { value: undefined }));
  const url = 'file://' + path.join(dir, 'worldmap_world1.html') + (query === undefined ? '?' + meta.query : query);
  const started = Date.now();
  await page.goto(url, { waitUntil: 'load' }); // the WebView calls onPageFinished at this point
  const msToLoadEvent = Date.now() - started;
  const cdp = await context.newCDPSession(page);
  return { context, page, cdp, errors, meta, msToLoadEvent, requestedAtLoadEvent: requested, requested: () => requested };
}

const stats = page => page.evaluate(() => {
  const images = [...document.querySelectorAll('#maps img')];
  return {
    total: images.length,
    withSrc: images.filter(i => i.getAttribute('src')).length,
    loaded: images.filter(i => i.complete && i.naturalWidth > 0).length,
    hidden: images.filter(i => getComputedStyle(i).visibility === 'hidden').length,
    scale: window.visualViewport ? window.visualViewport.scale : null,
  };
});

// Ids of images that overlap the visual viewport but are not loaded.
const visibleNotLoaded = (page, ignore = []) => page.evaluate(ignore => {
  const vv = window.visualViewport;
  const origin = document.getElementById('maps').getBoundingClientRect();
  const x = vv.pageLeft - (origin.left + window.scrollX), y = vv.pageTop - (origin.top + window.scrollY);
  return [...document.querySelectorAll('#maps img')].filter(i => {
    if (ignore.includes(i.id)) return false;
    const l = parseInt(i.style.left), t = parseInt(i.style.top), w = parseInt(i.style.width), h = parseInt(i.style.height);
    const visible = l + w > x && t + h > y && l < x + vv.width && t < y + vv.height;
    return visible && !(i.complete && i.naturalWidth > 0);
  }).map(i => i.id);
}, ignore);

async function scrollBy(cdp, dx, dy) {
  await cdp.send('Input.synthesizeScrollGesture', { x: 200, y: 400, xDistance: -dx, yDistance: -dy, speed: 100000, gestureSourceType: 'mouse', preventFling: true });
}

// DisplayWorldMapActivity.recenter(): 100 ms after onPageFinished, scroll to the player and start loading.
async function recenter(s) {
  assert.deepStrictEqual(s.errors, [], 'script errors while loading the page');
  await s.page.waitForTimeout(100);
  const d = await s.page.evaluate(() => {
    const p = document.getElementById('playerPosition');
    return { x: parseInt(p.style.left) + 4 - visualViewport.width / 2 - visualViewport.pageLeft,
             y: parseInt(p.style.top) + 12 - visualViewport.height / 2 - visualViewport.pageTop };
  });
  await scrollBy(s.cdp, Math.max(0, d.x), Math.max(0, d.y));
  await s.page.evaluate(START_JS);
  await s.page.waitForTimeout(800);
}

(async () => {
  const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
  const report = {};
  try {
    // Late game, all 546 maps of world1 visited, player in Fallhaven.
    {
      const s = await open(browser, 'full');
      report.full_at_load_event = { msToLoadEvent: s.msToLoadEvent, imagesRequested: s.requestedAtLoadEvent, total: s.meta.maps };
      await recenter(s);
      const after = await stats(s.page);
      report.full_after_recenter = { ...after, imagesRequested: s.requested(), notLoadedButVisible: await visibleNotLoaded(s.page) };
      assert.deepStrictEqual(s.errors, [], 'script errors');
      assert.deepStrictEqual(report.full_after_recenter.notLoadedButVisible, [], 'visible images not loaded after recentering');
      if (!allowEager) {
        assert.ok(s.requestedAtLoadEvent < s.meta.maps / 4, `too many images requested before onPageFinished: ${s.requestedAtLoadEvent}`);
        assert.ok(after.withSrc < after.total / 4, `too many images loaded around the player: ${after.withSrc}`);
      }

      // Pan to Crossglen (world position 148,355; the segment starts at -59,-52).
      const current = await s.page.evaluate(() => [visualViewport.pageLeft, visualViewport.pageTop]);
      await scrollBy(s.cdp, (148 + 59 + 15) * 8 - 206 - current[0], (355 + 52 + 15) * 8 - 390 - current[1]);
      await s.page.waitForTimeout(800);
      report.full_after_pan = { ...(await stats(s.page)), notLoadedButVisible: await visibleNotLoaded(s.page) };
      assert.deepStrictEqual(report.full_after_pan.notLoadedButVisible, [], 'visible images not loaded after panning');

      // Zoom out to the minimum scale: the whole map is visible and must be loaded.
      await s.cdp.send('Emulation.setPageScaleFactor', { pageScaleFactor: 0.1 });
      await s.page.waitForTimeout(2500);
      report.full_zoomed_out = { ...(await stats(s.page)), notLoadedButVisible: await visibleNotLoaded(s.page) };
      assert.deepStrictEqual(report.full_zoomed_out.notLoadedButVisible, [], 'visible images not loaded after zooming out');
      assert.deepStrictEqual(s.errors, [], 'script errors');

      // Opening the map again (DisplayWorldMapActivity reloads the page in onResume).
      await s.cdp.send('Emulation.setPageScaleFactor', { pageScaleFactor: 1 });
      await s.page.reload({ waitUntil: 'load' });
      await s.page.evaluate(START_JS);
      await s.page.waitForTimeout(500);
      report.full_reopened = await stats(s.page);
      assert.deepStrictEqual(s.errors, [], 'script errors after reopening');
      await s.context.close();
    }

    // New game: only Crossglen visited. The blank page of the first PR attempt showed up here.
    {
      const s = await open(browser, 'new_game');
      await recenter(s);
      const player = await s.page.evaluate(() => {
        const p = document.getElementById('playerPosition');
        return { left: p.style.left, top: p.style.top, display: getComputedStyle(p).display };
      });
      report.new_game = { ...(await stats(s.page)), player };
      assert.deepStrictEqual(s.errors, [], 'script errors');
      assert.strictEqual(report.new_game.loaded, 1, 'the only map image must be loaded');
      assert.ok(player.left.endsWith('px') && player.top.endsWith('px'), 'the player marker must be positioned');
      await s.context.close();
    }

    // Some cached map images are missing: the others still load, the script keeps working.
    {
      const s = await open(browser, 'missing_images');
      await recenter(s);
      report.missing_images = { ...(await stats(s.page)), notLoadedButVisible: await visibleNotLoaded(s.page, s.meta.missing) };
      assert.deepStrictEqual(s.errors, [], 'script errors');
      assert.deepStrictEqual(report.missing_images.notLoadedButVisible, [], 'visible images not loaded');
      await s.context.close();
    }

    // The page script is broken: the activity's fallback loads and shows every image.
    {
      const s = await open(browser, 'broken_script');
      assert.ok(s.errors.length > 0, 'the broken script must fail');
      await s.page.evaluate(START_JS);
      await s.page.waitForTimeout(1500);
      report.broken_script = await stats(s.page);
      assert.strictEqual(report.broken_script.loaded, report.broken_script.total, 'fallback must load all images');
      assert.strictEqual(report.broken_script.hidden, 0, 'fallback must show all images');
      await s.context.close();
    }

    // No player position in the URL, and a WebView without window.visualViewport (Chrome < 61).
    {
      const s = await open(browser, 'full', { query: '' });
      await s.page.evaluate(START_JS);
      await s.page.waitForTimeout(800);
      report.no_player_position = { ...(await stats(s.page)), notLoadedButVisible: await visibleNotLoaded(s.page) };
      assert.deepStrictEqual(s.errors, [], 'script errors');
      assert.deepStrictEqual(report.no_player_position.notLoadedButVisible, [], 'visible images not loaded');
      await s.context.close();

      const old = await open(browser, 'full', { noVisualViewport: true });
      await old.page.evaluate(START_JS);
      await old.page.waitForTimeout(1500);
      report.no_visual_viewport = await stats(old.page);
      assert.deepStrictEqual(old.errors, [], 'script errors');
      assert.ok(report.no_visual_viewport.loaded > 0, 'images must load without visualViewport');
      await old.context.close();
    }
    console.log(JSON.stringify(report, null, 1));
  } catch (e) {
    console.log(JSON.stringify(report, null, 1));
    console.error('FAILED:', e.message);
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();

"use strict";

// Fullscreen for the YouTube player without the browser's fullscreen mode.
//
// GeckoView refuses real fullscreen for the page shown on the car screen, so the player's
// fullscreen button did nothing there. The page's fullscreen API is replaced: the player is
// stretched over the whole page (shown in the top layer, like real fullscreen) and the page is
// told it is in fullscreen. The car screen has no browser UI to hide anyway.
//
// The app's car buttons reach this script through a native messaging port: it can enter or
// leave fullscreen, and it is told when fullscreen changes.

const page = window.wrappedJSObject;
const MARK = "data-aayt-fullscreen";
const STYLE = `
  [${MARK}] {
    position: fixed !important;
    inset: 0 !important;
    width: 100% !important;
    height: 100% !important;
    max-width: none !important;
    max-height: none !important;
    margin: 0 !important;
    padding: 0 !important;
    border: 0 !important;
    overflow: hidden !important;
    transform: none !important;
    z-index: 2147483647 !important;
    background: #000 !important;
  }
  [${MARK}] video {
    width: 100% !important;
    height: 100% !important;
    top: 0 !important;
    left: 0 !important;
    object-fit: contain !important;
  }
  /* The blurred "ambient mode" copy of the video must not cover the picture. */
  [${MARK}] [class*="cinematic"], [${MARK}] [id*="cinematic"] {
    display: none !important;
  }
`;
// No double-tap zoom: on the car screen it blows the page up with no way back.
const PAGE_STYLE = `html { touch-action: manipulation !important; }`;

const FULLSCREEN_BUTTONS = "button.fullscreen-icon, .fullscreen-icon, .ytp-fullscreen-button";

let requested = null; // What the page asked to show in fullscreen (reported back to it).
let stretched = null; // What is actually stretched over the page: the player.
let port = null;

function addStyle(css) {
  const style = document.createElement("style");
  style.textContent = css;
  const parent = document.head || document.documentElement;
  if (parent) {
    parent.appendChild(style);
  } else {
    // Very early in loading there may be no document element yet.
    document.addEventListener("DOMContentLoaded", () => document.head.appendChild(style), {
      once: true,
    });
  }
}

let fullscreenStyleAdded = false;
function addFullscreenStyle() {
  if (fullscreenStyleAdded) return;
  addStyle(STYLE);
  fullscreenStyleAdded = true;
}

function playerOf(element) {
  // The video player itself, without the page around it (titles, the ambient-mode backdrop).
  return (
    element.closest(".html5-video-player") ||
    element.querySelector(".html5-video-player") ||
    element
  );
}

function notify(element) {
  for (const type of ["fullscreenchange", "webkitfullscreenchange"]) {
    element.dispatchEvent(new Event(type, { bubbles: true, composed: true }));
  }
  report();
}

function report() {
  if (!port) return;
  try {
    port.postMessage({ fullscreen: requested !== null });
  } catch (e) {
    port = null;
  }
}

function enter(element) {
  if (requested === element) return;
  if (requested) leave();
  addFullscreenStyle();
  requested = element;
  stretched = playerOf(element);
  stretched.setAttribute(MARK, "");
  try {
    // The top layer keeps the player above everything, whatever its parents' styles are.
    stretched.setAttribute("popover", "manual");
    stretched.showPopover();
  } catch (e) {
    stretched.removeAttribute("popover");
  }
  notify(element);
}

function leave() {
  const element = requested;
  if (!element) return;
  try {
    stretched.hidePopover();
  } catch (e) {
    // Not shown as a popover.
  }
  stretched.removeAttribute("popover");
  stretched.removeAttribute(MARK);
  requested = null;
  stretched = null;
  notify(element);
}

function fullscreenElement() {
  // The player can be removed by the page (e.g. when leaving the video).
  if (requested && !requested.isConnected) {
    stretched.removeAttribute(MARK);
    requested = null;
    stretched = null;
    report();
  }
  return requested;
}

function resolved() {
  return page.Promise.resolve();
}

function replaceMethod(proto, names, method) {
  for (const name of names) {
    if (name in proto) {
      exportFunction(method, proto, { defineAs: name });
    }
  }
}

function replaceGetter(proto, names, getter) {
  for (const name of names) {
    if (name in proto) {
      Reflect.defineProperty(proto, name, {
        configurable: true,
        enumerable: true,
        get: exportFunction(getter, page),
      });
    }
  }
}

replaceMethod(
  page.Element.prototype,
  ["requestFullscreen", "mozRequestFullScreen", "webkitRequestFullscreen", "webkitRequestFullScreen"],
  function () {
    enter(this);
    return resolved();
  }
);
replaceMethod(
  page.Document.prototype,
  ["exitFullscreen", "mozCancelFullScreen", "webkitExitFullscreen", "webkitCancelFullScreen"],
  function () {
    leave();
    return resolved();
  }
);
replaceGetter(
  page.Document.prototype,
  ["fullscreenElement", "mozFullScreenElement", "webkitFullscreenElement", "webkitCurrentFullScreenElement"],
  fullscreenElement
);
replaceGetter(
  page.Document.prototype,
  ["fullscreen", "mozFullScreen", "webkitIsFullScreen"],
  () => fullscreenElement() !== null
);
replaceGetter(
  page.Document.prototype,
  ["fullscreenEnabled", "mozFullScreenEnabled", "webkitFullscreenEnabled"],
  () => true
);

// The player locks the screen to landscape in fullscreen; the car screen already is.
if (page.ScreenOrientation) {
  replaceMethod(page.ScreenOrientation.prototype, ["lock"], resolved);
  replaceMethod(page.ScreenOrientation.prototype, ["unlock"], () => {});
}

addStyle(PAGE_STYLE);

// Car buttons. Only the car screen answers; elsewhere the port just closes.
// (Calls from here would reach the browser's own fullscreen API, not the replacement above.)
function onAppMessage(message) {
  if (message.action === "exitFullscreen") {
    if (fullscreenElement()) leave();
  } else if (message.action === "enterFullscreen") {
    if (fullscreenElement()) return;
    // The player's own button keeps its controls in step with fullscreen.
    const button = document.querySelector(FULLSCREEN_BUTTONS);
    if (button) button.click();
    if (fullscreenElement()) return;
    const player = document.querySelector(".html5-video-player");
    if (player) enter(player);
  }
}

try {
  port = browser.runtime.connectNative("aayt");
  port.onMessage.addListener(onAppMessage);
  port.onDisconnect.addListener(() => {
    port = null;
  });
  report();
} catch (e) {
  port = null;
}

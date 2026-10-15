"use strict";

// Fullscreen for the YouTube player without the browser's fullscreen mode.
//
// GeckoView refuses real fullscreen for the page shown on the car screen, so the player's
// fullscreen button did nothing there. The page's fullscreen API is replaced: the requested
// element is stretched over the whole page (shown in the top layer, like real fullscreen) and
// the page is told it is in fullscreen. The car screen has no browser UI to hide anyway.

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
`;

let current = null;
let styleAdded = false;

function addStyle() {
  if (styleAdded) return;
  const style = document.createElement("style");
  style.textContent = STYLE;
  (document.head || document.documentElement).appendChild(style);
  styleAdded = true;
}

function notify(element) {
  for (const type of ["fullscreenchange", "webkitfullscreenchange"]) {
    element.dispatchEvent(new Event(type, { bubbles: true, composed: true }));
  }
}

function enter(element) {
  // Stretch the whole player rather than the bare video, so its controls stay usable.
  if (element.localName === "video") {
    element = element.closest(".html5-video-player") || element;
  }
  if (current === element) return;
  if (current) leave();
  addStyle();
  current = element;
  element.setAttribute(MARK, "");
  try {
    // The top layer keeps the player above everything, whatever its parents' styles are.
    element.setAttribute("popover", "manual");
    element.showPopover();
  } catch (e) {
    element.removeAttribute("popover");
  }
  notify(element);
}

function leave() {
  const element = current;
  if (!element) return;
  current = null;
  try {
    element.hidePopover();
  } catch (e) {
    // Not shown as a popover.
  }
  element.removeAttribute("popover");
  element.removeAttribute(MARK);
  notify(element);
}

function fullscreenElement() {
  // The player can be removed by the page (e.g. when leaving the video).
  if (current && !current.isConnected) {
    current.removeAttribute(MARK);
    current = null;
  }
  return current;
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

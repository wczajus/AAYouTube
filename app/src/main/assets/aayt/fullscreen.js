"use strict";

// Fullscreen for the YouTube player without the browser's fullscreen mode.
//
// GeckoView refuses real fullscreen for the page shown on the car screen, so the player's
// fullscreen button did nothing there. The page's fullscreen API is replaced: the player holding
// the video being watched is stretched over the whole page (shown in the top layer, like real
// fullscreen) and the page is told it is in fullscreen. The car screen has no browser UI to
// hide anyway.
//
// The app's car buttons reach this script through a native messaging port: it can enter or
// leave fullscreen, it is told when fullscreen changes, and it gets short diagnostic lines for
// the log (tag AAYouTubeCar).

const page = window.wrappedJSObject;
const MARK = "data-aayt-fullscreen";
const PLAYER = ".html5-video-player";
const FULLSCREEN_BUTTONS = "button.fullscreen-icon, .fullscreen-icon, .ytp-fullscreen-button";
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
  /* The page sizes the video in pixels inside a container of zero height, so both are
     stretched: a percentage height of that container would leave the video 0 px high. */
  [${MARK}] .html5-video-container {
    position: absolute !important;
    inset: 0 !important;
    width: 100% !important;
    height: 100% !important;
  }
  [${MARK}] video {
    position: absolute !important;
    inset: 0 !important;
    width: 100% !important;
    height: 100% !important;
    object-fit: contain !important;
  }
  /* The blurred "ambient mode" copy of the video must not cover the picture. */
  [${MARK}] [class*="cinematic"] canvas, [${MARK}] canvas[class*="cinematic"] {
    display: none !important;
  }
`;
// No double-tap zoom: on the car screen it blows the page up with no way back.
const PAGE_STYLE = `html { touch-action: manipulation !important; }`;

let requested = null; // What the page asked to show in fullscreen (reported back to it).
let stretched = null; // What is actually stretched over the page: the player.
let watchdog = 0;
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

function send(message) {
  if (!port) return false;
  try {
    port.postMessage(message);
    return true;
  } catch (e) {
    port = null;
    return false;
  }
}

function debug(text) {
  if (!send({ log: text })) console.log("[aayt] " + text);
}

function describe(element) {
  if (!element) return "none";
  const rect = element.getBoundingClientRect();
  const id = element.id ? "#" + element.id : "";
  const classes =
    typeof element.className === "string" && element.className.trim()
      ? "." + element.className.trim().split(/\s+/).slice(0, 4).join(".")
      : "";
  return (
    `${element.localName}${id}${classes} ` +
    `${Math.round(rect.width)}x${Math.round(rect.height)}@${Math.round(rect.left)},${Math.round(rect.top)}`
  );
}

function describeVideo(video) {
  return (
    `${describe(video)} paused=${video.paused} ready=${video.readyState} ` +
    `src=${video.videoWidth}x${video.videoHeight} t=${Math.round(video.currentTime)}`
  );
}

function allVideos() {
  const list = document.querySelectorAll("video");
  const videos = [];
  for (let i = 0; i < list.length; i++) videos.push(list[i]);
  return videos;
}

// The video being watched: a visible, playing, loaded one wins. The page can also hold hidden
// preview players, so the first player in the page is not necessarily it.
function mainVideo() {
  let best = null;
  let bestScore = -1;
  for (const video of allVideos()) {
    const rect = video.getBoundingClientRect();
    const score =
      (rect.width > 0 && rect.height > 0 ? 4 : 0) + (video.paused ? 0 : 2) + (video.readyState >= 2 ? 1 : 0);
    if (score > bestScore) {
      best = video;
      bestScore = score;
    }
  }
  return best;
}

function playerFor(element) {
  const video = mainVideo();
  if (video) return video.closest(PLAYER) || video.parentElement;
  return element.closest(PLAYER) || element.querySelector(PLAYER) || element;
}

function stretch(element) {
  stretched = element;
  element.setAttribute(MARK, "");
  try {
    // The top layer keeps the player above everything, whatever its parents' styles are.
    element.setAttribute("popover", "manual");
    element.showPopover();
  } catch (e) {
    element.removeAttribute("popover");
    debug("no top layer: " + e);
  }
}

function unstretch() {
  const element = stretched;
  if (!element) return;
  stretched = null;
  try {
    element.hidePopover();
  } catch (e) {
    // Not shown as a popover.
  }
  element.removeAttribute("popover");
  element.removeAttribute(MARK);
}

function notify(element) {
  for (const type of ["fullscreenchange", "webkitfullscreenchange"]) {
    element.dispatchEvent(new Event(type, { bubbles: true, composed: true }));
  }
  report();
}

function report() {
  send({ fullscreen: requested !== null });
}

function enter(element, by) {
  if (requested === element) return;
  if (requested) leave();
  addFullscreenStyle();
  requested = element;
  stretch(playerFor(element));
  debug(`fullscreen by ${by}: asked ${describe(element)}, showing ${describe(stretched)}`);
  watchdog = setInterval(keepStretched, 1000);
  setTimeout(logLayout, 1500);
  notify(element);
}

function leave() {
  const element = requested;
  if (!element) return;
  clearInterval(watchdog);
  unstretch();
  requested = null;
  notify(element);
}

// The page may re-render or move the player while it thinks it is in fullscreen.
function keepStretched() {
  if (!requested) return;
  if (!requested.isConnected) {
    debug("fullscreen element removed");
    leave();
    return;
  }
  if (!stretched || !stretched.isConnected || !stretched.querySelector("video")) {
    const player = playerFor(requested);
    debug(`player changed to ${describe(player)}`);
    unstretch();
    stretch(player);
  } else if (stretched.hasAttribute("popover") && !stretched.matches(":popover-open")) {
    debug("top layer lost, showing again");
    try {
      stretched.showPopover();
    } catch (e) {
      // Logged by the next check.
    }
  }
}

// What the car screen shows in fullscreen, for the log: the stretched player, the video in it
// and every element between them (a hidden one there means a black screen).
function logLayout() {
  if (!stretched) return;
  const open = stretched.hasAttribute("popover") && stretched.matches(":popover-open");
  debug(`showing ${describe(stretched)} topLayer=${open}`);
  const video = stretched.querySelector("video");
  for (let node = video; node && node !== stretched.parentElement; node = node.parentElement) {
    const style = window.getComputedStyle(node);
    debug(
      `  ${describe(node)} display=${style.display} visibility=${style.visibility} ` +
        `opacity=${style.opacity} z=${style.zIndex} position=${style.position}`
    );
  }
  for (let i = 0; i < stretched.children.length; i++) debug(`  child ${describe(stretched.children[i])}`);
  for (const other of allVideos()) debug(`  video ${describeVideo(other)}`);
}

function fullscreenElement() {
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
    enter(this, "page");
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
    const video = mainVideo();
    if (!video) {
      debug("no video to show in fullscreen");
      return;
    }
    // The player's own button keeps its controls in step with fullscreen.
    const player = video.closest(PLAYER);
    const around = (player && player.parentElement) || document;
    const button = around.querySelector(FULLSCREEN_BUTTONS);
    if (button) button.click();
    if (!fullscreenElement()) enter(player || video, "car button");
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

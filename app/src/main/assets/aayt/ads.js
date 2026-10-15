"use strict";

// Fallback for YouTube video ads that get past uBlock Origin: while the player shows an ad, the
// ad plays muted at the highest speed and its skip button is pressed. Runs in the same scope as
// fullscreen.js (debug() comes from there).

const AD_PLAYER = ".html5-video-player.ad-showing";
const SKIP_BUTTONS = ".ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button";
const AD_RATE = 16;

let adVideo = null; // The video being sped up, with its settings from before the ad.

function checkAds() {
  const player = document.querySelector(AD_PLAYER);
  const video = player && player.querySelector("video");
  if (video) {
    if (!adVideo || adVideo.video !== video) {
      adVideo = { video, muted: video.muted, rate: video.playbackRate };
      debug("ad playing, skipping it");
    }
    const button = player.querySelector(SKIP_BUTTONS);
    if (button) button.click();
    video.muted = true;
    if (video.playbackRate !== AD_RATE) video.playbackRate = AD_RATE;
  } else if (adVideo) {
    // The same video element goes on with the film: give it its sound and speed back.
    adVideo.video.muted = adVideo.muted;
    adVideo.video.playbackRate = adVideo.rate;
    adVideo = null;
  }
}

setInterval(checkAds, 500);

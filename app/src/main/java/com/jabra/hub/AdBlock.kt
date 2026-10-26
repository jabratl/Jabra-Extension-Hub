package com.jabra.hub

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Small built-in ad / popup / tracker blocker for the Hub browser.
 * Blocks sub-requests to well-known ad networks (never the page itself) and hides common ad boxes.
 */
object AdBlock {

    private val hosts: Set<String> = hashSetOf(
        // Google / big ad exchanges
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "pagead2.googlesyndication.com", "googletagservices.com", "2mdn.net", "adnxs.com",
        "adsrvr.org", "rubiconproject.com", "pubmatic.com", "openx.net", "criteo.com", "criteo.net",
        "casalemedia.com", "indexww.com", "smartadserver.com", "advertising.com", "adform.net",
        "amazon-adsystem.com", "moatads.com", "adsafeprotected.com", "serving-sys.com",
        "bidswitch.net", "contextweb.com", "lijit.com", "sharethrough.com", "teads.tv",
        "media.net", "yieldmo.com", "33across.com", "sovrn.com", "gumgum.com", "triplelift.com",
        // native / content-recommendation ads
        "taboola.com", "outbrain.com", "revcontent.com", "mgid.com", "zemanta.com", "content.ad",
        "adblade.com", "nativeads.com",
        // pop-up / pop-under / adult-site ad networks
        "popads.net", "popcash.net", "propellerads.com", "propellerclick.com", "onclickads.net",
        "exoclick.com", "exosrv.com", "exdynsrv.com", "juicyads.com", "trafficjunky.com",
        "trafficfactory.biz", "adsterra.com", "adsterratools.com", "hilltopads.net", "hilltopads.com",
        "clickadu.com", "clickaine.com", "adcash.com", "adk2.com", "adk2x.com", "ad-maven.com",
        "admaven.com", "richpush.co", "pushame.com", "pushhouse.com", "zeropark.com", "realsrv.com",
        "a-ads.com", "ads.com", "adspyglass.com", "adskeeper.com", "adxpansion.com", "bidvertiser.com",
        "plugrush.com", "tsyndicate.com", "mondiad.com", "monetag.com", "ad-score.com", "ero-advertising.com",
        "ads.exoclick.com", "syndication.exosrv.com", "go.oclasrv.com", "oclasrv.com", "aclib.net",
        "tapecontent.net", "betterstat.com", "pushwhy.com", "notifpush.com", "acscdn.com",
        // trackers / analytics that only slow pages down
        "scorecardresearch.com", "quantserve.com", "chartbeat.net", "hotjar.com", "mouseflow.com",
        "krxd.net", "bluekai.com", "demdex.net", "everesttech.net", "adsco.re"
    )

    /** Path fragments that are almost never anything but ads. */
    private val pathParts = listOf("/pagead/", "/adserver/", "/popunder", "/popads", "/adframe", "/banner_ads/")

    private fun hostBlocked(host: String?): Boolean {
        val labels = (host ?: return false).lowercase().split('.')
        for (i in 0 until labels.size - 1) {
            if (hosts.contains(labels.subList(i, labels.size).joinToString("."))) return true
        }
        return false
    }

    fun isAdHost(host: String?): Boolean = hostBlocked(host)

    /** Sub-resource check. Never call this for the main page request. */
    fun isBlocked(url: String, host: String?): Boolean {
        if (hostBlocked(host)) return true
        val low = url.lowercase()
        for (p in pathParts) if (low.contains(p)) return true
        return false
    }

    fun empty(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    /**
     * Injected at document start. Hides common ad boxes (only when the blocker is on) and reports
     * media the page uses: fetch / XHR calls to .m3u8 or media files, and <video>/<audio>/<source> tags.
     */
    val PAGE_SCRIPT = """
(function(){
  if (window.__jbInit) return; window.__jbInit = 1;
  var S = window.JabraSniff;
  function send(u, h){ try { if (S && u && /^https?:/i.test(u)) S.found(String(u), h || ''); } catch(e){} }
  function abs(u){ try { return new URL(String(u), location.href).href; } catch(e){ return ''; } }
  function chk(u){
    if (!u) return; u = String(u);
    if (/\.(m3u8|mp4|webm|mp3|m4a|vtt|srt)(\?|#|${'$'})/i.test(u) || /\.m3u8/i.test(u)) send(abs(u), '');
  }
  try {
    var of = window.fetch;
    if (of) window.fetch = function(a){ try { chk(typeof a === 'string' ? a : (a && a.url)); } catch(e){} return of.apply(this, arguments); };
    var ox = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(m, u){ try { chk(u); } catch(e){} return ox.apply(this, arguments); };
  } catch(e){}
  function scan(){
    try {
      var n = document.querySelectorAll('video,audio,source');
      for (var i = 0; i < n.length; i++) { var s = n[i].currentSrc || n[i].src; if (s) send(s, 'av'); }
    } catch(e){}
  }
  setInterval(scan, 2500);
  document.addEventListener('DOMContentLoaded', scan);
  try {
    if (S && S.adblock()) {
      var css = 'ins.adsbygoogle,.adsbygoogle,[id^="google_ads_"],[id^="div-gpt-ad"],[id^="aswift_"],' +
        'iframe[src*="doubleclick.net"],iframe[src*="googlesyndication"],iframe[src*="exoclick"],iframe[src*="juicyads"],' +
        'iframe[src*="adsterra"],iframe[src*="popads"],a[href*="popads.net"],[class*="ad-banner"],[class*="adbanner"],' +
        '[id*="adbanner"],[class*="sponsored-ad"],[data-ad-slot],[data-ad-client]{display:none!important}';
      var add = function(){
        var s = document.createElement('style'); s.textContent = css;
        (document.head || document.documentElement).appendChild(s);
      };
      add();
    }
  } catch(e){}
})();
"""
}

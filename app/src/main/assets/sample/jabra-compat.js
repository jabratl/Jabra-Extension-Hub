/*
 * Jabra Extension Hub API v1 compatibility helper.
 * Include this file before plugin code. All native methods are feature-detected.
 * A fallback is used only when it is reasonable; the helper never fakes success.
 */
(function (global) {
  'use strict';
  function api() { return global.JabraAPI || null; }
  function hostInfo() {
    try {
      var bridge = api();
      if (!bridge || typeof bridge.info !== 'function') return null;
      return JSON.parse(bridge.info());
    } catch (_) { return null; }
  }
  function has(name) {
    var info = hostInfo();
    return !!(info && Array.isArray(info.capabilities) && info.capabilities.indexOf(name) >= 0);
  }
  function blobBase64(blob) {
    return new Promise(function (resolve, reject) {
      var reader = new FileReader();
      reader.onload = function () { resolve(String(reader.result).split(',')[1] || ''); };
      reader.onerror = function () { reject(reader.error || new Error('Could not read file data')); };
      reader.readAsDataURL(blob);
    });
  }
  async function saveBlob(name, blob) {
    var bridge = api();
    if (bridge && typeof bridge.exportBase64File === 'function') {
      var ok = bridge.exportBase64File(name, await blobBase64(blob), blob.type || 'application/octet-stream');
      if (!ok) throw new Error('Jabra Hub export failed. Try Share or retry.');
      return { ok: true, method: 'hub-export' };
    }
    var url = URL.createObjectURL(blob);
    try {
      var a = document.createElement('a'); a.href = url; a.download = name; a.style.display = 'none';
      document.body.appendChild(a); a.click(); a.remove();
      return { ok: true, method: 'browser-download', fallbackMayVary: true };
    } finally { setTimeout(function () { URL.revokeObjectURL(url); }, 10000); }
  }
  async function shareBlob(name, blob) {
    var bridge = api();
    if (bridge && typeof bridge.shareBase64File === 'function') {
      var ok = bridge.shareBase64File(name, await blobBase64(blob), blob.type || 'application/octet-stream');
      if (!ok) throw new Error('Jabra Hub share failed. Use Export instead.');
      return { ok: true, method: 'hub-share' };
    }
    var file = new File([blob], name, { type: blob.type || 'application/octet-stream' });
    if (navigator.share && navigator.canShare && navigator.canShare({ files: [file] })) {
      await navigator.share({ files: [file], title: name });
      return { ok: true, method: 'web-share' };
    }
    return saveBlob(name, blob);
  }
  function readSelectedFile(file) {
    return new Promise(function (resolve, reject) {
      if (!file) { reject(new Error('No file selected')); return; }
      var reader = new FileReader();
      reader.onload = function () { resolve(String(reader.result || '')); };
      reader.onerror = function () { reject(reader.error || new Error('Could not read selected file')); };
      reader.readAsText(file);
    });
  }
  function storageGet(key) {
    var bridge = api();
    if (bridge && typeof bridge.storageGet === 'function') return bridge.storageGet(String(key));
    try { return global.localStorage.getItem(String(key)); } catch (_) { return null; }
  }
  function storageSet(key, value) {
    var bridge = api();
    if (bridge && typeof bridge.storageSet === 'function') return bridge.storageSet(String(key), String(value)) === true;
    try { global.localStorage.setItem(String(key), String(value)); return true; } catch (_) { return false; }
  }
  function storageRemove(key) {
    var bridge = api();
    if (bridge && typeof bridge.storageRemove === 'function') return bridge.storageRemove(String(key)) === true;
    try { global.localStorage.removeItem(String(key)); return true; } catch (_) { return false; }
  }
  function recordDiagnostic(event, detail) {
    var bridge = api();
    if (!bridge || typeof bridge.recordDiagnostic !== 'function') return false;
    try { return bridge.recordDiagnostic(String(event || 'extension-event'), String(detail || '')) === true; }
    catch (_) { return false; }
  }
  function copyText(text) {
    var bridge = api();
    if (bridge && typeof bridge.copyText === 'function') return bridge.copyText(String(text)) === true;
    if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
      return navigator.clipboard.writeText(String(text)).then(function () { return true; });
    }
    return false;
  }
  function shareText(text, title) {
    var bridge = api();
    if (bridge && typeof bridge.shareText === 'function') return bridge.shareText(String(text), String(title || 'Share')) === true;
    if (navigator.share) return navigator.share({ text: String(text), title: String(title || 'Share') }).then(function () { return true; });
    return false;
  }
  global.JabraCompat = {
    apiVersion: 1,
    hostInfo: hostInfo,
    has: has,
    saveBlob: saveBlob,
    shareBlob: shareBlob,
    readSelectedFile: readSelectedFile,
    storageGet: storageGet,
    storageSet: storageSet,
    storageRemove: storageRemove,
    recordDiagnostic: recordDiagnostic,
    copyText: copyText,
    shareText: shareText
  };
})(window);

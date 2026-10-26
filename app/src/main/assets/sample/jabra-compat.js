/*
 * Jabra Extension Hub API v1 compatibility helper.
 * Copy this file into an extension package and include it before the app script.
 * It intentionally uses feature detection and safe fallbacks; it does not emulate APIs
 * that the host does not provide.
 */
(function (global) {
  'use strict';
  function hostInfo() {
    try {
      if (!global.JabraAPI || typeof global.JabraAPI.info !== 'function') return null;
      return JSON.parse(global.JabraAPI.info());
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
    if (global.JabraAPI && typeof global.JabraAPI.exportBase64File === 'function') {
      var ok = global.JabraAPI.exportBase64File(name, await blobBase64(blob), blob.type || 'application/octet-stream');
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
    if (global.JabraAPI && typeof global.JabraAPI.shareBase64File === 'function') {
      var ok = global.JabraAPI.shareBase64File(name, await blobBase64(blob), blob.type || 'application/octet-stream');
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
  global.JabraCompat = {
    apiVersion: 1,
    hostInfo: hostInfo,
    has: has,
    saveBlob: saveBlob,
    shareBlob: shareBlob,
    readSelectedFile: readSelectedFile
  };
})(window);

// Mesin browsing. Native: InAppBrowser (WebView penuh, tanpa batasan X-Frame-Options). Web/dev: iframe.
const cap=window.Capacitor,native=!!(cap&&cap.isNativePlatform&&cap.isNativePlatform());
const IAB=native?cap.Plugins.InAppBrowser:null;
let onUrl=()=>{},onClose=()=>{};
if(IAB){IAB.addListener('urlChangeEvent',e=>onUrl(e.url));IAB.addListener('closeEvent',()=>onClose())}
export const isNative=native;
export const engine={
 set onUrl(f){onUrl=f},set onClose(f){onClose=f},
 async open(url){
  if(IAB){try{await IAB.openWebView({url,title:'Nevra',toolbarType:'navigation',showReloadButton:true,enabledSafeTopMargin:true});return}catch(e){console.warn(e)}}
  document.getElementById('fr').src=url},
 async inject(code){if(IAB)try{await IAB.executeScript({code});return true}catch(e){console.warn(e)}return false},
 async close(){if(IAB)try{await IAB.close()}catch(e){}}
};

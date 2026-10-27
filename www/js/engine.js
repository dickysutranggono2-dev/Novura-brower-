// Mesin browsing. Urutan: InAppBrowser -> Chrome Custom Tabs (@capacitor/browser) -> iframe (hanya dev web).
const cap=window.Capacitor,native=!!(cap&&cap.isNativePlatform&&cap.isNativePlatform());
export const isNative=native;
export const plugin=n=>{if(!native)return null;try{const p=cap.Plugins&&cap.Plugins[n];if(p)return p}catch(e){}try{return cap.registerPlugin(n)}catch(e){return null}};
const IAB=plugin('InAppBrowser'),BR=plugin('Browser');
let onUrl=()=>{},onClose=()=>{};
const listen=(p,n,f)=>{try{const r=p&&p.addListener(n,f);r&&r.catch&&r.catch(()=>{})}catch(e){}};
listen(IAB,'urlChangeEvent',e=>onUrl(e.url));listen(IAB,'closeEvent',()=>onClose());listen(BR,'browserFinished',()=>onClose());
const msg=e=>String((e&&e.message)||e);
export const engine={
 err:'',info:'',
 set onUrl(f){onUrl=f},set onClose(f){onClose=f},
 async open(url){
  this.err='';
  if(IAB){try{await IAB.openWebView({url,title:'Nevra',toolbarType:'navigation',showReloadButton:true,enabledSafeTopMargin:true});this.info='InAppBrowser';return true}catch(e){this.err='InAppBrowser gagal: '+msg(e)}}
  else if(native)this.err='plugin InAppBrowser tidak ditemukan';
  if(BR){try{await BR.open({url});this.info='Chrome Custom Tabs ('+this.err+')';return true}catch(e){this.err+=' | Browser gagal: '+msg(e)}}
  this.info='iframe ('+this.err+')';
  document.getElementById('fr').src=url;return false},
 async inject(code){if(IAB)try{await IAB.executeScript({code});return true}catch(e){console.warn(e)}return false},
 async close(){if(IAB)try{await IAB.close()}catch(e){}}
};

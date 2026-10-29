// Mesin browsing: WebView native Nevra (plugin NevraWeb) di antara bilah atas dan toolbar bawah. Mode web/dev: iframe.
const cap=window.Capacitor,native=!!(cap&&cap.isNativePlatform&&cap.isNativePlatform());
export const isNative=native;
export const plugin=n=>{if(!native)return null;try{const p=cap.Plugins&&cap.Plugins[n];if(p)return p}catch(e){}try{return cap.registerPlugin(n)}catch(e){return null}};
const NW=plugin('NevraWeb'),ev={};
['pageStarted','pageFinished','progress','download','downloadError','blocked'].forEach(n=>{try{const r=NW&&NW.addListener(n,e=>ev[n]&&ev[n](e));r&&r.catch&&r.catch(()=>{})}catch(e){}});
const px=b=>({top:Math.round(b.top*devicePixelRatio),bottom:Math.round(b.bottom*devicePixelRatio)});
const call=(m,a)=>{try{return Promise.resolve(NW[m](a||{})).catch(e=>{engine.err=String((e&&e.message)||e)})}catch(e){engine.err=String(e);return Promise.resolve()}};
const fr=()=>document.getElementById('fr');
export const engine={
 native:!!NW,err:'',
 on(n,f){ev[n]=f},
 open(url,b){if(NW)return call('open',{url,...px(b)});fr().src=url;return Promise.resolve()},
 show(b){if(NW)call('show',px(b))},
 hide(){if(NW)call('hide')},
 back(){if(NW)call('back');else try{fr().contentWindow.history.back()}catch(e){}},
 fwd(){if(NW)call('forward');else try{fr().contentWindow.history.forward()}catch(e){}},
 reload(){if(NW)call('reload');else fr().src=fr().src},
 openDownloads(){if(NW)call('openDownloads')},
 toast(t){if(NW)call('toast',{text:t})},
 async downloads(){if(!NW)return[];try{const r=await NW.queryDownloads({});return r.items||[]}catch(x){engine.err='unduhan: '+String((x&&x.message)||x);return[]}},
 fileOp(m,id){return NW?NW[m]({id}):Promise.reject(new Error('tidak didukung'))}
};

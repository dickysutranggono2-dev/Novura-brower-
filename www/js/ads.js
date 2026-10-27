// AdMob lewat @capacitor-community/admob. ID TEST dipakai sampai ADMOB_BANNER_ID diganti.
import {plugin} from './engine.js';
export const ADMOB_BANNER_ID='ca-app-pub-3940256099942544/6300978111'; // ID test Google
const A=plugin('AdMob');
export const adStatus={s:A?'menunggu':'tidak aktif (bukan di aplikasi Android)'};
let h=0;
const css=v=>document.documentElement.style.setProperty('--adh',v+'px');
const setH=v=>{h=v;css(v)};
const st=t=>{adStatus.s=t;const e=document.getElementById('adst');if(e)e.textContent='Status iklan: '+t};
const on=(n,f)=>{try{const p=A.addListener(n,f);p&&p.catch&&p.catch(()=>{})}catch(e){}};
export async function initAds(){
 if(!A)return false;
 const ph=document.getElementById('ad');if(ph)ph.style.display='none';
 on('bannerAdLoaded',()=>{st('banner dimuat');if(!h)setH(56)});
 on('bannerAdSizeChanged',s=>{if(s&&s.height)setH(Math.ceil(s.height))});
 on('bannerAdFailedToLoad',e=>{st('gagal dimuat ('+((e&&(e.code||e.message))||'?')+')');setH(0)});
 try{st('inisialisasi');await A.initialize();st('meminta banner');
  await A.showBanner({adId:ADMOB_BANNER_ID,adSize:'ADAPTIVE_BANNER',position:'BOTTOM_CENTER',margin:0,isTesting:true});return true}
 catch(e){st('error: '+((e&&e.message)||e));return false}}
export const hideAds=()=>{if(A){Promise.resolve(A.hideBanner()).catch(()=>{});css(0)}};
export const showAds=()=>{if(A){Promise.resolve(A.resumeBanner()).catch(()=>{});css(h)}};
st(adStatus.s);

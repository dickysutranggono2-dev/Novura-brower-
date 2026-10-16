// AdMob lewat @capacitor-community/admob. ID TEST dipakai sampai ADMOB_BANNER_ID diganti.
export const ADMOB_BANNER_ID='ca-app-pub-3940256099942544/6300978111'; // ID test Google
const cap=window.Capacitor,A=cap?.isNativePlatform?.()?cap.Plugins.AdMob:null;
export async function initAds(){
 if(!A)return false;
 try{await A.initialize();await A.showBanner({adId:ADMOB_BANNER_ID,adSize:'ADAPTIVE_BANNER',position:'BOTTOM_CENTER',margin:64,isTesting:true});
  document.getElementById('ad').style.display='none';return true}catch(e){console.warn('AdMob',e);return false}}
export const hideAds=()=>A?.hideBanner().catch(()=>{});
export const showAds=()=>A?.resumeBanner().catch(()=>{});

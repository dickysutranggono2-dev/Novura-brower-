import {ic,paint} from './icons.js';import {engine,isNative} from './engine.js';import {initAds,hideAds,showAds} from './ads.js';import {ext} from './ext.js';import {APP} from './config.js';
const $=s=>document.querySelector(s),KEY='nuvora.v1';
const S=Object.assign({tabs:[],active:0,bm:[],hist:[],dark:false,eng:'g',sc:[]},JSON.parse(localStorage.getItem(KEY)||'{}'));
S.ext=Object.assign({inst:{},on:{},air:[],wal:[],priv:{http:1,block:0,clear:0}},S.ext);
const save=()=>localStorage.setItem(KEY,JSON.stringify(S));
const SE={g:'https://www.google.com/search?q=',d:'https://duckduckgo.com/?q=',b:'https://www.bing.com/search?q='};
const DEF=[['Google','https://www.google.com','#4285f4'],['YouTube','https://m.youtube.com','#ef4444'],['Facebook','https://m.facebook.com','#1877f2'],['Instagram','https://www.instagram.com','#d946ef'],['X','https://x.com','#111827'],['TikTok','https://www.tiktok.com','#0f172a'],['ChatGPT','https://chatgpt.com','#10a37f']];
if(!S.sc.length)S.sc=DEF.map(([n,u,c])=>({n,u,c}));
const NEWS=[['Contoh berita: Teknologi','Sumber placeholder','tech'],['Contoh berita: Ekonomi','Sumber placeholder','eco'],['Contoh berita: Olahraga','Sumber placeholder','sport']];
const IC={'google.com':'google','youtube.com':'youtube','facebook.com':'facebook','instagram.com':'instagram','x.com':'x','tiktok.com':'tiktok','chatgpt.com':'chatgpt'};
const ico=s=>{const h=host(s.u),k=Object.keys(IC).find(d=>h===d||h.endsWith('.'+d));return k?`<img src="img/ic-${IC[k]}.svg" alt="" width="46" height="46">`:`<i style="background:${s.c}">${esc(s.n[0])}</i>`};
let cur='home';
const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const host=u=>{try{return new URL(u).hostname.replace(/^www\./,'')}catch{return u}};
function toast(m){const t=$('#toast');t.textContent=m;t.classList.add('on');setTimeout(()=>t.classList.remove('on'),1800)}
// Keamanan: hanya http(s); skema lain (javascript:, file:, intent:) ditolak.
function norm(v){v=v.trim();if(!v)return null;
 if(/^[a-z][a-z0-9+.-]*:/i.test(v)&&!/^https?:\/\//i.test(v)&&!/^[\w.-]+:\d+/.test(v)){toast('Skema tidak diizinkan');return null}
 if(/^https?:\/\//i.test(v))return v;
 if(!/\s/.test(v)&&/^[\w-]+(\.[\w-]+)+(:\d+)?(\/.*)?$/.test(v))return 'https://'+v;
 return SE[S.eng]+encodeURIComponent(v)}
function show(id){cur=id;document.querySelectorAll('.pg').forEach(p=>p.classList.toggle('on',p.id===id));
 $('#bar').hidden=id!=='web';render();
 document.querySelectorAll('#nav button').forEach(b=>b.classList.toggle('on',b.dataset.go===id||(id==='web'&&b.dataset.go==='home')));
 id==='web'?hideAds():showAds()}
function go(url){const u=norm(url);if(!u||!ext.allow(u))return;
 const t=S.tabs[S.active]||(S.tabs.push({url:u,title:host(u)}),S.active=S.tabs.length-1,S.tabs[S.active]);
 t.url=u;t.title=host(u);visit(u);save();$('#url').value=u;setLock(u);show('web');engine.open(u);ext.onPage()}
function visit(u){S.hist=S.hist.filter(h=>h.u!==u);S.hist.unshift({u,t:host(u),d:Date.now()});S.hist.length=Math.min(S.hist.length,200)}
const setLock=u=>{$('#lock').dataset.i=u.startsWith('https')?'lock':'unlock';paint($('#bar'))};
const fr=$('#fr');
fr.onload=()=>{};
engine.onUrl=u=>{const t=S.tabs[S.active];if(t){t.url=u;t.title=host(u)}visit(u);save();$('#url').value=u;setLock(u);ext.onPage()};
engine.onClose=()=>show('home');
const row=(l,t,s,a,x)=>`<div class="row" data-u="${esc(l)}"><div class="fav">${esc(t[0]||'?').toUpperCase()}</div><div><b>${esc(t)}</b><small>${esc(s)}</small></div>${x?`<button data-del="${a}" aria-label="Hapus">${ic('x')}</button>`:''}</div>`;
function render(){
 $('#sc').innerHTML=S.sc.map((s,i)=>`<a class="sc" data-u="${esc(s.u)}" data-i="${i}">${ico(s)}${esc(s.n)}</a>`).join('')+`<a class="sc" data-a="addsc"><i style="background:#e2e8f0;color:#475569">${ic('plus')}</i>Add</a>`;
 $('#news').innerHTML=NEWS.map(n=>`<div class="row"><div class="thumb"><img src="img/news-${n[2]}.svg" alt=""></div><div><b>${n[0]}</b><small>${n[1]}</small></div></div>`).join('');document.querySelectorAll('.thumb img').forEach(i=>i.addEventListener('error',()=>{i.style.display='none'}));
 $('#tl').innerHTML=S.tabs.map((t,i)=>row(t.url,t.title,t.url,i,1).replace('class="row"',`class="row" data-t="${i}"`)).join('')||'<p style="color:var(--m)">Belum ada tab.</p>';
 $('#bl').innerHTML=S.bm.map((b,i)=>row(b.u,b.t,b.u,i,1)).join('')||'<p style="color:var(--m)">Belum ada bookmark. Buka situs lalu tekan ★ di bar.</p>';
 $('#hl').innerHTML=S.hist.map(h=>row(h.u,h.t,h.u)).join('')||'<p style="color:var(--m)">Riwayat kosong.</p>';
 $('#dark').checked=S.dark;$('#eng').value=S.eng;document.body.classList.toggle('d',S.dark);ext.render();paint()}
$('#nav').innerHTML=[['home','Beranda','home'],['tabs','Tab','tabs'],['bm','Bookmark','bookmark'],['hist','Riwayat','clock'],['menu','Menu','gear']].map(([g,l,i])=>`<button data-go="${g}">${ic(i)}${l}</button>`).join('');
// Bar browser: tambahkan tombol back/forward/home/bookmark
$('#bar').insertAdjacentHTML('afterbegin',`<div style="display:flex;gap:4px;margin-bottom:6px"><button data-a="back">${ic('chev').replace('<svg','<svg style="transform:scaleX(-1)"')}</button><button data-a="fwd">${ic('chev')}</button><button data-a="home">${ic('home')}</button><button data-go="ext" style="margin-left:auto">${ic('puzzle')}</button><button data-a="bmk">${ic('star')}</button></div>`);
$('#bar').addEventListener('submit',e=>e.preventDefault());
$('#url').addEventListener('keydown',e=>{if(e.key==='Enter')go(e.target.value)});
$('#sf').onsubmit=e=>{e.preventDefault();go($('#q').value);$('#q').value=''};
const A={
 reload(){const t=S.tabs[S.active];t&&(isNative?engine.open(t.url):(fr.src=fr.src))},
 back(){try{isNative?toast('Gunakan tombol navigasi di bawah halaman'):fr.contentWindow.history.back()}catch{toast('Tidak bisa kembali')}},
 fwd(){try{isNative?toast('Gunakan tombol navigasi di bawah halaman'):fr.contentWindow.history.forward()}catch{toast('Tidak bisa maju')}},
 home(){engine.close();show('home')},
 bmk(){const t=S.tabs[S.active];if(!t)return;const i=S.bm.findIndex(b=>b.u===t.url);i>=0?(S.bm.splice(i,1),toast('Bookmark dihapus')):(S.bm.unshift({u:t.url,t:t.title}),toast('Bookmark disimpan'));save()},
 newtab(){S.tabs.push({url:'',title:'Tab Baru'});S.active=S.tabs.length-1;save();$('#q').value='';show('home')},
 clearhist(){S.hist=[];save();render();toast('Riwayat dihapus')},
 clearall(){if(confirm('Hapus riwayat, bookmark, tab, dan pengaturan?')){localStorage.removeItem(KEY);location.reload()}},
 addsc(){const u=prompt('URL situs:');const n=u&&norm(u);if(n&&n.startsWith('http')&&!n.includes('search?q')){S.sc.push({n:host(n),u:n,c:'#2596f6'});save();render()}},
 async share(){const d={title:'Nevra Browser',text:'Coba Nevra Browser — ringan dan cepat.'};navigator.share?navigator.share(d).catch(()=>{}):toast('Berbagi tidak didukung')},
 about(){toast('Nevra Browser v1.0.0')}
};
document.addEventListener('click',e=>{const t=e.target.closest('[data-a],[data-go],[data-u],[data-del],[data-t]');if(!t)return;
 if(t.dataset.a)return A[t.dataset.a]();
 if(t.dataset.go)return show(t.dataset.go);
 if(t.dataset.del!==undefined){e.stopPropagation();const l=t.closest('section').id;const i=+t.dataset.del;l==='tabs'?(S.tabs.splice(i,1),S.active=Math.max(0,S.tabs.length-1)):S.bm.splice(i,1);save();return render()}
 if(t.dataset.t!==undefined)S.active=+t.dataset.t;
 if(t.dataset.u){if(t.dataset.i!==undefined)S.active=Math.min(S.active,S.tabs.length-1);go(t.dataset.u)}});
$('#dark').onchange=e=>{S.dark=e.target.checked;save();render()};
$('#eng').onchange=e=>{S.eng=e.target.value;save()};
document.querySelectorAll('[data-app]').forEach(e=>e.textContent=APP[e.dataset.app]);
if(S.ext.priv.clear&&S.ext.on.privacy){S.hist=[];save()}
ext.init({S,save,toast,go,norm,render,host});ext.boot();render();show('home');initAds();

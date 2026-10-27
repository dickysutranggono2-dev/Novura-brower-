import {ic,paint} from './icons.js';import {engine} from './engine.js';import {initAds,hideAds,showAds} from './ads.js';import {APP} from './config.js';
const $=s=>document.querySelector(s),KEY='nuvora.v1';
const S=Object.assign({tabs:[],active:0,bm:[],hist:[],dl:[],dark:false,eng:'g',sc:[]},JSON.parse(localStorage.getItem(KEY)||'{}'));
const save=()=>localStorage.setItem(KEY,JSON.stringify(S));
const SE={g:'https://www.google.com/search?q=',d:'https://duckduckgo.com/?q=',b:'https://www.bing.com/search?q='};
const DEF=[['Google','https://www.google.com','#4285f4'],['YouTube','https://m.youtube.com','#ef4444'],['Facebook','https://m.facebook.com','#1877f2'],['Instagram','https://www.instagram.com','#d946ef'],['X','https://x.com','#111827'],['TikTok','https://www.tiktok.com','#0f172a'],['ChatGPT','https://chatgpt.com','#10a37f']];
if(!S.sc.length)S.sc=DEF.map(([n,u,c])=>({n,u,c}));
const IC={'google.com':'google','youtube.com':'youtube','facebook.com':'facebook','instagram.com':'instagram','x.com':'x','tiktok.com':'tiktok','chatgpt.com':'chatgpt'};
const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const host=u=>{try{return new URL(u).hostname.replace(/^www\./,'')}catch{return u}};
const ico=s=>{const h=host(s.u),k=Object.keys(IC).find(d=>h===d||h.endsWith('.'+d));return k?`<img src="img/ic-${IC[k]}.svg" alt="" width="46" height="46">`:`<i style="background:${s.c}">${esc(s.n[0])}</i>`};
let cur='home',selDl=-1,dlinfo={},polled=false;const nv={back:false,fwd:false};
function toast(m){const t=$('#toast');t.textContent=m;t.classList.add('on');setTimeout(()=>t.classList.remove('on'),2200)}
// Keamanan: hanya http(s); skema lain (javascript:, file:, intent:) ditolak.
function norm(v){v=v.trim();if(!v)return null;
 if(/^[a-z][a-z0-9+.-]*:/i.test(v)&&!/^https?:\/\//i.test(v)&&!/^[\w.-]+:\d+/.test(v)){toast('Skema tidak diizinkan');return null}
 if(/^https?:\/\//i.test(v))return v;
 if(!/\s/.test(v)&&/^[\w-]+(\.[\w-]+)+(:\d+)?(\/.*)?$/.test(v))return 'https://'+v;
 return SE[S.eng]+encodeURIComponent(v)}
const bounds=()=>({top:$('#bar').getBoundingClientRect().bottom,bottom:innerHeight-$('#bnav').getBoundingClientRect().top});
const raf2=f=>requestAnimationFrame(()=>requestAnimationFrame(f));
function show(id){cur=id;document.querySelectorAll('.pg').forEach(p=>p.classList.toggle('on',p.id===id));
 const w=id==='web';$('#bar').hidden=!w;$('#nav').hidden=w;$('#bnav').hidden=!w;render();
 document.querySelectorAll('#nav button').forEach(b=>b.classList.toggle('on',b.dataset.go===id));
 if(w){hideAds();raf2(()=>engine.show(bounds()))}else{engine.hide();showAds()}}
function visit(u,t){S.hist=S.hist.filter(h=>h.u!==u);S.hist.unshift({u,t:t||host(u),d:Date.now()});S.hist.length=Math.min(S.hist.length,200)}
const setLock=u=>{$('#lock').dataset.i=u.startsWith('https')?'lock':'unlock';paint($('#bar'))};
function go(url){const u=norm(url);if(!u)return;
 const t=S.tabs[S.active]||(S.tabs.push({url:u,title:host(u)}),S.active=S.tabs.length-1,S.tabs[S.active]);
 t.url=u;t.title=host(u);visit(u);save();$('#url').value=u;setLock(u);show('web');raf2(()=>engine.open(u,bounds()))}
function paintNav(){const b=$('#bnav');if(!b)return;b.querySelector('[data-a=back]').classList.toggle('dis',!nv.back);b.querySelector('[data-a=fwd]').classList.toggle('dis',!nv.fwd)}
engine.on('pageStarted',e=>{if(!e.url)return;$('#url').value=e.url;setLock(e.url);$('#prog').style.width='8%'});
engine.on('pageFinished',e=>{if(!e.url)return;const t=S.tabs[S.active];if(t){t.url=e.url;t.title=e.title||host(e.url)}visit(e.url,e.title);save();nv.back=!!e.canGoBack;nv.fwd=!!e.canGoForward;$('#prog').style.width='0';paintNav()});
engine.on('progress',e=>{$('#prog').style.width=(e.progress<100?e.progress:0)+'%'});
engine.on('download',e=>{S.dl.unshift({id:e.id,n:e.name,u:e.url,d:Date.now()});S.dl.length=Math.min(S.dl.length,100);save();toast('Mengunduh: '+e.name)});
engine.on('downloadError',()=>toast('Unduhan gagal (jenis file ini belum didukung)'));
engine.on('blocked',()=>toast('Tautan ke aplikasi lain diblokir'));
const row=(l,t,s,a,x)=>`<div class="row" data-u="${esc(l)}"><div class="fav">${esc(t[0]||'?').toUpperCase()}</div><div><b>${esc(t)}</b><small>${esc(s)}</small></div>${x?`<button data-del="${a}" aria-label="Hapus">${ic('x')}</button>`:''}</div>`;
const em=t=>`<p style="color:var(--m)">${t}</p>`;
const fmt=b=>b<=0?'':b<1024?b+' B':b<1048576?(b/1024).toFixed(1)+' kB':b<1073741824?(b/1048576).toFixed(2)+' MB':(b/1073741824).toFixed(2)+' GB';
function dlHtml(){if(!S.dl.length)return em('Belum ada unduhan.');let last='',h='';
 S.dl.forEach((d,i)=>{const day=new Date(d.d).toLocaleDateString('id-ID',{day:'numeric',month:'short',year:'numeric'});if(day!==last){h+=`<h4>${day}</h4>`;last=day}
  const f=dlinfo[d.id],src=host(d.u||'');let sub,pr='';
  if(!f)sub=(polled&&d.id?'File sudah dihapus • ':'')+src;
  else if(f.status===8)sub=[fmt(f.total),src].filter(Boolean).join(' • ');
  else if(f.status===16)sub='Gagal • '+src;
  else{const pct=f.total>0?Math.round(f.done*100/f.total):0;sub=(f.status===2?pct+'% • '+fmt(f.done)+(f.total>0?' / '+fmt(f.total):''):'Menunggu')+' • '+src;pr=`<div class="pb"><i style="width:${pct}%"></i></div>`}
  h+=`<div class="row"><div class="fav">${ic('download')}</div><div><b>${esc(d.n)}</b><small>${esc(sub)}</small>${pr}</div><button data-dm="${i}" aria-label="Opsi">${ic('dots')}</button></div>`});
 return h}
function render(){
 $('#sc').innerHTML=S.sc.map((s,i)=>`<a class="sc" data-u="${esc(s.u)}" data-i="${i}">${ico(s)}${esc(s.n)}</a>`).join('')+`<a class="sc" data-a="addsc"><i style="background:#e2e8f0;color:#475569">${ic('plus')}</i>Add</a>`;
 $('#tl').innerHTML=S.tabs.map((t,i)=>row(t.url,t.title,t.url||'Tab kosong',i,1).replace('class="row"',`class="row" data-t="${i}"`)).join('')||em('Belum ada tab.');
 $('#bl').innerHTML=S.bm.map((b,i)=>row(b.u,b.t,b.u,i,1)).join('')||em('Belum ada bookmark. Buka situs lalu tekan ★ di bar.');
 $('#hl').innerHTML=S.hist.map(h=>row(h.u,h.t,h.u)).join('')||em('Riwayat kosong.');
 $('#dll').innerHTML=dlHtml();
 $('#dark').checked=S.dark;$('#eng').value=S.eng;document.body.classList.toggle('d',S.dark);
 const e=$('#engst');if(e)e.textContent='Mesin browser: '+(engine.native?'WebView native Nevra':'iframe (mode web)');
 const bd=$('#bnav .bd');if(bd)bd.textContent=Math.max(S.tabs.length,1);paint()}
$('#nav').innerHTML=[['home','Beranda','home'],['tabs','Tab','tabs'],['bm','Bookmark','bookmark'],['hist','Riwayat','clock'],['menu','Menu','gear']].map(([g,l,i])=>`<button data-go="${g}">${ic(i)}${l}</button>`).join('');
const flip=ic('chev').replace('<svg','<svg style="transform:scaleX(-1)"');
$('#bnav').innerHTML=`<button data-a="home" aria-label="Beranda">${ic('home')}</button><button data-go="bm" aria-label="Bookmark">${ic('bookmark')}</button><button data-a="back" class="dis" aria-label="Kembali">${flip}</button><button data-a="fwd" class="dis" aria-label="Maju">${ic('chev')}</button><button data-go="tabs" aria-label="Tab">${ic('tabs')}<span class="bd">1</span></button><button data-go="menu" aria-label="Menu">${ic('menu')}</button>`;
$('#url').addEventListener('keydown',e=>{if(e.key==='Enter'){e.target.blur();go(e.target.value)}});
$('#sf').onsubmit=e=>{e.preventDefault();go($('#q').value);$('#q').value=''};
const A={
 reload(){engine.reload()},back(){engine.back()},fwd(){engine.fwd()},home(){show('home')},opendl(){engine.openDownloads()},
 bmk(){const t=S.tabs[S.active];if(!t||!t.url)return;const i=S.bm.findIndex(b=>b.u===t.url);i>=0?(S.bm.splice(i,1),toast('Bookmark dihapus')):(S.bm.unshift({u:t.url,t:t.title}),toast('Bookmark disimpan'));save()},
 newtab(){S.tabs.push({url:'',title:'Tab Baru'});S.active=S.tabs.length-1;save();$('#q').value='';show('home')},
 clearhist(){S.hist=[];save();render();toast('Riwayat dihapus')},
 cleardl(){S.dl=[];save();render();toast('Daftar unduhan dihapus')},
 clearall(){if(confirm('Hapus riwayat, bookmark, tab, dan pengaturan?')){localStorage.removeItem(KEY);location.reload()}},
 addsc(){const u=prompt('URL situs:');const n=u&&norm(u);if(n&&n.startsWith('http')&&!n.includes('search?q')){S.sc.push({n:host(n),u:n,c:'#2596f6'});save();render()}},
 dlclose(){$('#asheet').hidden=true},
 async dlopen(){const d=S.dl[selDl];A.dlclose();if(!d||!d.id)return toast('File tidak bisa dibuka');try{await engine.fileOp('openFile',d.id)}catch(x){toast('Tidak ada aplikasi untuk membuka file ini')}},
 async dlshare(){const d=S.dl[selDl];A.dlclose();if(!d||!d.id)return toast('File tidak bisa dibagikan');try{await engine.fileOp('shareFile',d.id)}catch(x){toast('Gagal membagikan file')}},
 async dldel(){const d=S.dl[selDl];A.dlclose();if(!d||!confirm('Hapus file ini dari HP?'))return;if(d.id)try{await engine.fileOp('removeDownload',d.id)}catch(x){}S.dl.splice(selDl,1);save();render()},
 async share(){const d={title:'Nevra Browser',text:'Coba Nevra Browser — ringan dan cepat.'};navigator.share?navigator.share(d).catch(()=>{}):toast('Berbagi tidak didukung')}
};
document.addEventListener('click',e=>{const t=e.target.closest('[data-a],[data-go],[data-u],[data-del],[data-t],[data-dm]');if(!t)return;
 if(t.dataset.dm!==undefined){selDl=+t.dataset.dm;$('#asn').textContent=S.dl[selDl].n;$('#asheet').hidden=false;return}
 if(t.dataset.a)return A[t.dataset.a]();
 if(t.dataset.go)return show(t.dataset.go);
 if(t.dataset.del!==undefined){e.stopPropagation();const l=t.closest('section').id;const i=+t.dataset.del;l==='tabs'?(S.tabs.splice(i,1),S.active=Math.max(0,S.tabs.length-1)):S.bm.splice(i,1);save();return render()}
 if(t.dataset.t!==undefined){S.active=+t.dataset.t;if(!t.dataset.u)return show('home')}
 if(t.dataset.u)go(t.dataset.u)});
$('#dark').onchange=e=>{S.dark=e.target.checked;save();render()};
$('#eng').onchange=e=>{S.eng=e.target.value;save()};
document.querySelectorAll('[data-app]').forEach(e=>e.textContent=APP[e.dataset.app]);
render();show('home');initAds();
setInterval(async()=>{if(cur!=='dl'||!S.dl.length||!engine.native)return;const ids=S.dl.filter(d=>d.id).map(d=>d.id).slice(0,50);if(!ids.length)return;const r=await engine.downloads(ids);dlinfo={};r.forEach(x=>dlinfo[x.id]=x);polled=true;if(cur==='dl'&&$('#asheet').hidden)render()},1500);

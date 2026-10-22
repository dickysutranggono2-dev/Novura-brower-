// Toko Ekstensi internal: modul terkontrol (tanpa kode eksternal). Status disimpan di S.ext (localStorage).
import {ic,paint} from './icons.js';import {engine,isNative} from './engine.js';
const EXT=[
 {id:'dark',n:'Mode Gelap',c:'Kustomisasi',ic:'moon',f:1,d:'Gelapkan antarmuka browser dan, di aplikasi Android, halaman web (hasil bisa berbeda tiap situs).'},
 {id:'reader',n:'Mode Pembaca',c:'Produktivitas',ic:'book',ui:1,d:'Ubah artikel yang terbaca menjadi tampilan sederhana. Tidak semua halaman didukung.'},
 {id:'sites',n:'Pintasan Situs',c:'Produktivitas',ic:'globe',ui:1,p:1,d:'Simpan, buka, dan hapus situs favorit di beranda.'},
 {id:'airdrop',n:'Pelacak Airdrop',c:'Web3',ic:'gift',ui:1,f:1,d:'Catat proyek, tautan, status, dan catatan pribadi. Data hanya di perangkat.'},
 {id:'wallet',n:'Pelacak Portofolio Crypto',c:'Web3',ic:'wallet',ui:1,p:1,d:'Pantau alamat wallet publik (EVM). Tidak pernah meminta private key atau seed phrase.'},
 {id:'privacy',n:'Perlindungan Privasi',c:'Privasi',ic:'shield',ui:1,p:1,d:'Peringatan situs HTTP, blokir domain pelacak dikenal (alamat yang dibuka langsung), hapus riwayat saat dibuka.'}];
const TRK=['doubleclick.net','googlesyndication.com','google-analytics.com','googletagmanager.com','facebook.net','scorecardresearch.com','hotjar.com','mixpanel.com','adservice.google.com'];
const DARK="(function(){var s=document.getElementById('nv-dark')||document.createElement('style');s.id='nv-dark';s.textContent='html{filter:invert(1) hue-rotate(180deg)}img,video,picture,canvas{filter:invert(1) hue-rotate(180deg)}';document.documentElement.appendChild(s)})()";
const UNDO="(function(){var s=document.getElementById('nv-dark');if(s)s.remove()})()";
const READER="(function(){var a=document.querySelector('article')||document.querySelector('main')||document.body;var ps=[].slice.call(a.querySelectorAll('h1,h2,h3,p,li,blockquote')).filter(function(e){return e.textContent.trim().length>20||/^H/.test(e.tagName)});if(ps.length<3){alert('Halaman ini tidak didukung Mode Pembaca');return}var h='<div style=\"max-width:680px;margin:0 auto;padding:20px;font:18px/1.7 Georgia,serif;color:#222\"><h1>'+document.title.replace(/</g,'&lt;')+'</h1>';ps.forEach(function(e){h+='<'+e.tagName+'>'+e.textContent.replace(/</g,'&lt;')+'</'+e.tagName+'>'});document.body.innerHTML=h+'</div>';document.body.style.background='#fafafa'})()";
let C,cat='Semua',q='',tab='store';const $=s=>document.querySelector(s),bal={};
const E=()=>C.S.ext,esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const cur=()=>C.S.tabs[C.S.active]?.url||'';
const card=x=>{const i=E().inst[x.id],on=E().on[x.id];return `<div class="xc"><div class="xi">${ic(x.ic)}</div><div class="xb"><b>${x.n}</b><small>${x.c}${i?(on?' · Aktif':' · Nonaktif'):''}</small><p>${x.d}</p></div><div class="xa">${!i?`<button class="xbtn" data-x="inst" data-id="${x.id}">Pasang</button>`:`<button class="xdel" data-x="rm" data-id="${x.id}">Hapus</button>${x.ui?`<button class="xbtn ghost" data-x="open" data-id="${x.id}">${x.id==='reader'?'Terapkan':'Buka'}</button>`:''}<button class="xbtn ${on?'ghost':''}" data-x="tog" data-id="${x.id}">${on?'Nonaktifkan':'Aktifkan'}</button>`}</div></div>`};
const sheetHead=t=>`<div class="row" style="box-shadow:none;background:none;padding:0"><b style="font-size:18px;flex:1">${t}</b><button data-x="close">${ic('x')}</button></div>`;
const PAN={
 sites:()=>sheetHead('Pintasan Situs')+`<div class="fm"><input id="sn" placeholder="Nama (opsional)" maxlength="30"><input id="su" placeholder="https://contoh.com" inputmode="url"><button class="btn" data-x="sadd">Tambah</button></div>`+C.S.sc.map((s,i)=>`<div class="row"><div class="fav">${esc(s.n[0])}</div><div><b>${esc(s.n)}</b><small>${esc(s.u)}</small></div><button data-x="sgo" data-id="${i}">${ic('chev')}</button><button data-x="sdel" data-id="${i}">${ic('trash')}</button></div>`).join(''),
 airdrop:()=>sheetHead('Pelacak Airdrop')+`<div class="fm"><input id="an" placeholder="Nama proyek" maxlength="60"><input id="al" placeholder="Tautan (https://...)" inputmode="url"><select id="as"><option>Belum</option><option>Dikerjakan</option><option>Selesai</option></select><textarea id="ao" placeholder="Catatan pribadi" maxlength="300" rows="2"></textarea><button class="btn" data-x="aadd">Simpan</button></div>`+(E().air.map((a,i)=>`<div class="row"><div><b>${esc(a.n)}</b><small>${esc(a.l||'tanpa tautan')}</small>${a.o?`<small>${esc(a.o)}</small>`:''}</div><button class="xbtn ghost" data-x="ast" data-id="${i}">${esc(a.s)}</button>${a.l?`<button data-x="ago" data-id="${i}">${ic('chev')}</button>`:''}<button data-x="adel" data-id="${i}">${ic('trash')}</button></div>`).join('')||'<p class="m">Belum ada proyek.</p>'),
 wallet:()=>sheetHead('Pelacak Portofolio')+`<p class="m">Hanya alamat publik. Jangan pernah memasukkan private key atau seed phrase.</p><div class="fm"><input id="wl" placeholder="Label" maxlength="30"><input id="wa" placeholder="0x... (alamat EVM)" autocapitalize="off"><button class="btn" data-x="wadd">Tambah alamat</button></div>`+(E().wal.map((w,i)=>`<div class="row"><div><b>${esc(w.l||'Wallet')}</b><small>${w.a}</small><small>${bal[w.a]??'Saldo ETH: belum dicek'}</small></div><button class="xbtn ghost" data-x="wbal" data-id="${i}">Cek</button><button data-x="wdel" data-id="${i}">${ic('trash')}</button></div>`).join('')||'<p class="m">Belum ada alamat.</p>'),
 privacy:()=>{const p=E().priv,t=(k,l)=>`<label class="row"><div><b>${l}</b></div><input type="checkbox" class="sw" data-x="ptog" data-id="${k}" ${p[k]?'checked':''}></label>`;return sheetHead('Perlindungan Privasi')+t('http','Peringatan sebelum membuka situs HTTP')+t('block','Blokir domain pelacak dikenal')+t('clear','Hapus riwayat saat aplikasi dibuka')+'<p class="m">Batasan: pemblokiran hanya untuk alamat yang kamu buka langsung dengan daftar kecil; pelacak di dalam halaman web tidak diblokir.</p>'}};
const open=id=>{const s=$('#sheet');s.innerHTML=PAN[id]();s.dataset.p=id;s.hidden=false;paint(s)};
const redo=()=>{const s=$('#sheet');if(!s.hidden)open(s.dataset.p);C.render()};
export const ext={
 init(ctx){C=ctx;document.addEventListener('click',e=>{const t=e.target.closest('[data-x]');if(!t)return;const a=t.dataset.x,id=t.dataset.id,S=E();
  if(a==='tab'){tab=id;return ext.render()}
  if(a==='cat'){cat=id;return ext.render()}
  if(a==='close'){$('#sheet').hidden=true;return}
  if(a==='inst'){if(S.inst[id])return C.toast('Sudah terpasang');S.inst[id]=1;S.on[id]=1;C.save();ext.apply(id);C.toast('Terpasang');return ext.render()}
  if(a==='rm'){if(!confirm('Hapus ekstensi? Data yang kamu simpan tetap ada.'))return;S.on[id]=0;ext.apply(id);delete S.inst[id];delete S.on[id];C.save();C.toast('Dihapus');return ext.render()}
  if(a==='tog'){S.on[id]=S.on[id]?0:1;C.save();ext.apply(id);return ext.render()}
  if(a==='open'){if(!S.on[id])return C.toast('Aktifkan dulu ekstensi ini');if(id==='reader'){return isNative?engine.inject(READER).then(ok=>C.toast(ok?'Mode Pembaca diterapkan':'Buka halaman dulu')):C.toast('Hanya berjalan di aplikasi Android')}return open(id)}
  const L={sadd(){const u=C.norm($('#su').value);if(!u||!/^https?:/.test(u)||u.includes('?q='))return C.toast('URL tidak valid');if(C.S.sc.some(s=>s.u===u))return C.toast('Sudah ada');C.S.sc.push({n:($('#sn').value.trim()||C.host(u)).slice(0,30),u,c:'#2596f6'})},sdel(){C.S.sc.splice(+id,1)},sgo(){$('#sheet').hidden=true;C.go(C.S.sc[+id].u)},
   aadd(){const n=$('#an').value.trim();if(!n)return C.toast('Isi nama proyek');let l=$('#al').value.trim();if(l){l=C.norm(l);if(!l||!/^https?:/.test(l))return C.toast('Tautan tidak valid')}S.air.push({n,l,s:$('#as').value,o:$('#ao').value.trim()})},adel(){S.air.splice(+id,1)},ast(){const o=['Belum','Dikerjakan','Selesai'],a2=S.air[+id];a2.s=o[(o.indexOf(a2.s)+1)%3]},ago(){$('#sheet').hidden=true;C.go(S.air[+id].l)},
   wadd(){const a=$('#wa').value.trim();if(!/^0x[a-fA-F0-9]{40}$/.test(a))return C.toast('Alamat EVM tidak valid');if(S.wal.some(w=>w.a.toLowerCase()===a.toLowerCase()))return C.toast('Alamat sudah ada');S.wal.push({a,l:$('#wl').value.trim()})},wdel(){S.wal.splice(+id,1)},
   ptog(){S.priv[id]=t.checked?1:0},
   async wbal(){if(!S.walOK){if(!confirm('Alamat publik akan dikirim ke layanan RPC publik (eth.llamarpc.com) untuk membaca saldo. Lanjut?'))return;S.walOK=1}const w=S.wal[+id];try{const r=await fetch('https://eth.llamarpc.com',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({jsonrpc:'2.0',id:1,method:'eth_getBalance',params:[w.a,'latest']})});const j=await r.json();bal[w.a]='Saldo ETH: '+(Number(BigInt(j.result))/1e18).toFixed(4)}catch{bal[w.a]='Gagal mengambil saldo (periksa koneksi)'}}};
  if(L[a]){Promise.resolve(L[a]()).then(()=>{C.save();redo()})}});},
 boot(){const S=E();Object.keys(S.inst).forEach(id=>{if(S.on[id])ext.apply(id,1)})},
 apply(id,quiet){const on=E().on[id];if(id==='dark'){C.S.dark=!!on;C.save();C.render();if(!on)engine.inject(UNDO);else if(!quiet)ext.onPage()}},
 onPage(){if(E().on.dark&&E().inst.dark)engine.inject(DARK)},
 allow(u){const p=E().priv,on=E().on.privacy&&E().inst.privacy;if(!on)return true;let h='';try{h=new URL(u).hostname}catch{}
  if(p.block&&TRK.some(d=>h===d||h.endsWith('.'+d))){C.toast('Domain pelacak diblokir');return false}
  if(p.http&&u.startsWith('http:')&&!confirm('Situs ini tidak memakai HTTPS. Tetap buka?'))return false;return true},
 render(){const el=$('#ext');if(!el||!el.classList.contains('on'))return;
  document.querySelectorAll('#ext .seg button').forEach(b=>b.classList.toggle('on',b.dataset.id===tab));
  const mine=E().inst,list=EXT.filter(x=>tab==='mine'?mine[x.id]:(cat==='Semua'||x.c===cat)&&(x.n+x.d).toLowerCase().includes(q));
  let h='';if(tab==='store'){h+=`<input class="xs" id="xq" placeholder="Cari ekstensi" value="${esc(q)}"><div class="chips">${['Semua','Web3','Privasi','Produktivitas','Kustomisasi'].map(c=>`<button data-x="cat" data-id="${c}" class="${c===cat?'on':''}">${c}</button>`).join('')}</div>`;
   if(cat==='Semua'&&!q)h+=`<h4>Ekstensi Unggulan</h4>${EXT.filter(x=>x.f).map(card).join('')}<h4>Ekstensi Populer</h4>${EXT.filter(x=>x.p).map(card).join('')}<h4>Semua Ekstensi</h4>`}
  h+=list.map(card).join('')||'<p class="m">Tidak ada ekstensi.</p>';$('#xbody').innerHTML=h;paint(el);
  const i=$('#xq');if(i)i.oninput=e=>{q=e.target.value.toLowerCase();ext.render();const n=$('#xq');n.focus();n.setSelectionRange(99,99)}}
};

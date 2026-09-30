/* 리뷰·매출 분석 프로토타입 — backend API(/api/v1)만 사용하는 단일 페이지 */
(() => {
  'use strict';

  const API = '/api/v1';
  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
  const state = { token: null, me: null, stores: [], storeId: null, store: null, reviewPage: 0 };

  // ---------- 유틸 ----------
  const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const won = (n) => `${Math.round(n).toLocaleString('ko-KR')}원`;
  const num = (n) => Number(n).toLocaleString('ko-KR');
  const shortWon = (n) => (Math.abs(n) >= 1e8 ? `${(n / 1e8).toFixed(1)}억` : Math.abs(n) >= 1e4 ? `${Math.round(n / 1e4).toLocaleString('ko-KR')}만` : `${n}`);
  const fmtDateTime = (s) => (s ? new Date(s).toLocaleString('ko-KR', { dateStyle: 'short', timeStyle: 'short' }) : '-');
  const store = {
    get: (k) => { try { return sessionStorage.getItem(k); } catch { return null; } },
    set: (k, v) => { try { v == null ? sessionStorage.removeItem(k) : sessionStorage.setItem(k, v); } catch { /* 무시 */ } },
  };
  const CATEGORY = { KOREAN: '한식', CHINESE: '중식', JAPANESE: '일식', WESTERN: '양식', CAFE: '카페', PUB: '주점', ETC: '기타' };
  const JOB_TYPE = { SALES_UPLOAD: '매출 업로드', REVIEW_COLLECT: '리뷰 수집', REVIEW_UPLOAD: '리뷰 파일', ANALYZE: '분석' };
  const JOB_STATUS = { REQUESTED: ['요청됨', ''], RUNNING: ['진행 중', 'warn'], COMPLETED: ['완료', 'good'], FAILED: ['실패', 'bad'] };
  const HINT = {
    ACCESS_BLOCKED: '네이버가 접근을 제한했습니다. 잠시 후 다시 시도하거나 “리뷰 파일 등록”을 이용하세요.',
    NAVER_UNREACHABLE: '네이버에 연결하지 못했습니다. 네트워크를 확인하거나 “리뷰 파일 등록”을 이용하세요.',
    COLLECTOR_UNAVAILABLE: '수집 서버(collector, :8000)가 꺼져 있습니다.',
    PAGE_STRUCTURE_CHANGED: '네이버 페이지 구조가 바뀌었습니다. collector/app/parser.py 확인이 필요합니다.',
    COLLECT_TOO_FREQUENT: '같은 매장은 1시간에 한 번만 수집할 수 있습니다.',
    PLACE_NOT_CONNECTED: '먼저 “플레이스 연결” 탭에서 네이버 플레이스를 연결하세요.',
    INTERRUPTED: '서버 재시작으로 중단된 작업입니다.',
  };
  const statusBadge = (s) => { const [t, c] = JOB_STATUS[s] || [s, '']; return `<span class="badge ${c}">${esc(t)}</span>`; };

  function toast(msg, bad = false) {
    const t = $('#toast');
    t.textContent = msg;
    t.className = `toast${bad ? ' bad' : ''}`;
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => t.classList.add('hidden'), 4000);
  }

  class ApiError extends Error {
    constructor(status, code, message, data) { super(message); this.status = status; this.code = code; this.data = data; }
  }

  // ---------- API ----------
  async function api(method, path, body, retried = false) {
    const opts = { method, headers: {}, credentials: 'same-origin' };
    if (state.token) opts.headers.Authorization = `Bearer ${state.token}`;
    if (body instanceof FormData) opts.body = body;
    else if (body !== undefined) { opts.headers['Content-Type'] = 'application/json'; opts.body = JSON.stringify(body); }
    const res = await fetch(API + path, opts);
    if (res.status === 204) return null;
    let json = null;
    try { json = await res.json(); } catch { /* 본문 없음 */ }
    if (res.ok && json?.success) return json.data;
    const err = json?.error || {};
    if (res.status === 401 && err.code === 'TOKEN_EXPIRED' && !retried && await refresh()) {
      return api(method, path, body, true);
    }
    if (res.status === 401 && !path.startsWith('/auth/')) { logoutLocal(); }
    throw new ApiError(res.status, err.code || `HTTP_${res.status}`, err.message || '요청에 실패했습니다.', err.data);
  }

  async function refresh() {
    try {
      const res = await fetch(`${API}/auth/refresh`, { method: 'POST', credentials: 'same-origin' });
      const json = await res.json();
      if (!json.success) return false;
      setToken(json.data.accessToken);
      return true;
    } catch { return false; }
  }

  function setToken(t) { state.token = t; store.set('accessToken', t); }
  function logoutLocal() { setToken(null); state.me = null; showLogin(); }
  function errorText(e) { return `${e.message}${HINT[e.code] ? ` — ${HINT[e.code]}` : ''}`; }

  // ---------- 로그인 ----------
  async function showLogin() {
    $('#appView').classList.add('hidden');
    $('#loginView').classList.remove('hidden');
    $('#authArea').innerHTML = '';
    try {
      const p = await api('GET', '/auth/providers');
      const box = $('#oauthButtons');
      box.innerHTML = [
        p.kakao ? '<a class="kakao" href="/api/v1/auth/oauth/kakao">카카오로 로그인</a>' : '',
        p.google ? '<a href="/api/v1/auth/oauth/google">Google 로그인</a>' : '',
        !p.kakao && !p.google ? '<p class="muted small">카카오·구글 로그인은 키(KAKAO_CLIENT_ID 등)를 설정하면 나타납니다.</p>' : '',
      ].join('');
      $('#devLoginForm').classList.toggle('hidden', !p.dev);
    } catch { /* 무시 */ }
  }

  $('#devLoginForm').addEventListener('submit', async (ev) => {
    ev.preventDefault();
    try {
      const data = await api('POST', '/auth/dev-login', { email: ev.target.email.value });
      setToken(data.accessToken);
      await enterApp();
    } catch (e) { toast(errorText(e), true); }
  });

  async function enterApp() {
    state.me = await api('GET', '/me');
    $('#loginView').classList.add('hidden');
    $('#appView').classList.remove('hidden');
    $('#authArea').innerHTML = `<span class="muted">${esc(state.me.email || '(이메일 없음)')} · ${esc(state.me.provider)}</span>
      <button id="logoutBtn">로그아웃</button>`;
    $('#logoutBtn').onclick = async () => { try { await api('POST', '/auth/logout'); } catch { /* 무시 */ } store.set('storeId', null); logoutLocal(); };
    await loadStores();
    const saved = Number(store.get('storeId'));
    const pick = state.stores.find((s) => s.storeId === saved) || state.stores[0];
    if (pick) await selectStore(pick.storeId); else showNoStore();
  }

  // ---------- 매장 ----------
  async function loadStores() {
    state.stores = await api('GET', '/stores');
    $('#storeList').innerHTML = state.stores.map((s) => `
      <li><button data-store="${s.storeId}" class="${s.storeId === state.storeId ? 'active' : ''}">
        ${esc(s.name)} <span class="muted small">${esc(CATEGORY[s.category] || s.category)}</span></button></li>`).join('');
    $$('#storeList [data-store]').forEach((b) => { b.onclick = () => selectStore(Number(b.dataset.store)); });
  }

  function showNoStore() { $('#noStore').classList.remove('hidden'); $('#storeView').classList.add('hidden'); }

  $('#storeForm').addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const f = ev.target;
    try {
      const s = await api('POST', '/stores', { name: f.name.value, category: f.category.value });
      f.reset();
      await loadStores();
      await selectStore(s.storeId);
      toast('매장을 등록했습니다.');
    } catch (e) { toast(errorText(e), true); }
  });

  async function selectStore(id) {
    state.storeId = id;
    store.set('storeId', String(id));
    $$('#storeList [data-store]').forEach((b) => b.classList.toggle('active', Number(b.dataset.store) === id));
    $('#noStore').classList.add('hidden');
    $('#storeView').classList.remove('hidden');
    await refreshStoreHeader();
    setDefaultRange();
    openTab(currentTab());
  }

  async function refreshStoreHeader() {
    const s = state.store = await api('GET', `/stores/${state.storeId}`);
    $('#storeName').textContent = s.name;
    $('#storeMeta').innerHTML = `${esc(CATEGORY[s.category] || s.category)} · 메뉴 ${s.menuCount}개 · ${
      s.placeConnected ? `네이버 플레이스: ${esc(s.place.placeName || s.place.placeId)}` : '<span class="badge warn">플레이스 미연결</span>'}
      · 마지막 수집 ${esc(fmtDateTime(s.lastCollectedAt))}`;
  }

  // ---------- 탭 ----------
  const currentTab = () => $('.tabs button.active')?.dataset.tab || 'dashboard';
  $$('.tabs button').forEach((b) => { b.onclick = () => openTab(b.dataset.tab); });
  function openTab(name) {
    $$('.tabs button').forEach((b) => b.classList.toggle('active', b.dataset.tab === name));
    $$('.tab').forEach((p) => p.classList.toggle('hidden', p.dataset.panel !== name));
    ({ dashboard: loadDashboard, sales: loadUploadHistory, reviews: () => loadReviews(0), menus: loadMenus, place: renderPlace, jobs: loadJobs })[name]?.();
  }

  // ---------- 대시보드 ----------
  function setDefaultRange() { $('#dashFrom').value = ''; $('#dashTo').value = ''; }
  $$('[data-preset]').forEach((b) => {
    b.onclick = () => {
      const p = b.dataset.preset;
      if (p === 'all') { $('#dashFrom').value = ''; $('#dashTo').value = ''; }
      if (p === 'aug') { $('#dashFrom').value = '2026-08-01'; $('#dashTo').value = '2026-08-31'; }
      if (p === 'last30') {
        const to = new Date(); const from = new Date(Date.now() - 29 * 864e5);
        const iso = (d) => new Date(d.getTime() - d.getTimezoneOffset() * 6e4).toISOString().slice(0, 10);
        $('#dashFrom').value = iso(from); $('#dashTo').value = iso(to);
      }
      loadDashboard();
    };
  });
  $('#dashFrom').onchange = $('#dashTo').onchange = () => loadDashboard();
  $$('[data-toggle-table]').forEach((b) => {
    b.onclick = () => {
      const t = $(`#${b.dataset.toggleTable}`);
      t.classList.toggle('hidden');
      b.textContent = t.classList.contains('hidden') ? '표로 보기' : '표 숨기기';
    };
  });

  function rangeQuery(fromSel, toSel) {
    const q = new URLSearchParams();
    if ($(fromSel).value) q.set('from', $(fromSel).value);
    if ($(toSel).value) q.set('to', $(toSel).value);
    return q;
  }

  async function loadDashboard() {
    const q = rangeQuery('#dashFrom', '#dashTo');
    try {
      const [day, menu, reviews] = await Promise.all([
        api('GET', `/stores/${state.storeId}/sales/summary?${q}&groupBy=DAY`),
        api('GET', `/stores/${state.storeId}/sales/summary?${q}&groupBy=MENU`),
        api('GET', `/stores/${state.storeId}/reviews?${q}&size=5`),
      ]);
      const days = day.items.length;
      $('#kpis').innerHTML = [
        kpi('매출액', won(day.totalAmount), days ? `${days}일 영업` : '데이터 없음'),
        kpi('판매 수량', `${num(day.totalQuantity)}개`, days ? `하루 평균 ${won(day.totalAmount / days)}` : ''),
        kpi('리뷰', `${num(reviews.totalElements)}건`, '같은 기간 작성일 기준'),
        kpi('메뉴', `${num(menu.items.filter((m) => m.matched).length)}개 판매`, `미등록 ${menu.items.filter((m) => !m.matched).length}개`),
      ].join('');
      state.chartData = { day: day.items, menu: menu.items };
      renderDayChart($('#dayChart'), day.items);
      $('#dayTable').innerHTML = table(['날짜', '수량', '매출액'], day.items.map((d) => [d.date, num(d.quantity), won(d.amount)]), [1, 2]);
      renderMenuChart($('#menuChart'), menu.items);
      $('#menuTable').innerHTML = table(['메뉴', '등록 여부', '수량', '매출액'],
        menu.items.map((m) => [m.menuName, m.matched ? '등록' : '미등록', num(m.quantity), won(m.amount)]), [2, 3]);
      $('#dashReviews').innerHTML = reviews.content.length
        ? reviews.content.map(reviewItem).join('')
        : '<p class="muted">이 기간의 리뷰가 없습니다. “리뷰” 탭에서 수집하거나 파일을 등록하세요.</p>';
    } catch (e) { toast(errorText(e), true); }
  }

  const kpi = (label, value, sub) => `<div class="kpi"><div class="label">${esc(label)}</div><div class="value">${esc(value)}</div><div class="sub">${esc(sub)}</div></div>`;

  function table(headers, rows, numCols = []) {
    if (!rows.length) return '<p class="muted">데이터가 없습니다.</p>';
    return `<table><thead><tr>${headers.map((h, i) => `<th class="${numCols.includes(i) ? 'num' : ''}">${esc(h)}</th>`).join('')}</tr></thead>
      <tbody>${rows.map((r) => `<tr>${r.map((c, i) => `<td class="${numCols.includes(i) ? 'num' : ''}">${esc(c)}</td>`).join('')}</tr>`).join('')}</tbody></table>`;
  }

  // ---------- 차트 (SVG, 외부 라이브러리 없음) ----------
  const SVG_NS = 'http://www.w3.org/2000/svg';
  const tip = $('#tooltip');
  function showTip(html, x, y) {
    tip.innerHTML = html;
    tip.classList.remove('hidden');
    const r = tip.getBoundingClientRect();
    const left = Math.min(x + 14, window.innerWidth - r.width - 8);
    const top = y - r.height - 12 < 8 ? y + 16 : y - r.height - 12;
    tip.style.left = `${left}px`; tip.style.top = `${top}px`;
  }
  const hideTip = () => tip.classList.add('hidden');

  function niceMax(v) {
    if (v <= 0) return 1;
    const p = 10 ** Math.floor(Math.log10(v)); const m = v / p;
    return (m <= 1 ? 1 : m <= 2 ? 2 : m <= 2.5 ? 2.5 : m <= 5 ? 5 : 10) * p;
  }

  function renderDayChart(el, items) {
    el.innerHTML = '';
    if (!items.length) { el.innerHTML = '<div class="chart-empty">이 기간의 매출이 없습니다. “매출 업로드” 탭에서 CSV 를 올리세요.</div>'; return; }
    const W = Math.max(300, Math.round(el.clientWidth || 800)), H = W < 500 ? 200 : 260, m = { l: 46, r: 12, t: 12, b: 26 };
    const iw = W - m.l - m.r, ih = H - m.t - m.b;
    const max = niceMax(Math.max(...items.map((d) => d.amount)));
    const x = (i) => m.l + (items.length === 1 ? iw / 2 : (i / (items.length - 1)) * iw);
    const y = (v) => m.t + ih - (v / max) * ih;
    const ticks = [0, .25, .5, .75, 1].map((f) => f * max);
    const step = Math.max(1, Math.ceil(items.length / Math.max(3, Math.floor(W / 110))));
    const pts = items.map((d, i) => `${x(i).toFixed(1)},${y(d.amount).toFixed(1)}`);
    el.innerHTML = `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="일별 매출액 추이">
      <g class="axis">${ticks.map((t) => `<line class="gridline" x1="${m.l}" x2="${W - m.r}" y1="${y(t)}" y2="${y(t)}"/>
        <text x="${m.l - 8}" y="${y(t) + 4}" text-anchor="end">${shortWon(t)}</text>`).join('')}
        ${items.map((d, i) => (i % step === 0 || i === items.length - 1) && !(i !== items.length - 1 && items.length - 1 - i < step / 2)
          ? `<text x="${x(i)}" y="${H - 6}" text-anchor="middle">${d.date.slice(5).replace('-', '/')}</text>` : '').join('')}</g>
      <line class="baseline" x1="${m.l}" x2="${W - m.r}" y1="${y(0)}" y2="${y(0)}"/>
      <polygon class="area" points="${x(0)},${y(0)} ${pts.join(' ')} ${x(items.length - 1)},${y(0)}"/>
      <polyline class="line" points="${pts.join(' ')}"/>
      <line class="crosshair hidden" y1="${m.t}" y2="${m.t + ih}"/>
      <circle class="dot hidden" r="4.5"/>
      <rect class="hit" x="${m.l}" y="${m.t}" width="${iw}" height="${ih}"/>
    </svg>`;
    const svg = el.querySelector('svg'), hit = svg.querySelector('.hit');
    const cross = svg.querySelector('.crosshair'), dot = svg.querySelector('.dot');
    const move = (ev) => {
      const r = svg.getBoundingClientRect();
      const sx = ((ev.clientX - r.left) / r.width) * W;
      const i = Math.max(0, Math.min(items.length - 1, Math.round(((sx - m.l) / iw) * (items.length - 1))));
      const d = items[i];
      cross.setAttribute('x1', x(i)); cross.setAttribute('x2', x(i)); cross.classList.remove('hidden');
      dot.setAttribute('cx', x(i)); dot.setAttribute('cy', y(d.amount)); dot.classList.remove('hidden');
      const wd = '일월화수목금토'[new Date(`${d.date}T00:00:00`).getDay()];
      showTip(`<b>${esc(d.date)} (${wd})</b>매출액 ${won(d.amount)}<br>수량 ${num(d.quantity)}개`, ev.clientX, ev.clientY);
    };
    hit.addEventListener('mousemove', move);
    hit.addEventListener('mouseleave', () => { cross.classList.add('hidden'); dot.classList.add('hidden'); hideTip(); });
  }

  function renderMenuChart(el, items) {
    el.innerHTML = '';
    if (!items.length) { el.innerHTML = '<div class="chart-empty">데이터가 없습니다.</div>'; return; }
    const rows = [...items].sort((a, b) => b.amount - a.amount);
    const W = Math.max(300, Math.round(el.clientWidth || 800)), narrow = W < 500;
    const rowH = 28, labelW = narrow ? 96 : 150, valueW = narrow ? 64 : 110, m = { t: 4, b: 4 };
    const maxChars = narrow ? 7 : 12;
    const H = m.t + m.b + rows.length * rowH;
    const bw = W - labelW - valueW;
    const max = Math.max(...rows.map((r) => r.amount)) || 1;
    const bar = (x0, y0, w, h) => { // 오른쪽(값 끝)만 4px 둥글게, 기준선 쪽은 직각
      const r = Math.min(4, w / 2, h / 2);
      return `M${x0},${y0} H${x0 + w - r} Q${x0 + w},${y0} ${x0 + w},${y0 + r} V${y0 + h - r} Q${x0 + w},${y0 + h} ${x0 + w - r},${y0 + h} H${x0} Z`;
    };
    el.innerHTML = `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="메뉴별 매출액">
      <line class="baseline" x1="${labelW}" x2="${labelW}" y1="0" y2="${H}"/>
      ${rows.map((r, i) => {
        const y0 = m.t + i * rowH, w = Math.max(2, (r.amount / max) * bw);
        return `<g class="bar-row" data-i="${i}">
          <text class="bar-label" x="${labelW - 10}" y="${y0 + rowH / 2 + 4}" text-anchor="end">${esc(r.menuName.length > maxChars ? `${r.menuName.slice(0, maxChars - 1)}…` : r.menuName)}</text>
          <path class="bar${r.matched ? '' : ' unmatched'}" d="${bar(labelW, y0 + 5, w, rowH - 10)}"/>
          <text class="bar-value" x="${labelW + w + 8}" y="${y0 + rowH / 2 + 4}">${shortWon(r.amount)}원</text>
          <rect class="hit" x="0" y="${y0}" width="${W}" height="${rowH}"/>
        </g>`;
      }).join('')}
    </svg>
    ${rows.some((r) => !r.matched) ? '<div class="legend-note"><span><span class="sw"></span>등록된 메뉴</span><span><span class="sw outline"></span>미등록 메뉴 (메뉴 탭에서 등록하면 연결)</span></div>' : ''}`;
    $$('.bar-row', el).forEach((g) => {
      const r = rows[Number(g.dataset.i)];
      g.addEventListener('mousemove', (ev) => showTip(`<b>${esc(r.menuName)}</b>${r.matched ? '' : '미등록 메뉴<br>'}매출액 ${won(r.amount)}<br>수량 ${num(r.quantity)}개`, ev.clientX, ev.clientY));
      g.addEventListener('mouseleave', hideTip);
    });
  }

  let resizeTimer;
  window.addEventListener('resize', () => {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(() => {
      if (!state.chartData || $('[data-panel=dashboard]').classList.contains('hidden')) return;
      renderDayChart($('#dayChart'), state.chartData.day);
      renderMenuChart($('#menuChart'), state.chartData.menu);
    }, 150);
  });

  // ---------- 매출 업로드 ----------
  $('#salesForm').addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const f = ev.target, fd = new FormData();
    fd.append('file', f.file.files[0]);
    const mapping = {};
    ['soldAt', 'menuName', 'quantity', 'amount'].forEach((k) => { if (f[k].value.trim()) mapping[k] = f[k].value.trim(); });
    if (Object.keys(mapping).length) fd.append('columnMapping', JSON.stringify(mapping));
    if (f.replace.checked) fd.append('replace', 'true');
    const btn = f.querySelector('button[type=submit]');
    btn.disabled = true; btn.textContent = '업로드 중…';
    const out = $('#salesResult');
    try {
      const r = await api('POST', `/stores/${state.storeId}/sales/uploads`, fd);
      out.innerHTML = `<div class="result"><dl>
        <dt>기간</dt><dd>${esc(r.periodStart)} ~ ${esc(r.periodEnd)}</dd>
        <dt>전체 행</dt><dd>${num(r.totalRows)}</dd>
        <dt>성공</dt><dd><span class="badge good">${num(r.successRows)}</span></dd>
        <dt>오류</dt><dd>${r.errorRows ? `<span class="badge bad">${num(r.errorRows)}</span> <button class="link" data-errors="${r.uploadId}">오류 행 보기</button>` : '0'}</dd>
        <dt>미등록 메뉴</dt><dd>${r.newMenuCandidates.length ? `<div class="chips">${r.newMenuCandidates.map((n) =>
          `<span class="chip">${esc(n)}<button data-addmenu="${esc(n)}" title="메뉴로 등록">+ 등록</button></span>`).join('')}</div>` : '없음'}</dd>
      </dl><div id="errorRows"></div></div>`;
      bindUploadResult(out);
      f.reset();
      toast('매출을 업로드했습니다.');
      loadUploadHistory();
    } catch (e) {
      let extra = '';
      if (e.code === 'CSV_MISSING_COLUMNS') extra = `<br>없는 열: ${esc((e.data?.missing || []).join(', '))} — 열 이름이 다르면 “열 이름이 다르면”을 펼쳐 입력하세요.`;
      if (e.code === 'SALES_PERIOD_OVERLAP') extra = `<br>겹치는 업로드: ${(e.data?.overlaps || []).map((o) => `#${o.uploadId} (${esc(o.periodStart)}~${esc(o.periodEnd)})`).join(', ')} — 교체하려면 “기존 매출을 교체”를 체크하세요.`;
      out.innerHTML = `<div class="result bad"><b>${esc(e.code)}</b> ${esc(e.message)}${extra}</div>`;
      loadUploadHistory();
    } finally { btn.disabled = false; btn.textContent = '업로드'; }
  });

  function bindUploadResult(root) {
    $$('[data-errors]', root).forEach((b) => { b.onclick = () => showErrors(Number(b.dataset.errors), $('#errorRows', root) || root); });
    $$('[data-addmenu]', root).forEach((b) => {
      b.onclick = async () => {
        try {
          await api('POST', `/stores/${state.storeId}/menus`, { posName: b.dataset.addmenu });
          b.closest('.chip').remove();
          toast(`“${b.dataset.addmenu}” 메뉴를 등록했습니다. 다음 업로드부터 연결됩니다.`);
          refreshStoreHeader();
        } catch (e) { toast(errorText(e), true); }
      };
    });
  }

  async function showErrors(uploadId, target) {
    try {
      const r = await api('GET', `/stores/${state.storeId}/sales/uploads/${uploadId}/errors`);
      target.innerHTML = `<div class="table-wrap">${table(['행', '사유', '원문'], r.errors.map((e) => [e.rowNumber ?? '-', e.reason, e.rawLine ?? '']))}</div>
        ${r.truncated ? `<p class="muted small">앞의 ${r.returnedRows}건만 표시 (전체 ${r.errorRows}건)</p>` : ''}`;
    } catch (e) { toast(errorText(e), true); }
  }

  async function loadUploadHistory() {
    try {
      const page = await api('GET', `/stores/${state.storeId}/sales/uploads?size=20`);
      const box = $('#uploadHistory');
      if (!page.content.length) { box.innerHTML = '<p class="muted">아직 업로드한 매출이 없습니다. <code>samples/sales_sample.csv</code> 로 시작해 보세요.</p>'; return; }
      box.innerHTML = `<div class="table-wrap"><table><thead><tr><th>#</th><th>파일</th><th>상태</th><th>기간</th><th class="num">성공</th><th class="num">오류</th><th>시각</th><th></th></tr></thead><tbody>
        ${page.content.map((u) => `<tr><td>${u.uploadId}</td><td>${esc(u.fileName || '-')}</td><td>${statusBadge(u.status)}</td>
          <td>${u.periodStart ? `${esc(u.periodStart)} ~ ${esc(u.periodEnd)}` : '-'}</td>
          <td class="num">${num(u.successRows)}</td><td class="num">${num(u.errorRows)}</td><td>${esc(fmtDateTime(u.requestedAt))}</td>
          <td><button class="link" data-errors="${u.uploadId}">오류 보기</button></td></tr>
          <tr class="hidden" data-err-row="${u.uploadId}"><td colspan="8"></td></tr>`).join('')}
      </tbody></table></div>`;
      $$('[data-errors]', box).forEach((b) => {
        b.onclick = () => {
          const row = $(`[data-err-row="${b.dataset.errors}"]`, box);
          row.classList.toggle('hidden');
          if (!row.classList.contains('hidden')) showErrors(Number(b.dataset.errors), row.firstElementChild);
        };
      });
    } catch (e) { toast(errorText(e), true); }
  }

  // ---------- 리뷰 ----------
  function reviewItem(r) {
    const stars = r.rating != null ? `★ ${Number(r.rating).toFixed(1)}` : '';
    return `<div class="review"><div class="meta">
      <span>작성 ${esc(r.writtenAt)}</span>${r.visitedAt ? `<span>방문 ${esc(r.visitedAt)}</span>` : ''}
      ${stars ? `<span>${esc(stars)}</span>` : ''}<span class="badge">${r.source === 'NAVER' ? '네이버 수집' : '파일 등록'}</span>
      <span class="badge" title="5주차 이후 딥러닝 분석 결과가 채워집니다">분석 전</span></div>
      <div>${esc(r.content)}</div></div>`;
  }

  async function loadReviews(page) {
    state.reviewPage = page;
    const q = rangeQuery('#revFrom', '#revTo');
    q.set('page', page); q.set('size', 10);
    try {
      const r = await api('GET', `/stores/${state.storeId}/reviews?${q}`);
      $('#reviewList').innerHTML = r.content.length ? `<p class="muted small">총 ${num(r.totalElements)}건 · 최신순</p>${r.content.map(reviewItem).join('')}`
        : '<p class="muted">리뷰가 없습니다.</p>';
      $('#reviewPager').innerHTML = r.totalPages > 1 ? `<button ${page === 0 ? 'disabled' : ''} data-page="${page - 1}">이전</button>
        <span class="muted small">${page + 1} / ${r.totalPages}</span>
        <button ${page + 1 >= r.totalPages ? 'disabled' : ''} data-page="${page + 1}">다음</button>` : '';
      $$('#reviewPager [data-page]').forEach((b) => { b.onclick = () => loadReviews(Number(b.dataset.page)); });
    } catch (e) { toast(errorText(e), true); }
  }
  $('#revSearch').onclick = () => loadReviews(0);

  $('#reviewForm').addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const fd = new FormData(); fd.append('file', ev.target.file.files[0]);
    try {
      const r = await api('POST', `/stores/${state.storeId}/reviews/uploads`, fd);
      $('#reviewUploadResult').innerHTML = `<div class="result"><dl><dt>저장</dt><dd><span class="badge good">${num(r.acceptedRows)}</span></dd>
        <dt>중복 건너뜀</dt><dd>${num(r.duplicatedRows)}</dd><dt>오류 행</dt><dd>${num(r.errorRows)}</dd></dl></div>`;
      ev.target.reset();
      loadReviews(0);
    } catch (e) { $('#reviewUploadResult').innerHTML = `<div class="result bad"><b>${esc(e.code)}</b> ${esc(errorText(e))}</div>`; }
  });

  $('#collectBtn').addEventListener('click', async () => {
    const btn = $('#collectBtn'), box = $('#collectStatus');
    btn.disabled = true;
    try {
      const job = await api('POST', `/stores/${state.storeId}/review-collections`);
      await pollJob(job.jobId, box);
    } catch (e) {
      box.innerHTML = `<div class="result bad"><b>${esc(e.code)}</b> ${esc(errorText(e))}</div>`;
    } finally { btn.disabled = false; }
  });

  async function pollJob(jobId, box) {
    const started = Date.now();
    for (;;) {
      const j = await api('GET', `/jobs/${jobId}`);
      const sec = Math.round((Date.now() - started) / 1000);
      if (j.status === 'COMPLETED') {
        box.innerHTML = `<div class="result">${statusBadge(j.status)} 새 리뷰 <b>${num(j.processedCount)}</b>건 저장 (작업 #${j.jobId}, ${sec}초)</div>`;
        refreshStoreHeader(); loadReviews(0);
        return;
      }
      if (j.status === 'FAILED') {
        const d = j.errorDetail || {};
        box.innerHTML = `<div class="result bad">${statusBadge(j.status)} <b>${esc(d.code || '')}</b> ${esc(d.message || '')}${HINT[d.code] ? `<br>${esc(HINT[d.code])}` : ''}</div>`;
        return;
      }
      box.innerHTML = `<div class="progress"><div></div></div><span class="muted small">${statusBadge(j.status)} 작업 #${j.jobId} · ${sec}초 경과 (요청 간 2초씩 쉬며 읽는 중)</span>`;
      await new Promise((r) => setTimeout(r, 2000));
    }
  }

  // ---------- 메뉴 ----------
  async function loadMenus() {
    try {
      const menus = await api('GET', `/stores/${state.storeId}/menus`);
      const box = $('#menuList');
      box.innerHTML = menus.length ? `<table><thead><tr><th>POS 메뉴명</th><th>정규화 이름</th><th>별칭</th><th></th></tr></thead><tbody>
        ${menus.map((m) => `<tr><td>${esc(m.posName)}</td><td><code>${esc(m.normalizedName)}</code></td>
          <td>${m.aliases.map((a) => `<span class="chip">${esc(a)}</span>`).join(' ') || '<span class="muted">-</span>'}</td>
          <td><button class="link" data-alias="${m.menuId}">별칭 수정</button> · <button class="link danger" data-del="${m.menuId}">삭제</button></td></tr>`).join('')}
      </tbody></table>` : '<p class="muted">등록된 메뉴가 없습니다.</p>';
      $$('[data-del]', box).forEach((b) => {
        b.onclick = async () => {
          if (!confirm('이 메뉴를 삭제할까요?')) return;
          try { await api('DELETE', `/stores/${state.storeId}/menus/${b.dataset.del}`); toast('삭제했습니다.'); loadMenus(); refreshStoreHeader(); } catch (e) { toast(errorText(e), true); }
        };
      });
      $$('[data-alias]', box).forEach((b) => {
        b.onclick = async () => {
          const m = menus.find((x) => x.menuId === Number(b.dataset.alias));
          const v = prompt('별칭 (쉼표로 구분)', m.aliases.join(', '));
          if (v === null) return;
          try { await api('PATCH', `/stores/${state.storeId}/menus/${m.menuId}`, { aliases: splitList(v) }); loadMenus(); } catch (e) { toast(errorText(e), true); }
        };
      });
    } catch (e) { toast(errorText(e), true); }
  }
  const splitList = (v) => v.split(',').map((s) => s.trim()).filter(Boolean);

  $('#menuForm').addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const f = ev.target;
    try {
      const m = await api('POST', `/stores/${state.storeId}/menus`, { posName: f.posName.value, aliases: splitList(f.aliases.value) });
      toast(`등록: ${m.posName} → ${m.normalizedName}`);
      f.reset(); loadMenus(); refreshStoreHeader();
    } catch (e) { toast(errorText(e), true); }
  });

  // ---------- 플레이스 ----------
  function renderPlace() {
    const s = state.store;
    $('#placeCurrent').innerHTML = s?.placeConnected
      ? `현재 연결: <b>${esc(s.place.placeName)}</b> (${esc(s.place.placeId)}) · ${esc(s.place.address || '')}`
      : '아직 연결되지 않았습니다. 네이버 지도에서 매장 링크를 복사해 붙여 넣으세요.';
    $('#placePreview').innerHTML = '';
  }

  $('#placeForm').addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const out = $('#placePreview');
    out.innerHTML = '<div class="progress"><div></div></div>';
    try {
      const p = await api('POST', `/stores/${state.storeId}/place/preview`, { placeUrl: ev.target.placeUrl.value });
      out.innerHTML = `<div class="result"><dl><dt>이름</dt><dd><b>${esc(p.placeName)}</b></dd><dt>주소</dt><dd>${esc(p.address || '-')}</dd>
        <dt>placeId</dt><dd><code>${esc(p.placeId)}</code></dd></dl><p><button class="primary" id="connectBtn">이 플레이스로 연결</button></p></div>`;
      $('#connectBtn').onclick = async () => {
        try {
          await api('PUT', `/stores/${state.storeId}/place`, { placeId: p.placeId });
          toast('플레이스를 연결했습니다.');
          await refreshStoreHeader(); renderPlace();
        } catch (e) { toast(errorText(e), true); }
      };
    } catch (e) { out.innerHTML = `<div class="result bad"><b>${esc(e.code)}</b> ${esc(errorText(e))}</div>`; }
  });

  // ---------- 작업 이력 ----------
  $('#jobType').onchange = () => loadJobs();
  async function loadJobs() {
    const type = $('#jobType').value;
    try {
      const page = await api('GET', `/stores/${state.storeId}/jobs?size=50${type ? `&type=${type}` : ''}`);
      $('#jobList').innerHTML = page.content.length ? `<div class="table-wrap"><table><thead><tr><th>#</th><th>유형</th><th>상태</th>
        <th class="num">처리</th><th class="num">오류</th><th>요청</th><th>완료</th><th>사유</th></tr></thead><tbody>
        ${page.content.map((j) => `<tr><td>${j.jobId}</td><td>${esc(JOB_TYPE[j.type] || j.type)}</td><td>${statusBadge(j.status)}</td>
          <td class="num">${num(j.processedCount)}</td><td class="num">${num(j.errorCount)}</td>
          <td>${esc(fmtDateTime(j.requestedAt))}</td><td>${esc(fmtDateTime(j.finishedAt))}</td>
          <td class="small">${j.errorDetail ? `<b>${esc(j.errorDetail.code)}</b> ${esc(j.errorDetail.message)}` : ''}</td></tr>`).join('')}
      </tbody></table></div>` : '<p class="muted">작업이 없습니다.</p>';
    } catch (e) { toast(errorText(e), true); }
  }

  // ---------- 시작 ----------
  async function boot() {
    if (location.pathname === '/login/success') history.replaceState(null, '', '/');
    state.token = store.get('accessToken');
    if (!state.token && !(await refresh())) { showLogin(); return; }
    try { await enterApp(); } catch { if (await refresh()) { try { await enterApp(); return; } catch { /* 아래 */ } } showLogin(); }
  }
  boot();
})();

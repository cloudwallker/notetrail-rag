'use strict';
const $ = id => document.getElementById(id);
const sample = { title: 'Java 21 虚拟线程笔记', text: '虚拟线程由 Java 运行时调度，适合大量阻塞式 I/O 任务，例如等待数据库或 HTTP 请求。虚拟线程不会让 CPU 密集型计算自动变快。\n\n使用虚拟线程时，仍应限制下游数据库连接数和外部服务并发，避免把压力转移给依赖服务。资源的生命周期和超时策略同样重要。\n\n学习建议：用本地 HTTP 服务模拟延迟，对比平台线程与虚拟线程的资源消耗；记录吞吐量和失败率，不只观察平均响应时间。' };
function notice(message, error = false) { $('notice').hidden = false; $('notice').className = 'notice' + (error ? ' error' : ''); $('notice').textContent = message; }
async function api(path, options = {}) {
  const response = await fetch(path, { ...options, headers: { 'Content-Type': 'application/json', ...options.headers } });
  const value = response.status === 204 ? null : await response.json();
  if (!response.ok) throw new Error(value?.message || '请求失败（' + response.status + '）');
  return value;
}
function element(tag, text, className) { const node = document.createElement(tag); if (text != null) node.textContent = text; if (className) node.className = className; return node; }
function evidence(hits) {
  $('evidence').replaceChildren();
  if (!hits.length) { $('evidence').append(element('div', '没有相关检索片段', 'empty')); return; }
  hits.forEach((hit, index) => {
    const card = element('div', null, 'item');
    card.append(element('div', '[' + (index + 1) + '] ' + hit.title, 'item-title'));
    card.append(element('div', '片段 ' + hit.position + ' · BM25 ' + Number(hit.score).toFixed(3) + ' · ' + hit.documentId, 'meta'));
    card.append(element('pre', hit.text)); $('evidence').append(card);
  });
}
function showAnswer(value) {
  $('mode').textContent = value.mode === 'OPENAI_COMPATIBLE' ? '模型生成' : '本地摘录';
  $('answer').textContent = value.answer; evidence(value.citations);
}
async function loadHistory() {
  const values = await api('/api/questions'); $('history').replaceChildren();
  if (!values.length) { $('history').append(element('div', '尚无问答记录', 'empty')); return; }
  for (const value of values) {
    const card = element('div', null, 'item'); const button = element('button', value.question, 'link-button');
    button.addEventListener('click', () => showAnswer(value));
    card.append(button, element('div', new Date(value.createdAt).toLocaleString() + ' · ' + value.mode, 'meta'));
    $('history').append(card);
  }
}
async function loadDocuments() {
  const values = await api('/api/documents'); $('documents').replaceChildren();
  if (!values.length) { $('documents').append(element('div', '先导入一篇笔记', 'empty')); return; }
  for (const doc of values) {
    const card = element('div', null, 'item'); const head = element('div', null, 'item-head'); const remove = element('button', '删除', 'danger');
    remove.addEventListener('click', () => { if (confirm('删除这篇文档及其检索片段？历史问答快照会保留。')) action(async () => { await api('/api/documents/' + encodeURIComponent(doc.id), { method: 'DELETE' }); await loadDocuments(); notice('文档已删除，历史快照保留。'); }); });
    head.append(element('span', doc.title, 'item-title'), remove);
    card.append(head, element('div', doc.chunkCount + ' 个片段 · ' + new Date(doc.createdAt).toLocaleString(), 'meta')); $('documents').append(card);
  }
}
async function action(work) {
  const buttons = [...document.querySelectorAll('button')]; buttons.forEach(button => button.disabled = true);
  try { await work(); } catch (error) { notice(error.message, true); }
  finally { buttons.forEach(button => button.disabled = false); }
}
$('sample').addEventListener('click', () => { $('title').value = sample.title; $('text').value = sample.text; $('question').value = '虚拟线程适合什么场景'; });
$('file').addEventListener('change', () => action(async () => {
  const file = $('file').files[0]; if (!file) return;
  if (file.size > 400000) throw new Error('请选择不超过 400 KB 的 UTF-8 文本文件。');
  const text = await file.text();
  if ([...text].length > 100000) throw new Error('文档正文不能超过 100000 个 Unicode 码点。');
  $('text').value = text; if (!$('title').value) $('title').value = file.name.slice(0, 100);
  notice('文件已读取，点击“导入并分块”保存。');
}));
$('import').addEventListener('click', () => action(async () => {
  const doc = await api('/api/documents', { method: 'POST', body: JSON.stringify({ title: $('title').value, text: $('text').value }) });
  await loadDocuments(); notice('文档已导入，共 ' + doc.chunkCount + ' 个片段。');
}));
function queryBody(key) { return JSON.stringify({ [key]: $('question').value, topK: Number($('top-k').value) }); }
$('search').addEventListener('click', () => action(async () => { const result = await api('/api/search', { method: 'POST', body: queryBody('query') }); evidence(result.hits); $('mode').textContent = '仅检索'; $('answer').textContent = '以下是当前检索证据，尚未生成回答。'; notice('找到 ' + result.hits.length + ' 个相关片段。'); }));
$('ask').addEventListener('click', () => action(async () => { const answer = await api('/api/questions', { method: 'POST', body: queryBody('question') }); showAnswer(answer); await loadHistory(); notice('回答和引用快照已保存。'); }));
$('refresh').addEventListener('click', () => action(loadDocuments));
action(async () => { await loadDocuments(); await loadHistory(); });

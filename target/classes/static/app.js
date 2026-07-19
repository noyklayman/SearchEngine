const views = {
  search: document.getElementById('search-view'),
  crawler: document.getElementById('crawler-view')
};

const navButtons = document.querySelectorAll('.nav-button');
const searchForm = document.getElementById('search-form');
const searchInput = document.getElementById('search-input');
const resultsSection = document.getElementById('results-section');
const resultsList = document.getElementById('results-list');
const searchState = document.getElementById('search-state');
const resultsMeta = document.getElementById('results-meta');
const resultsTitle = document.getElementById('results-title');
const crawlForm = document.getElementById('crawl-form');
let crawlTimer = null;
let currentMaxUrls = 100;

navButtons.forEach(button => {
  button.addEventListener('click', () => switchView(button.dataset.view));
});

document.querySelectorAll('.quick-searches button').forEach(button => {
  button.addEventListener('click', () => {
    searchInput.value = button.textContent.trim();
    searchForm.requestSubmit();
  });
});

document.getElementById('clear-search').addEventListener('click', () => {
  resultsSection.classList.add('hidden');
  document.querySelector('.hero').scrollIntoView({ behavior: 'smooth' });
  searchInput.focus();
});

searchForm.addEventListener('submit', async event => {
  event.preventDefault();
  const query = searchInput.value.trim();
  if (!query) return;

  resultsSection.classList.remove('hidden');
  resultsList.innerHTML = '';
  searchState.innerHTML = '<div class="state-card"><div class="loader"></div>Searching indexed pages…</div>';
  resultsTitle.textContent = `Results for “${query}”`;
  resultsMeta.textContent = 'Searching…';
  resultsSection.scrollIntoView({ behavior: 'smooth', block: 'start' });

  try {
    const response = await fetch(`/api/search?query=${encodeURIComponent(query)}&size=20`);
    if (!response.ok) throw new Error(`Search failed (${response.status})`);
    const data = await response.json();
    renderResults(data);
  } catch (error) {
    searchState.innerHTML = `<div class="state-card"><strong>Could not complete the search.</strong><br>${escapeHtml(error.message)}. Make sure Elasticsearch and the Spring Boot application are running.</div>`;
    resultsMeta.textContent = 'Search unavailable';
  }
});

crawlForm.addEventListener('submit', async event => {
  event.preventDefault();
  const payload = {
    url: document.getElementById('crawl-url').value.trim(),
    maxDistance: Number(document.getElementById('max-distance').value),
    maxUrls: Number(document.getElementById('max-urls').value),
    maxSeconds: Number(document.getElementById('max-seconds').value)
  };
  currentMaxUrls = payload.maxUrls;

  updateCrawlBadge('running', 'Starting');
  document.getElementById('crawl-message').textContent = 'Sending crawl request to the API…';

  try {
    const response = await fetch('/api/crawl', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    if (!response.ok) throw new Error(`Crawler request failed (${response.status})`);
    const crawlId = await response.text();
    document.getElementById('crawl-id').textContent = crawlId;
    document.getElementById('crawl-message').textContent = 'Crawler is running through Kafka.';
    beginStatusPolling(crawlId);
  } catch (error) {
    updateCrawlBadge('error', 'Error');
    document.getElementById('crawl-message').textContent = `${error.message}. Check Kafka, Redis and the application logs.`;
  }
});

function switchView(name) {
  Object.entries(views).forEach(([key, element]) => element.classList.toggle('active-view', key === name));
  navButtons.forEach(button => button.classList.toggle('active', button.dataset.view === name));
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function renderResults(data) {
  searchState.innerHTML = '';
  resultsMeta.textContent = `${Number(data.total || 0).toLocaleString()} indexed matches · ${data.tookMs || 0} ms`;
  const results = Array.isArray(data.results) ? data.results : [];

  if (!results.length) {
    searchState.innerHTML = '<div class="state-card"><strong>No matching pages were found.</strong><br>Run the crawler first or try a different search term.</div>';
    return;
  }

  resultsList.innerHTML = results.map(result => `
    <article class="result-card">
      <div class="result-domain">${escapeHtml(result.url)}</div>
      <h3><a href="${escapeAttribute(result.url)}" target="_blank" rel="noopener noreferrer">${escapeHtml(result.title || result.url)}</a></h3>
      <p>${highlight(escapeHtml(result.snippet || ''), searchInput.value.trim())}</p>
      <div class="result-meta">Depth ${result.level} · Crawl ${escapeHtml(result.crawlId || 'unknown')} · Score ${Number(result.score || 0).toFixed(2)}</div>
    </article>
  `).join('');
}

function beginStatusPolling(crawlId) {
  if (crawlTimer) window.clearInterval(crawlTimer);
  pollCrawl(crawlId);
  crawlTimer = window.setInterval(() => pollCrawl(crawlId), 1800);
}

async function pollCrawl(crawlId) {
  try {
    const response = await fetch(`/api/crawl/${encodeURIComponent(crawlId)}`);
    if (!response.ok) throw new Error('Status unavailable');
    const data = await response.json();
    document.getElementById('pages-count').textContent = Number(data.numPages || 0).toLocaleString();
    document.getElementById('distance-count').textContent = data.distance ?? 0;
    document.getElementById('started-at').textContent = formatDate(data.startTime);
    document.getElementById('last-update').textContent = formatDate(data.lastModified);
    const progress = Math.min(100, Math.round(((data.numPages || 0) / Math.max(currentMaxUrls, 1)) * 100));
    document.getElementById('crawl-progress').style.width = `${Math.max(progress, 4)}%`;

    if (data.stopReason) {
      updateCrawlBadge('complete', 'Completed');
      document.getElementById('crawl-message').textContent = `Stopped because the ${humanize(data.stopReason)} limit was reached.`;
      window.clearInterval(crawlTimer);
      crawlTimer = null;
    } else {
      updateCrawlBadge('running', 'Running');
      document.getElementById('crawl-message').textContent = 'Discovering and indexing pages…';
    }
  } catch (error) {
    updateCrawlBadge('error', 'Waiting');
    document.getElementById('crawl-message').textContent = 'Waiting for crawl status. Redis or Kafka may still be starting.';
  }
}

function updateCrawlBadge(state, label) {
  const badge = document.getElementById('crawl-badge');
  badge.className = `badge ${state}`;
  badge.textContent = label;
}

function formatDate(value) {
  if (!value) return '—';
  return String(value).replace('T', ' ').slice(0, 19);
}

function humanize(value) {
  return String(value).replace(/([A-Z])/g, ' $1').toLowerCase();
}

function highlight(text, query) {
  if (!query) return text;
  const escaped = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return text.replace(new RegExp(`(${escaped})`, 'gi'), '<mark>$1</mark>');
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>'"]/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[char]));
}

function escapeAttribute(value) {
  const stringValue = String(value ?? '');
  return /^https?:\/\//i.test(stringValue) ? escapeHtml(stringValue) : '#';
}


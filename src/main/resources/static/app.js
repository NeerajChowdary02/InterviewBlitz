'use strict';

const App = (() => {

    // ── State ──────────────────────────────────────────────────────────────
    let currentProblem  = null;  // Problem object from /api/quiz/next
    let questions       = [];    // Question array for current problem
    let questionIndex   = 0;     // Index into questions[]
    let correctCount    = 0;     // Correct answers in current problem session

    // ── Screen routing ─────────────────────────────────────────────────────
    function showScreen(id) {
        document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
        document.getElementById('screen-' + id).classList.add('active');
        document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
        const navBtn = document.getElementById('nav-' + id);
        if (navBtn) navBtn.classList.add('active');
    }

    function showHome() {
        showScreen('home');
        loadOverview();
    }

    function showStats() {
        showScreen('stats');
        document.getElementById('nav-stats').classList.add('active');
        loadStats();
    }

    // ── Toast ───────────────────────────────────────────────────────────────
    let toastTimer;
    function toast(msg, duration = 3000) {
        const el = document.getElementById('toast');
        el.textContent = msg;
        el.classList.add('show');
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => el.classList.remove('show'), duration);
    }

    // ── Auth ─────────────────────────────────────────────────────────────────
    // Credentials are stored in sessionStorage so a page reload within the same
    // browser tab doesn't require re-entering them.
    let _authHeader = sessionStorage.getItem('ib_auth') || null;
    let _credentialPromise = null;  // deduplicate concurrent 401 prompts

    function buildAuthHeader(username, password) {
        return 'Basic ' + btoa(unescape(encodeURIComponent(username + ':' + password)));
    }

    function promptCredentials(errorMsg = null) {
        // If a prompt is already visible, all callers share the same promise
        if (_credentialPromise) return _credentialPromise;

        _credentialPromise = new Promise(resolve => {
            const overlay = document.createElement('div');
            overlay.style.cssText = [
                'position:fixed;inset:0;background:rgba(0,0,0,0.72)',
                'display:flex;align-items:center;justify-content:center;z-index:9999',
                'padding:16px;box-sizing:border-box',
            ].join(';');

            const errorHtml = errorMsg
                ? `<div style="color:#e74c3c;font-size:0.83rem;margin-bottom:14px">${errorMsg}</div>`
                : '';

            overlay.innerHTML = `
              <div style="background:#0f3460;border:1px solid #1e4d80;border-radius:16px;
                          padding:32px 24px;width:min(340px,100%);color:#e2e2e2">
                <div style="font-size:1.3rem;font-weight:700;color:#16c79a;margin-bottom:4px">
                  InterviewBlitz
                </div>
                <div style="font-size:0.85rem;color:#8892a4;margin-bottom:22px">
                  Sign in to continue
                </div>
                ${errorHtml}
                <input id="auth-user" type="text" placeholder="Username"
                  autocomplete="username" spellcheck="false"
                  style="display:block;width:100%;padding:13px 14px;background:#1a1a2e;
                         border:1px solid #1e4d80;border-radius:9px;color:#e2e2e2;
                         font-size:1rem;margin-bottom:10px;outline:none;box-sizing:border-box">
                <input id="auth-pass" type="password" placeholder="Password"
                  autocomplete="current-password"
                  style="display:block;width:100%;padding:13px 14px;background:#1a1a2e;
                         border:1px solid #1e4d80;border-radius:9px;color:#e2e2e2;
                         font-size:1rem;margin-bottom:22px;outline:none;box-sizing:border-box">
                <button id="auth-submit"
                  style="display:block;width:100%;padding:14px;background:#16c79a;
                         color:#0d0d1a;border:none;border-radius:10px;font-size:1rem;
                         font-weight:700;cursor:pointer;min-height:48px">
                  Sign In
                </button>
              </div>`;

            document.body.appendChild(overlay);

            const userInput = overlay.querySelector('#auth-user');
            const passInput = overlay.querySelector('#auth-pass');
            const submitBtn = overlay.querySelector('#auth-submit');

            function attempt() {
                const u = userInput.value.trim();
                const p = passInput.value;
                if (!u || !p) return;
                _authHeader = buildAuthHeader(u, p);
                sessionStorage.setItem('ib_auth', _authHeader);
                document.body.removeChild(overlay);
                _credentialPromise = null;
                resolve();
            }

            submitBtn.addEventListener('click', attempt);
            passInput.addEventListener('keydown', e => { if (e.key === 'Enter') attempt(); });
            userInput.addEventListener('keydown', e => { if (e.key === 'Enter') passInput.focus(); });
            setTimeout(() => userInput.focus(), 60);
        });

        return _credentialPromise;
    }

    // ── API helpers ─────────────────────────────────────────────────────────
    async function api(path, options = {}) {
        const headers = { ...(options.headers || {}) };
        if (_authHeader) headers['Authorization'] = _authHeader;

        const res = await fetch(path, { ...options, headers, credentials: 'include' });

        if (res.status === 401) {
            // Credentials missing or wrong — clear stale auth and prompt
            const wasAuthed = !!_authHeader;
            _authHeader = null;
            sessionStorage.removeItem('ib_auth');
            await promptCredentials(wasAuthed ? 'Incorrect credentials — please try again.' : null);
            return api(path, options);  // retry with new credentials
        }

        if (!res.ok) {
            const body = await res.json().catch(() => ({}));
            throw new Error(body.error || `HTTP ${res.status}`);
        }
        return res.json();
    }

    // ── Home / Overview ─────────────────────────────────────────────────────
    async function loadOverview() {
        try {
            const data = await api('/api/stats/overview');
            document.getElementById('stat-due').textContent = data.totalDueToday ?? '—';
            document.getElementById('stat-reviewed').textContent = data.totalReviewed ?? '—';
            document.getElementById('stat-streak').textContent = data.currentStreak ?? '—';
            const acc = data.overallAccuracy;
            document.getElementById('stat-accuracy').textContent =
                (acc != null) ? Math.round(acc * 100) + '%' : '—';

            const startBtn = document.getElementById('btn-start');
            if (data.totalDueToday > 0) {
                startBtn.textContent = `Start Review (${data.totalDueToday})`;
                startBtn.disabled = false;
            } else {
                startBtn.textContent = 'All Caught Up';
                startBtn.disabled = true;
            }
        } catch (e) {
            toast('Could not load stats: ' + e.message);
        }
    }

    // ── Sync ────────────────────────────────────────────────────────────────
    async function syncProblems() {
        toast('Syncing with LeetCode…', 8000);
        try {
            const data = await api('/api/problems/sync/codingknight2625');
            toast(`Sync done — ${data.newProblemsSynced} new problems added.`);
            loadOverview();
        } catch (e) {
            toast('Sync failed: ' + e.message);
        }
    }

    // ── Quiz: start ─────────────────────────────────────────────────────────
    async function startQuiz() {
        showScreen('quiz');
        document.getElementById('nav-home').classList.add('active');
        await loadNextProblem();
    }

    async function loadNextProblem() {
        setQuizState('loading', 'Loading next problem…');
        currentProblem = null;
        questions = [];
        questionIndex = 0;
        correctCount = 0;

        try {
            const data = await api('/api/quiz/next');

            if (data.message) {
                // All caught up
                document.getElementById('next-review-msg').textContent =
                    data.nextReviewDate ? 'Next review: ' + data.nextReviewDate : '';
                setQuizState('caught-up');
                return;
            }

            currentProblem = data;
            await loadQuestionsForProblem();
        } catch (e) {
            toast('Error loading problem: ' + e.message);
            setQuizState('caught-up');
        }
    }

    async function loadQuestionsForProblem() {
        if (!currentProblem) return;

        setQuizState('loading', 'Fetching questions…');
        try {
            let qs = await api('/api/quiz/questions/' + currentProblem.id);

            // Handle wrapped response {message, questions:[]}
            if (!Array.isArray(qs)) qs = qs.questions || [];

            if (qs.length === 0) {
                // Generate questions via OpenAI
                setQuizState('loading', 'Generating questions with AI…');
                qs = await api('/api/quiz/generate/' + currentProblem.id, { method: 'POST' });
                if (!Array.isArray(qs)) qs = qs.questions || [];
            }

            if (qs.length === 0) {
                toast('No questions available — skipping problem.');
                await loadNextProblem();
                return;
            }

            questions = qs;
            renderProblemHeader();
            renderQuestion();
            setQuizState('active');
        } catch (e) {
            toast('Could not load questions: ' + e.message);
            setQuizState('caught-up');
        }
    }

    // ── Quiz: rendering ──────────────────────────────────────────────────────
    function setQuizState(state, loadingText = '') {
        document.getElementById('quiz-loading').style.display    = state === 'loading'  ? '' : 'none';
        document.getElementById('quiz-caught-up').style.display  = state === 'caught-up'? '' : 'none';
        document.getElementById('quiz-active').style.display     = state === 'active'   ? '' : 'none';
        document.getElementById('quiz-summary').style.display    = state === 'summary'  ? '' : 'none';
        if (state === 'loading') {
            document.getElementById('quiz-loading-text').textContent = loadingText;
        }
    }

    function renderProblemHeader() {
        const p = currentProblem;
        document.getElementById('q-topic').textContent   = (p.topic || '').toUpperCase();
        document.getElementById('q-title').textContent   = p.title || '';
        const badge = document.getElementById('q-difficulty');
        badge.textContent   = p.difficulty || '';
        badge.className     = 'difficulty-badge difficulty-' + (p.difficulty || 'Medium');

        const slug = p.description || '';
        const link = slug ? 'https://leetcode.com/problems/' + slug + '/' : 'https://leetcode.com';
        document.getElementById('q-leetcode-link').href = link;
    }

    function renderQuestion() {
        const q = questions[questionIndex];
        if (!q) return;

        const total = questions.length;
        const num   = questionIndex + 1;

        document.getElementById('q-progress-text').textContent = num + ' / ' + total;
        document.getElementById('q-progress-bar').style.width  = (num / total * 100) + '%';
        document.getElementById('q-type').textContent = formatQuestionType(q.questionType);
        document.getElementById('q-text').textContent = q.questionText || '';
        document.getElementById('q-explanation').style.display = 'none';
        document.getElementById('btn-next-q').style.display    = 'none';

        const opts = [
            { key: 'A', text: q.optionA },
            { key: 'B', text: q.optionB },
            { key: 'C', text: q.optionC },
            { key: 'D', text: q.optionD },
        ];

        const container = document.getElementById('q-options');
        container.innerHTML = '';
        opts.forEach(({ key, text }) => {
            const btn = document.createElement('button');
            btn.className = 'option-btn';
            btn.innerHTML = `<span class="option-label">${key}</span><span>${escHtml(text || '')}</span>`;
            btn.addEventListener('click', () => submitAnswer(q.id, key));
            container.appendChild(btn);
        });
    }

    function formatQuestionType(t) {
        if (!t) return '';
        return t.replace(/_/g, ' ');
    }

    function escHtml(str) {
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    // ── Quiz: answer submission ──────────────────────────────────────────────
    async function submitAnswer(questionId, selectedOption) {
        // Disable all option buttons immediately
        const optionBtns = document.querySelectorAll('#q-options .option-btn');
        optionBtns.forEach(b => { b.disabled = true; });

        try {
            const res = await api('/api/quiz/submit', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ questionId, selectedOption }),
            });

            if (res.correct) correctCount++;

            // Highlight correct and wrong options
            const optLabels = ['A', 'B', 'C', 'D'];
            optionBtns.forEach((btn, i) => {
                const key = optLabels[i];
                if (key === res.correctOption) {
                    btn.classList.add('correct');
                } else if (key === selectedOption && !res.correct) {
                    btn.classList.add('wrong');
                }
            });

            // Show explanation
            if (res.explanation) {
                document.getElementById('q-explanation-text').textContent = res.explanation;
                document.getElementById('q-explanation').style.display = '';
            }

            // Show next button or finish
            const isLast = questionIndex === questions.length - 1;
            const nextBtn = document.getElementById('btn-next-q');
            nextBtn.style.display = '';
            nextBtn.textContent = isLast ? 'See Results' : 'Next Question';

        } catch (e) {
            toast('Submit error: ' + e.message);
            optionBtns.forEach(b => { b.disabled = false; });
        }
    }

    function nextQuestion() {
        questionIndex++;
        if (questionIndex >= questions.length) {
            showSummary();
        } else {
            renderQuestion();
        }
    }

    // ── Quiz: summary ────────────────────────────────────────────────────────
    function showSummary() {
        const total   = questions.length;
        const pct     = total > 0 ? Math.round(correctCount / total * 100) : 0;
        const scoreEl = document.getElementById('summary-score');
        scoreEl.textContent = correctCount + '/' + total + ' (' + pct + '%)';
        scoreEl.style.color = pct >= 70 ? 'var(--accent)' : 'var(--wrong)';

        const labelMap = { 4: 'Perfect! 🎯', 3: 'Great job! 💪', 2: 'Good progress! 📈', 1: 'Keep practicing! 📚', 0: 'Review this topic! 🔄' };
        document.getElementById('summary-label').textContent = labelMap[correctCount] ?? 'Keep practicing! 📚';

        document.getElementById('summary-title').textContent = currentProblem?.title || '';

        const slug = currentProblem?.description || '';
        const link = slug ? 'https://leetcode.com/problems/' + slug + '/' : 'https://leetcode.com';
        document.getElementById('summary-leetcode-link').href = link;

        setQuizState('summary');
    }

    // ── Stats ────────────────────────────────────────────────────────────────
    async function loadStats() {
        try {
            const [topics, weakAreas] = await Promise.all([
                api('/api/stats/topics'),
                api('/api/stats/weak-areas'),
            ]);

            const weakTopics = new Set(weakAreas.map(w => w.topic));

            renderWeakAreas(weakAreas);
            renderTopics(topics, weakTopics);
        } catch (e) {
            toast('Could not load stats: ' + e.message);
            document.getElementById('weak-areas-list').innerHTML =
                '<div style="color:var(--muted);font-size:0.9rem">Failed to load.</div>';
            document.getElementById('topics-list').innerHTML =
                '<div style="color:var(--muted);font-size:0.9rem">Failed to load.</div>';
        }
    }

    function renderWeakAreas(weakAreas) {
        const container = document.getElementById('weak-areas-list');
        if (!weakAreas.length) {
            container.innerHTML = '<div style="color:var(--muted);font-size:0.9rem;text-align:center;padding:12px 0">No weak areas yet — keep reviewing!</div>';
            return;
        }
        container.innerHTML = weakAreas.map(t => topicRowHtml(t, true)).join('');
    }

    function renderTopics(topics, weakTopics) {
        const container = document.getElementById('topics-list');
        if (!topics.length) {
            container.innerHTML = '<div style="color:var(--muted);font-size:0.9rem;text-align:center;padding:12px 0">No topics found.</div>';
            return;
        }
        container.innerHTML = topics.map(t => topicRowHtml(t, weakTopics.has(t.topic))).join('');
    }

    function topicRowHtml(t, isWeak) {
        const pct      = Math.round((t.accuracy || 0) * 100);
        const barColor = pct >= 70 ? 'var(--accent)' : pct >= 40 ? '#ffa726' : 'var(--wrong)';
        const weakBadge = isWeak ? '<span class="weak-tag">Weak</span>' : '';
        return `
        <div class="topic-row">
          <div class="topic-row-header">
            <span class="topic-name">${escHtml(t.topic || '')}${weakBadge}</span>
            <span class="topic-pct">${pct}% accuracy &bull; ${t.reviewed} of ${t.totalProblems} reviewed</span>
          </div>
          <div class="topic-bar">
            <div class="topic-bar-fill" style="width:${pct}%;background:${barColor}"></div>
          </div>
        </div>`;
    }

    // ── Service Worker registration ──────────────────────────────────────────
    function registerSW() {
        if ('serviceWorker' in navigator) {
            navigator.serviceWorker.register('/sw.js').catch(() => {});
        }
    }

    // ── Init ─────────────────────────────────────────────────────────────────
    function init() {
        registerSW();
        showHome();
    }

    document.addEventListener('DOMContentLoaded', init);

    return { showHome, showStats, startQuiz, syncProblems, loadNextProblem, nextQuestion };

})();

// ExPense Book — Client Application Controller
// Complete SPA Implementation supporting Mode A (Track Only) & Mode B (Track + Budget)

let currentUser = null;
let currentToken = localStorage.getItem('eb_token') || null;
let currentView = 'dashboard';
let currentCalMonth = new Date().getMonth() + 1;
let currentCalYear = new Date().getFullYear();
let currentAnalyticsMonth = new Date().getMonth() + 1;
let currentAnalyticsYear = new Date().getFullYear();

// Chart instances
let dashTrendChartInstance = null;
let analyticsCatChartInstance = null;
let analyticsPayChartInstance = null;
let analyticsYearChartInstance = null;

// Temporary register state for onboarding
let pendingRegisterData = null;

// Helper for local date YYYY-MM-DD
function getTodayLocalDate() {
    const d = new Date();
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

// Category metadata palette matching the design reference UI
const CATEGORY_META = {
    'Food': { bg: '#FEF3D6', bar: '#F5C253', text: '#92570D' },
    'Travel': { bg: '#DDF4E8', bar: '#1B6E4A', text: '#0D382B' },
    'Shopping': { bg: '#E1F4F1', bar: '#195F54', text: '#0C4B42' },
    'Education': { bg: '#E6F5EC', bar: '#276B45', text: '#154D2F' },
    'Entertainment': { bg: '#FDF1D8', bar: '#D9822B', text: '#974C0A' },
    'Health': { bg: '#E3F3EC', bar: '#1E6B4A', text: '#0F4E34' },
    'Groceries': { bg: '#E8F5EB', bar: '#2E7D32', text: '#1B5E20' },
    'Bills': { bg: '#EDF2F0', bar: '#475569', text: '#334155' },
    'Utilities': { bg: '#FEF9C3', bar: '#CA8A04', text: '#854D0E' },
    'Recharge': { bg: '#E0F2FE', bar: '#0284C7', text: '#0369A1' },
    'Rent': { bg: '#F1EEFA', bar: '#7C3AED', text: '#5B21B6' },
    'Fitness': { bg: '#DCFCE7', bar: '#16A34A', text: '#166534' },
    'Personal Care': { bg: '#FCE7F3', bar: '#DB2777', text: '#9D174D' },
    'Subscription': { bg: '#EDE9FE', bar: '#8B5CF6', text: '#6D28D9' },
    'Electronics': { bg: '#DBEAFE', bar: '#2563EB', text: '#1E40AF' },
    'Gifts': { bg: '#FFEDD5', bar: '#EA580C', text: '#9A3412' },
    'Investment': { bg: '#D1FAE5', bar: '#059669', text: '#065F46' },
    'Savings': { bg: '#DDF4E8', bar: '#10B981', text: '#065F46' },
    'Saving': { bg: '#DDF4E8', bar: '#10B981', text: '#065F46' },
    'Salary': { bg: '#DCFCE7', bar: '#16A34A', text: '#166534' },
    'Other': { bg: '#F1F5F9', bar: '#64748B', text: '#334155' },
    'Others': { bg: '#F1F5F9', bar: '#64748B', text: '#334155' }
};

function getCategoryMeta(name) {
    if (!name) return CATEGORY_META['Other'];
    const trimmed = name.trim();
    if (CATEGORY_META[trimmed]) return CATEGORY_META[trimmed];
    const key = Object.keys(CATEGORY_META).find(k => k.toLowerCase() === trimmed.toLowerCase());
    if (key) return CATEGORY_META[key];
    return { bg: '#E8F5EF', bar: '#0D382B', text: '#0D382B' };
}

// --- Initialization ---
document.addEventListener('DOMContentLoaded', () => {
    initApp();
    const modal = document.getElementById('expense-modal');
    if (modal) {
        modal.addEventListener('click', (e) => {
            if (e.target === modal) closeExpenseModal();
        });
    }
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') closeExpenseModal();
    });

    // Mobile Sidebar Swipe-to-Close Touch Gestures
    const sidebarEl = document.getElementById('sidebar');
    if (sidebarEl) {
        let touchStartX = 0;
        let touchStartY = 0;
        sidebarEl.addEventListener('touchstart', (e) => {
            if (e.touches && e.touches[0]) {
                touchStartX = e.touches[0].clientX;
                touchStartY = e.touches[0].clientY;
            }
        }, { passive: true });

        sidebarEl.addEventListener('touchmove', (e) => {
            if (e.touches && e.touches[0]) {
                const touchEndX = e.touches[0].clientX;
                const touchEndY = e.touches[0].clientY;
                const diffX = touchEndX - touchStartX;
                const diffY = touchEndY - touchStartY;
                // If swiped left by > 36px and mostly horizontal
                if (diffX < -36 && Math.abs(diffX) > Math.abs(diffY)) {
                    closeMobileSidebar();
                }
            }
        }, { passive: true });
    }

    // Register Service Worker for PWA / offline resilience
    if ('serviceWorker' in navigator) {
        navigator.serviceWorker.register('/sw.js').catch(err => {
            console.log('SW registration note:', err);
        });
    }

    // Online / Offline Listeners
    window.addEventListener('online', () => {
        const banner = document.getElementById('offline-banner');
        if (banner) banner.style.display = 'none';
        showToast('Back online! Live sync connected.', 'success');
    });
    window.addEventListener('offline', () => {
        const banner = document.getElementById('offline-banner');
        if (banner) banner.style.display = 'block';
        showToast('You are offline. Running in cached offline mode.', 'info');
    });
    if (!navigator.onLine) {
        const banner = document.getElementById('offline-banner');
        if (banner) banner.style.display = 'block';
    }
});

async function initApp() {
    const splash = document.getElementById('app-splash-loader');
    if (currentToken) {
        document.documentElement.classList.add('user-logged-in');
        try {
            const resp = await apiFetch('/api/auth/current');
            if (resp && resp.user) {
                currentUser = resp.user;
                if (splash) {
                    splash.style.opacity = '0';
                    setTimeout(() => { splash.style.display = 'none'; }, 300);
                }
                showAppUI();
                return;
            }
        } catch (e) {
            console.warn('Session expired or invalid:', e);
            localStorage.removeItem('eb_token');
            currentToken = null;
            currentUser = null;
            document.documentElement.classList.remove('user-logged-in');
        }
    }
    document.documentElement.classList.remove('user-logged-in');
    if (splash) {
        splash.style.opacity = '0';
        setTimeout(() => { splash.style.display = 'none'; }, 300);
    }
    showAuthUI();
}

// --- API Fetch Utility ---
async function apiFetch(endpoint, options = {}) {
    const headers = options.headers || {};
    if (currentToken) {
        headers['Authorization'] = `Bearer ${currentToken}`;
    }
    if (options.body && typeof options.body === 'object' && !(options.body instanceof FormData)) {
        headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(options.body);
    }
    options.headers = headers;

    const res = await fetch(endpoint, options);
    if (res.status === 401) {
        // Unauthorized
        localStorage.removeItem('eb_token');
        currentToken = null;
        currentUser = null;
        showAuthUI();
        throw new Error('Session expired. Please sign in.');
    }
    if (!res.ok) {
        let errMessage = 'Request failed';
        try {
            const errData = await res.json();
            if (errData && errData.error) errMessage = errData.error;
        } catch (ignored) { }
        throw new Error(errMessage);
    }
    const contentType = res.headers.get('content-type');
    if (contentType && contentType.includes('application/json')) {
        return await res.json();
    }
    return res;
}

// --- Toast Notification System ---
function showToast(message, type = 'info') {
    let container = document.getElementById('toast-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toast-container';
        container.style.cssText = 'position: fixed; top: 24px; right: 24px; z-index: 999999; display: flex; flex-direction: column; gap: 10px; max-width: 380px; pointer-events: none;';
        document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    const bg = type === 'error' ? '#C25953' : (type === 'success' ? '#2D6B50' : '#26332F');
    const icon = type === 'error' ? '⚠️' : (type === 'success' ? '✅' : 'ℹ️');
    toast.style.cssText = `background: ${bg}; color: #ffffff; padding: 12px 18px; border-radius: 10px; font-weight: 600; font-size: 0.9rem; box-shadow: 0 10px 30px rgba(0,0,0,0.25); display: flex; align-items: center; gap: 10px; pointer-events: auto; transition: all 0.3s ease;`;
    toast.innerHTML = `<span>${icon}</span><span style="flex: 1;">${message}</span>`;
    container.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(-10px)';
        setTimeout(() => toast.remove(), 300);
    }, 3500);
}

// --- Auth UI Management ---

// --- Tabbed Auth UI Controller ---
function showAuthTab(tab) {
    const loginForm = document.getElementById('form-login');
    const registerForm = document.getElementById('form-register');
    const personalizeStep = document.getElementById('step-personalize');
    const tabLogin = document.getElementById('tab-login-btn');
    const tabRegister = document.getElementById('tab-register-btn');
    const switcher = document.getElementById('auth-tab-switch');
    const errBanner = document.getElementById('auth-error-banner');

    if (errBanner) errBanner.style.display = 'none';
    if (personalizeStep) personalizeStep.style.display = 'none';
    if (switcher) switcher.style.display = 'flex';

    if (tab === 'login') {
        if (loginForm) loginForm.style.display = 'block';
        if (registerForm) registerForm.style.display = 'none';
        if (tabLogin) tabLogin.classList.add('active');
        if (tabRegister) tabRegister.classList.remove('active');
    } else {
        if (loginForm) loginForm.style.display = 'none';
        if (registerForm) registerForm.style.display = 'block';
        if (tabLogin) tabLogin.classList.remove('active');
        if (tabRegister) tabRegister.classList.add('active');
    }
}

function showLogin() {
    showAuthTab('login');
}

function showRegister() {
    showAuthTab('register');
}

function togglePasswordVisibility(inputId, btn) {
    const input = document.getElementById(inputId);
    if (!input) return;
    if (input.type === 'password') {
        input.type = 'text';
        btn.innerHTML = `<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path><line x1="1" y1="1" x2="23" y2="23"></line></svg>`;
    } else {
        input.type = 'password';
        btn.innerHTML = `<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg>`;
    }
}

function showAuthUI() {
    document.documentElement.classList.remove('user-logged-in');
    const splash = document.getElementById('app-splash-loader');
    if (splash) splash.style.display = 'none';

    const auth = document.getElementById('auth-container');
    const app = document.getElementById('app-container');
    if (auth) auth.style.display = 'flex';
    if (app) app.style.display = 'none';

    // Strictly ensure all floating and nav items are hidden
    const fab = document.getElementById('ai-floating-fab');
    if (fab) fab.style.display = 'none';
    const drawer = document.getElementById('ai-floating-drawer');
    if (drawer) drawer.style.display = 'none';
    const bottomNav = document.getElementById('mobile-nav-bottom');
    if (bottomNav) bottomNav.style.display = 'none';

    showAuthTab('login');
}

function showAppUI() {
    document.documentElement.classList.add('user-logged-in');
    const splash = document.getElementById('app-splash-loader');
    if (splash) splash.style.display = 'none';

    const auth = document.getElementById('auth-container');
    const app = document.getElementById('app-container');
    if (auth) auth.style.display = 'none';
    if (app) app.style.display = 'flex';

    const fab = document.getElementById('ai-floating-fab');
    if (fab) fab.style.display = 'flex';

    const bottomNav = document.getElementById('mobile-nav-bottom');
    if (bottomNav && window.innerWidth <= 900) {
        bottomNav.style.display = 'flex';
    }

    updateUserHeaderAndSidebar();
    populateCategoryDropdowns();
    initAnalyticsPickers();
    navigate('dashboard');
}

function showOnboardingStep(step) {
    document.getElementById('step-welcome').style.display = step === 1 ? 'block' : 'none';
    document.getElementById('step-register').style.display = step === 2 ? 'block' : 'none';
    document.getElementById('step-personalize').style.display = step === 3 ? 'block' : 'none';
    document.getElementById('step-login').style.display = 'none';
}

function showLogin() {
    document.getElementById('step-welcome').style.display = 'none';
    document.getElementById('step-register').style.display = 'none';
    document.getElementById('step-personalize').style.display = 'none';
    document.getElementById('step-login').style.display = 'block';
    const errBox = document.getElementById('login-error');
    if (errBox) errBox.style.display = 'none';
}

function fillAdminCredentials() {
    showAuthTab('login');
    const emailEl = document.getElementById('login-email');
    const passEl = document.getElementById('login-pass');
    if (emailEl) emailEl.value = 'admin@expensebook.com';
    if (passEl) passEl.value = 'SURAJ@260203';
    showToast('Admin credentials filled!', 'info');
}

function selectPersonalizeMode(mode) {
    window.selectedOnboardingMode = mode;
    const trackCard = document.getElementById('mode-opt-track');
    const budgetCard = document.getElementById('mode-opt-budget');
    if (mode === 'TRACK_ONLY') {
        trackCard.style.border = '2px solid #78BFA0';
        trackCard.style.boxShadow = '0 4px 12px rgba(120, 191, 160, 0.2)';
        budgetCard.style.border = '1px solid var(--border-color)';
        budgetCard.style.boxShadow = 'none';
    } else {
        budgetCard.style.border = '2px solid #5FA787';
        budgetCard.style.boxShadow = '0 4px 12px rgba(169, 207, 224, 0.3)';
        trackCard.style.border = '1px solid var(--border-color)';
        trackCard.style.boxShadow = 'none';
    }
}

function handleRegisterSubmit(e) {
    e.preventDefault();
    const name = document.getElementById('reg-name').value.trim();
    const email = document.getElementById('reg-email').value.trim();
    const password = document.getElementById('reg-pass').value;
    const confirmPassword = document.getElementById('reg-conf-pass').value;
    const errBox = document.getElementById('auth-error-banner');

    if (errBox) errBox.style.display = 'none';

    if (password !== confirmPassword) {
        if (errBox) {
            errBox.textContent = 'Passwords do not match.';
            errBox.style.display = 'block';
        }
        return;
    }

    pendingRegisterData = { fullName: name, email, password, confirmPassword };
    window.selectedOnboardingMode = 'TRACK_ONLY';

    const loginForm = document.getElementById('form-login');
    const registerForm = document.getElementById('form-register');
    const personalizeStep = document.getElementById('step-personalize');
    const switcher = document.getElementById('auth-tab-switch');

    if (loginForm) loginForm.style.display = 'none';
    if (registerForm) registerForm.style.display = 'none';
    if (switcher) switcher.style.display = 'none';
    if (personalizeStep) personalizeStep.style.display = 'block';
}

async function finalizePersonalization() {
    if (!pendingRegisterData) {
        showOnboardingStep(2);
        return;
    }
    const mode = window.selectedOnboardingMode || 'TRACK_ONLY';
    const payload = { ...pendingRegisterData, mode };

    try {
        const resp = await apiFetch('/api/auth/register', {
            method: 'POST',
            body: payload
        });
        if (resp && resp.token) {
            currentToken = resp.token;
            currentUser = resp.user;
            localStorage.setItem('eb_token', currentToken);
            showAppUI();
        }
    } catch (err) {
        alert('Registration error: ' + err.message);
        showOnboardingStep(2);
    }
}

async function handleLoginSubmit(e) {
    e.preventDefault();
    const email = document.getElementById('login-email').value.trim();
    const password = document.getElementById('login-pass').value;
    const errBox = document.getElementById('auth-error-banner');
    const submitBtn = document.getElementById('btn-login-submit');

    if (errBox) errBox.style.display = 'none';
    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.textContent = 'Signing In...';
    }

    try {
        const resp = await apiFetch('/api/auth/login', {
            method: 'POST',
            body: { email, password }
        });
        if (resp && resp.token) {
            currentToken = resp.token;
            currentUser = resp.user;
            localStorage.setItem('eb_token', currentToken);
            showToast(`Welcome back, ${currentUser.fullName || 'User'}!`, 'success');
            showAppUI();
        }
    } catch (err) {
        if (errBox) {
            errBox.textContent = err.message || 'Invalid email or password.';
            errBox.style.display = 'block';
        }
    } finally {
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Sign In →';
        }
    }
}

async function handleLogout() {
    if (!confirm('Are you sure you want to sign out?')) return;
    try {
        await apiFetch('/api/auth/logout', { method: 'POST' });
    } catch (ignored) { }
    localStorage.removeItem('eb_token');
    currentToken = null;
    currentUser = null;
    const fab = document.getElementById('ai-floating-fab');
    if (fab) fab.style.display = 'none';
    const drawer = document.getElementById('ai-floating-drawer');
    if (drawer) drawer.style.display = 'none';
    showAuthUI();
}

// --- Navigation & Mode Switching ---
function updateUserHeaderAndSidebar() {
    if (!currentUser) return;
    document.getElementById('user-display-name').textContent = currentUser.fullName || 'User';
    const isBudget = currentUser.financialMode === 'TRACK_AND_BUDGET';
    const modeText = isBudget ? 'Track + Budget' : 'Track Only';
    document.getElementById('user-display-mode').textContent = modeText;

    const modeBadge = document.getElementById('top-mode-badge');
    if (modeBadge) {
        modeBadge.textContent = isBudget ? 'Track + Budget' : 'Track Only';
        modeBadge.className = isBudget ? 'badge badge-blue' : 'badge badge-sage';
    }

    const budgetNav = document.getElementById('nav-item-budget');
    if (budgetNav) {
        budgetNav.style.display = isBudget ? 'flex' : 'none';
    }

    const adminNav = document.getElementById('nav-item-admin');
    if (adminNav) {
        adminNav.style.display = currentUser.role === 'ADMIN' ? 'flex' : 'none';
    }

    // Greet only Hey! [Name]
    const firstName = (currentUser && currentUser.fullName) ? currentUser.fullName.split(' ')[0] : 'User';
    const topGreeting = document.getElementById('top-greeting');
    if (topGreeting) {
        topGreeting.textContent = `Hey! ${firstName}`;
    }
}

function navigate(viewName) {
    if (!currentUser || !currentToken) {
        showAuthUI();
        return;
    }
    currentView = viewName;

    // Hide all views
    document.querySelectorAll('.view-panel').forEach(el => el.style.display = 'none');

    // Show selected view
    const target = document.getElementById(`view-${viewName}`);
    if (target) target.style.display = 'block';

    // Update active nav links
    document.querySelectorAll('.nav-item').forEach(item => {
        item.classList.remove('active');
        if (item.getAttribute('onclick') && item.getAttribute('onclick').includes(viewName)) {
            item.classList.add('active');
        }
    });

    // Update mobile bottom nav
    document.querySelectorAll('.mobile-nav-item').forEach(item => {
        item.classList.remove('active');
        if (item.getAttribute('onclick') && item.getAttribute('onclick').includes(viewName)) {
            item.classList.add('active');
        }
    });

    // Close mobile sidebar if open
    closeMobileSidebar();

    // Trigger view-specific loads
    switch (viewName) {
        case 'dashboard':
            loadDashboard();
            break;
        case 'transactions':
            loadTransactions();
            break;
        case 'calendar':
            loadCalendar();
            break;
        case 'analytics':
            loadAnalytics();
            break;
        case 'budget':
            loadBudgetView();
            break;
        case 'income':
            loadIncomeView();
            break;
        case 'recurring':
            loadRecurringView();
            break;
        case 'reports':
            loadReportsView();
            break;
        case 'ai-assistant':
            loadAiAssistant();
            break;
        case 'settings':
            loadSettingsView();
            break;
        case 'admin':
            loadAdminView();
            break;
    }
}

function openMobileSidebar() {
    const sidebar = document.getElementById('sidebar');
    const overlay = document.getElementById('sidebar-overlay');
    if (sidebar) sidebar.classList.add('open');
    if (overlay) overlay.classList.add('active');
    document.body.style.overflow = 'hidden';
}

function closeMobileSidebar() {
    const sidebar = document.getElementById('sidebar');
    const overlay = document.getElementById('sidebar-overlay');
    if (sidebar) sidebar.classList.remove('open');
    if (overlay) overlay.classList.remove('active');
    document.body.style.overflow = '';
}

function toggleMobileSidebar() {
    const sidebar = document.getElementById('sidebar');
    if (sidebar && sidebar.classList.contains('open')) {
        closeMobileSidebar();
    } else {
        openMobileSidebar();
    }
}

// --- Dashboard View ---
async function loadDashboard() {
    try {
        const data = await apiFetch('/api/dashboard');
        const curr = data.currency || '₹';

        // Period Title
        if (data.monthYearTitle) {
            document.getElementById('top-period').textContent = data.monthYearTitle;
        }

        // Mode B Budget Card
        const budgetCard = document.getElementById('dashboard-budget-card');
        if (data.isBudgetMode && data.budgetSummary) {
            if (budgetCard) budgetCard.style.display = 'block';
            const b = data.budgetSummary;
            const totalBudget = Number(b.totalBudget ?? b.monthlyBudget ?? 0);
            const totalSpent = Number(b.totalSpent ?? data.totalSpent ?? 0);
            const remainingBudget = Number(b.remainingBudget ?? (totalBudget - totalSpent));
            const spentPercentage = Number(b.spentPercentage ?? b.usedPercentage ?? 0);
            const recommendedDaily = Number(b.recommendedDaily ?? b.recommendedDailySpending ?? 0);
            const projectedMonthEnd = Number(b.projectedMonthEnd ?? b.projectedMonthEndSpending ?? totalSpent);
            const alerts = b.alerts || b.activeAlerts || [];

            const totalEl = document.getElementById('dash-budget-total');
            if (totalEl) totalEl.textContent = `${curr}${Math.round(totalBudget).toLocaleString()}`;

            const spentEl = document.getElementById('dash-budget-spent');
            if (spentEl) spentEl.textContent = `${curr}${Math.round(totalSpent).toLocaleString()}`;

            const remEl = document.getElementById('dash-budget-remaining');
            if (remEl) remEl.textContent = `${curr}${Math.round(remainingBudget).toLocaleString()}`;

            const pctEl = document.getElementById('dash-budget-percent');
            if (pctEl) {
                pctEl.textContent = totalBudget > 0 ? `${Math.round(spentPercentage)}% Used` : 'No budget set';
            }

            const fill = document.getElementById('dash-budget-progress-fill');
            if (fill) {
                fill.style.width = `${Math.min(100, Math.max(0, spentPercentage))}%`;
                if (spentPercentage > 100) fill.style.background = '#E88C8C';
                else if (spentPercentage >= 80) fill.style.background = '#E69C24';
                else fill.style.background = 'var(--primary-sage)';
            }

            const recEl = document.getElementById('dash-recommended-daily');
            if (recEl) {
                recEl.textContent = `Recommended Daily: ${curr}${Math.round(recommendedDaily).toLocaleString()}/day`;
            }
            const projEl = document.getElementById('dash-projection-text');
            if (projEl) {
                projEl.textContent = `Projected Month-End: ${curr}${Math.round(projectedMonthEnd).toLocaleString()}`;
            }

            // Render alerts
            const alertsBox = document.getElementById('dash-alerts-box');
            if (alertsBox) {
                alertsBox.innerHTML = '';
                if (alerts && alerts.length > 0) {
                    alerts.forEach(alertText => {
                        const badge = document.createElement('div');
                        badge.className = 'badge badge-coral';
                        badge.style.width = '100%';
                        badge.style.padding = '8px 12px';
                        badge.textContent = `⚠️ ${alertText}`;
                        alertsBox.appendChild(badge);
                    });
                }
            }
        } else {
            if (budgetCard) budgetCard.style.display = 'none';
        }

        // Summary Stats
        const totalSpentEl = document.getElementById('dash-total-spent');
        if (totalSpentEl) totalSpentEl.textContent = `${curr}${Math.round(data.totalSpent || 0).toLocaleString()}`;

        const todaySpentEl = document.getElementById('dash-today-spent');
        if (todaySpentEl) todaySpentEl.textContent = `${curr}${Math.round(data.todaySpent || 0).toLocaleString()}`;

        const dailyAvgEl = document.getElementById('dash-daily-avg');
        if (dailyAvgEl) dailyAvgEl.textContent = `${curr}${Math.round(data.dailyAvg || 0).toLocaleString()}`;

        const daysLeftEl = document.getElementById('dash-days-left');
        const daysLeftVal = (data.daysLeft !== undefined && data.daysLeft !== null) ? data.daysLeft : 0;
        if (daysLeftEl) daysLeftEl.textContent = daysLeftVal;

        // Days left progress fill
        const daysFill = document.getElementById('dash-days-left-fill');
        if (daysFill) {
            const now = new Date();
            const daysInMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
            const daysPassed = Math.max(1, daysInMonth - daysLeftVal);
            const pct = Math.min(100, Math.round((daysPassed / daysInMonth) * 100));
            daysFill.style.width = `${pct}%`;
        }

        // Categories List (Rendered as attractive squircle cards matching reference image)
        const catContainer = document.getElementById('dash-categories-container');
        catContainer.innerHTML = '';
        if (data.categoriesBreakdown && data.categoriesBreakdown.length > 0) {
            catContainer.className = 'dash-cat-grid';
            data.categoriesBreakdown.forEach(item => {
                const cat = item.category;
                const meta = getCategoryMeta(cat.name);
                const card = document.createElement('div');
                card.className = 'dash-cat-card';
                card.onclick = () => {
                    navigate('transactions');
                    const txFilter = document.getElementById('tx-filter-cat');
                    if (txFilter) {
                        txFilter.value = cat.id;
                        loadTransactions();
                    }
                };
                card.innerHTML = `
                    <div style="display: flex; align-items: center; gap: 8px;">
                        <span style="width: 8px; height: 8px; border-radius: 50%; background: ${meta.bar}; display: inline-block;"></span>
                        <div class="dash-cat-name">${escapeHtml(cat.name)}</div>
                    </div>
                    <div class="dash-cat-amount">${curr}${Math.round(item.amount).toLocaleString()}</div>
                    <div class="dash-cat-bar-wrap">
                        <div class="dash-cat-bar">
                            <div class="dash-cat-bar-fill" style="width: ${Math.min(100, Math.round(item.percentage))}%; background: ${meta.bar};"></div>
                        </div>
                        <span class="dash-cat-pct">${Math.round(item.percentage)}%</span>
                    </div>
                `;
                catContainer.appendChild(card);
            });
        } else {
            catContainer.className = '';
            catContainer.innerHTML = '<div class="text-muted" style="text-align: center; padding: 24px;">No expenses recorded this month. Tap "+ Add Expense" to begin.</div>';
        }

        // Daily Spending Trend Chart
        renderDashTrendChart(data.dailySpending);

        // Recent Transactions List
        const recentList = document.getElementById('dash-recent-list');
        recentList.innerHTML = '';
        if (data.recentTransactions && data.recentTransactions.length > 0) {
            data.recentTransactions.forEach(t => {
                const catName = t.categoryName || (t.category ? t.category.name : 'Other');
                const meta = getCategoryMeta(catName);
                const item = document.createElement('div');
                item.className = 'card';
                item.style.padding = '12px 16px';
                item.style.display = 'flex';
                item.style.justifyContent = 'space-between';
                item.style.alignItems = 'center';
                item.innerHTML = `
                    <div>
                        <div style="font-weight: 700; font-size: 0.92rem; color: var(--text-charcoal);">${escapeHtml(t.description || catName)}</div>
                        <div class="text-muted" style="font-size: 0.75rem;">${t.expenseDate} • <span class="badge" style="background: ${meta.bg}; color: ${meta.text}; font-size: 0.68rem; padding: 2px 7px;">${catName}</span> • <span class="badge badge-sage" style="font-size: 0.68rem; padding: 2px 7px;">${t.paymentMode}</span></div>
                    </div>
                    <div style="font-weight: 800; font-size: 1.05rem; color: #1A2E26;">
                        -${curr}${Number(t.amount || 0).toLocaleString()}
                    </div>
                `;
                recentList.appendChild(item);
            });
        } else {
            recentList.innerHTML = '<div class="text-muted" style="text-align: center; padding: 18px;">No transactions recorded yet. Tap "+ Add Expense" to begin.</div>';
        }

    } catch (e) {
        console.error('Error loading dashboard:', e);
    }
}

function renderDashTrendChart(dailyData) {
    const ctx = document.getElementById('dashTrendChart');
    if (!ctx || typeof Chart === 'undefined') return;

    const days = [];
    const values = [];
    const now = new Date();
    const daysInMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();

    for (let d = 1; d <= daysInMonth; d++) {
        days.push(`Day ${d}`);
        const val = dailyData ? (dailyData[d] ?? dailyData[String(d)] ?? 0) : 0;
        values.push(val);
    }

    if (dashTrendChartInstance) {
        dashTrendChartInstance.destroy();
    }

    dashTrendChartInstance = new Chart(ctx, {
        type: 'line',
        data: {
            labels: days,
            datasets: [{
                label: 'Daily Spent (₹)',
                data: values,
                borderColor: '#1B6E4A',
                backgroundColor: 'rgba(27, 110, 74, 0.12)',
                fill: true,
                tension: 0.35,
                pointRadius: 3,
                pointBackgroundColor: '#1B6E4A',
                pointHoverRadius: 6,
                pointHoverBackgroundColor: '#0D382B',
                borderWidth: 2.5
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        label: (c) => ` ₹${c.parsed.y.toLocaleString()}`
                    }
                }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    grid: { color: '#E6EBE8' }
                },
                x: {
                    grid: { display: false },
                    ticks: { maxTicksLimit: 10 }
                }
            }
        }
    });
}

// --- Categories Dropdowns Populate ---
let cachedCategories = [];
async function populateCategoryDropdowns() {
    try {
        const cats = await apiFetch('/api/categories');
        cachedCategories = cats || [];

        // Filter dropdown in Transactions
        const txFilter = document.getElementById('tx-filter-cat');
        if (txFilter) {
            txFilter.innerHTML = '<option value="">All Categories</option>';
            cachedCategories.forEach(c => {
                txFilter.innerHTML += `<option value="${c.id}">${c.name}</option>`;
            });
        }

        // Expense Modal dropdown
        const modalCat = document.getElementById('modal-expense-category');
        if (modalCat) {
            modalCat.innerHTML = '';
            cachedCategories.forEach(c => {
                modalCat.innerHTML += `<option value="${c.id}">${c.name}</option>`;
            });
        }

        // Recurring dropdown
        const recCat = document.getElementById('rec-category');
        if (recCat) {
            recCat.innerHTML = '';
            cachedCategories.forEach(c => {
                recCat.innerHTML += `<option value="${c.id}">${c.name}</option>`;
            });
        }
    } catch (e) {
        console.error('Error populating categories:', e);
    }
}

// --- Transactions History View ---
async function loadTransactions() {
    try {
        const search = document.getElementById('tx-search').value.trim();
        const catId = document.getElementById('tx-filter-cat').value;
        const pay = document.getElementById('tx-filter-pay').value;
        const sort = document.getElementById('tx-filter-sort').value;

        let url = `/api/expenses?sortBy=${encodeURIComponent(sort)}`;
        if (search) url += `&search=${encodeURIComponent(search)}`;
        if (catId) url += `&categoryId=${encodeURIComponent(catId)}`;
        if (pay) url += `&paymentMode=${encodeURIComponent(pay)}`;

        const data = await apiFetch(url);
        const curr = (currentUser && currentUser.currency) || '₹';

        document.getElementById('tx-count-badge').textContent = `${data.count} items`;
        document.getElementById('tx-total-badge').textContent = `Total: ${curr}${data.total.toLocaleString()}`;

        const tbody = document.getElementById('tx-table-body');
        tbody.innerHTML = '';

        if (data.expenses && data.expenses.length > 0) {
            data.expenses.forEach(e => {
                const catName = e.categoryName || (e.category ? e.category.name : 'Other');
                const meta = getCategoryMeta(catName);
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td data-label="Date" style="font-weight: 600;">${e.expenseDate}</td>
                    <td data-label="Category">
                        <span class="badge" style="background: ${meta.bg}; color: ${meta.text}; font-weight: 700; font-size: 0.82rem; padding: 4px 10px; border-radius: 8px;">
                            ${escapeHtml(catName)}
                        </span>
                    </td>
                    <td data-label="Description">${escapeHtml(e.description || catName)}</td>
                    <td data-label="Payment Mode"><span class="badge badge-sage">${e.paymentMode}</span></td>
                    <td data-label="Amount" style="font-weight: 800; color: #1A2E26;">-${curr}${Number(e.amount || 0).toLocaleString()}</td>
                    <td data-label="Actions" style="text-align: right;">
                        <button class="btn btn-secondary btn-small" onclick='editExpense(${JSON.stringify(e)})' title="Edit">Edit</button>
                        <button class="btn btn-danger btn-small" onclick="deleteExpense(${e.id})" title="Delete" style="margin-left: 4px;">Delete</button>
                    </td>
                `;
                tbody.appendChild(tr);
            });
        } else {
            tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--text-muted); padding: 24px;">No matching transactions found.</td></tr>`;
        }
    } catch (e) {
        console.error('Error loading transactions:', e);
    }
}

// --- Add / Edit Expense Modal ---
async function openExpenseModal(expenseToEdit = null) {
    const modal = document.getElementById('expense-modal');
    const title = document.getElementById('modal-expense-title');
    const btnSubmit = document.getElementById('modal-btn-submit');
    const idInput = document.getElementById('modal-expense-id');
    const amtInput = document.getElementById('modal-expense-amount');
    const catSelect = document.getElementById('modal-expense-category');
    const paySelect = document.getElementById('modal-expense-payment');
    const dateInput = document.getElementById('modal-expense-date');
    const descInput = document.getElementById('modal-expense-desc');

    if (!cachedCategories || cachedCategories.length === 0) {
        await populateCategoryDropdowns();
    }

    // Ensure options exist
    if (catSelect && catSelect.options.length === 0 && cachedCategories.length > 0) {
        catSelect.innerHTML = '';
        cachedCategories.forEach(c => {
            catSelect.innerHTML += `<option value="${c.id}">${c.name}</option>`;
        });
    }

    if (expenseToEdit) {
        title.textContent = 'Edit Expense';
        btnSubmit.textContent = 'Update Expense';
        idInput.value = expenseToEdit.id;
        amtInput.value = expenseToEdit.amount;
        catSelect.value = expenseToEdit.categoryId;
        paySelect.value = expenseToEdit.paymentMode;
        dateInput.value = expenseToEdit.expenseDate;
        descInput.value = expenseToEdit.description || '';
    } else {
        title.textContent = 'Record Expense';
        btnSubmit.textContent = 'Save Expense';
        idInput.value = '';
        amtInput.value = '';
        dateInput.value = getTodayLocalDate();
        descInput.value = '';
        if (cachedCategories.length > 0 && catSelect) {
            catSelect.value = cachedCategories[0].id;
        }
        if (paySelect) paySelect.value = 'UPI';
        const curCatText = (catSelect && catSelect.selectedIndex >= 0 && catSelect.options[catSelect.selectedIndex])
            ? catSelect.options[catSelect.selectedIndex].text.trim() : '';
        descInput.placeholder = curCatText ? `Optional (defaults to ${curCatText})` : 'Optional (defaults to category)';
    }

    if (catSelect && !catSelect.dataset.listenerBound) {
        catSelect.dataset.listenerBound = 'true';
        catSelect.addEventListener('change', () => {
            const curText = (catSelect.selectedIndex >= 0 && catSelect.options[catSelect.selectedIndex])
                ? catSelect.options[catSelect.selectedIndex].text.trim() : '';
            if (descInput && !descInput.value) {
                descInput.placeholder = curText ? `Optional (defaults to ${curText})` : 'Optional (defaults to category)';
            }
        });
    }

    modal.classList.add('open');
    modal.style.display = 'flex';
    if (window.innerWidth <= 900) {
        document.body.style.overflow = 'hidden';
    }
    setTimeout(() => {
        if (amtInput) amtInput.focus();
    }, 60);
}

function closeExpenseModal() {
    const modal = document.getElementById('expense-modal');
    if (modal) {
        modal.classList.remove('open');
        modal.style.display = 'none';
        document.body.style.overflow = '';
    }
}

async function handleSaveExpense(e) {
    e.preventDefault();
    const id = document.getElementById('modal-expense-id').value;
    const amount = parseFloat(document.getElementById('modal-expense-amount').value);
    const catSelect = document.getElementById('modal-expense-category');
    const catVal = catSelect ? catSelect.value : '';
    const categoryId = parseInt(catVal);
    const selectedCategoryName = (catSelect && catSelect.selectedIndex >= 0 && catSelect.options[catSelect.selectedIndex])
        ? catSelect.options[catSelect.selectedIndex].text.trim()
        : '';
    const paymentMode = document.getElementById('modal-expense-payment').value;
    const expenseDate = document.getElementById('modal-expense-date').value;
    let description = document.getElementById('modal-expense-desc').value.trim();

    // If description is empty or 'Other', default to the category name
    if (!description || description.toLowerCase() === 'other') {
        description = selectedCategoryName || 'Expense';
    }

    if (isNaN(amount) || amount <= 0) {
        showToast('Please enter a valid amount greater than ₹0.', 'error');
        return;
    }
    if (!catVal || isNaN(categoryId) || categoryId <= 0) {
        showToast('Please select a valid category from the list.', 'error');
        return;
    }
    if (!expenseDate) {
        showToast('Please select an expense date.', 'error');
        return;
    }

    const payload = { amount, categoryId, paymentMode, expenseDate, description };

    try {
        if (id) {
            payload.id = parseInt(id);
            await apiFetch('/api/expenses', {
                method: 'PUT',
                body: payload
            });
            showToast('Expense updated successfully!', 'success');
        } else {
            await apiFetch('/api/expenses', {
                method: 'POST',
                body: payload
            });
            showToast(`Expense of ₹${amount.toFixed(2)} recorded successfully! 🎉`, 'success');
        }

        closeExpenseModal();
        switch (currentView) {
            case 'dashboard': loadDashboard(); break;
            case 'transactions': loadTransactions(); break;
            case 'calendar': loadCalendar(); break;
            case 'budget': loadBudgetView(); break;
            case 'analytics': loadAnalytics(); break;
            default: loadDashboard(); break;
        }
    } catch (err) {
        showToast('Error saving expense: ' + err.message, 'error');
    }
}

function editExpense(exp) {
    openExpenseModal(exp);
}

async function deleteExpense(id) {
    if (!confirm('Are you sure you want to delete this expense record?')) return;
    try {
        await apiFetch(`/api/expenses?id=${id}`, { method: 'DELETE' });
        showToast('Expense deleted successfully.', 'info');
        switch (currentView) {
            case 'dashboard': loadDashboard(); break;
            case 'transactions': loadTransactions(); break;
            case 'calendar': loadCalendar(); break;
            case 'budget': loadBudgetView(); break;
            case 'analytics': loadAnalytics(); break;
            default: loadDashboard(); break;
        }
    } catch (err) {
        showToast('Failed to delete expense: ' + err.message, 'error');
    }
}

// --- Interactive Calendar View ---
function prevCalendarMonth() {
    currentCalMonth--;
    if (currentCalMonth < 1) {
        currentCalMonth = 12;
        currentCalYear--;
    }
    loadCalendar();
}

function nextCalendarMonth() {
    currentCalMonth++;
    if (currentCalMonth > 12) {
        currentCalMonth = 1;
        currentCalYear++;
    }
    loadCalendar();
}

async function loadCalendar(selectedDay = null) {
    try {
        let url = `/api/calendar?month=${currentCalMonth}&year=${currentCalYear}`;
        if (selectedDay) {
            url += `&selectedDate=${selectedDay}`;
        }
        const data = await apiFetch(url);
        const curr = (currentUser && currentUser.currency) || '₹';

        document.getElementById('calendar-month-title').textContent = data.monthYearTitle;

        const grid = document.getElementById('calendar-grid');
        grid.innerHTML = '';

        const daysOfWeek = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
        daysOfWeek.forEach(d => {
            const h = document.createElement('div');
            h.style.fontWeight = '700';
            h.style.fontSize = '0.75rem';
            h.style.color = 'var(--text-muted)';
            h.style.padding = '6px 0';
            h.textContent = d;
            grid.appendChild(h);
        });

        const firstDayIdx = new Date(currentCalYear, currentCalMonth - 1, 1).getDay();
        const daysInMonth = new Date(currentCalYear, currentCalMonth, 0).getDate();

        // Empty lead cells
        for (let i = 0; i < firstDayIdx; i++) {
            const empty = document.createElement('div');
            grid.appendChild(empty);
        }

        const today = new Date();
        const isCurrentMonth = (today.getMonth() + 1 === currentCalMonth && today.getFullYear() === currentCalYear);

        for (let day = 1; day <= daysInMonth; day++) {
            const dateStr = `${currentCalYear}-${String(currentCalMonth).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
            const daySpent = data.dailySpending && data.dailySpending[day] ? data.dailySpending[day] : 0;
            const isToday = isCurrentMonth && today.getDate() === day;

            const cell = document.createElement('div');
            cell.className = 'card';
            cell.style.padding = '8px 4px';
            cell.style.cursor = 'pointer';
            cell.style.minHeight = '64px';
            cell.style.display = 'flex';
            cell.style.flexDirection = 'column';
            cell.style.justifyContent = 'space-between';
            cell.style.transition = 'all 0.15s ease';
            if (isToday) cell.style.border = '2px solid #78BFA0';

            cell.onclick = () => selectCalendarDay(dateStr, day, daySpent);

            cell.innerHTML = `
                <div style="font-weight: 700; font-size: 0.85rem; ${isToday ? 'color: #2D6B50;' : ''}">${day}</div>
                ${daySpent > 0 ? `<div class="badge badge-peach" style="font-size: 0.65rem; padding: 2px 4px; overflow: hidden; text-overflow: ellipsis;">${curr}${Math.round(daySpent)}</div>` : '<div style="height: 14px;"></div>'}
            `;
            grid.appendChild(cell);
        }

        // If a date was requested or selectedDay is passed, update drawer
        if (data.selectedDateExpenses) {
            renderCalendarDayDrawer(selectedDay, data.selectedDateExpenses);
        } else if (!selectedDay) {
            // Select today if in current month, else 1st
            const defaultDay = isCurrentMonth ? today.getDate() : 1;
            const defaultDateStr = `${currentCalYear}-${String(currentCalMonth).padStart(2, '0')}-${String(defaultDay).padStart(2, '0')}`;
            selectCalendarDay(defaultDateStr, defaultDay, data.dailySpending ? (data.dailySpending[defaultDay] || 0) : 0);
        }

    } catch (e) {
        console.error('Error loading calendar:', e);
    }
}

async function selectCalendarDay(dateStr, dayNum, totalSpent) {
    document.getElementById('cal-selected-day-title').textContent = `Transactions on ${dateStr}`;
    const curr = (currentUser && currentUser.currency) || '₹';
    document.getElementById('cal-selected-day-summary').textContent = `Total: ${curr}${Math.round(totalSpent).toLocaleString()}`;

    try {
        const resp = await apiFetch(`/api/calendar?month=${currentCalMonth}&year=${currentCalYear}&selectedDate=${dateStr}`);
        renderCalendarDayDrawer(dateStr, resp.selectedDateExpenses || []);
    } catch (e) {
        console.error('Error fetching date transactions:', e);
    }
}

function renderCalendarDayDrawer(dateStr, expenses) {
    const list = document.getElementById('cal-selected-day-list');
    list.innerHTML = '';
    const curr = (currentUser && currentUser.currency) || '₹';

    if (expenses && expenses.length > 0) {
        expenses.forEach(e => {
            const catName = e.categoryName || (e.category ? e.category.name : 'Other');
            const meta = getCategoryMeta(catName);
            const item = document.createElement('div');
            item.className = 'card';
            item.style.padding = '10px 14px';
            item.style.display = 'flex';
            item.style.justifyContent = 'space-between';
            item.style.alignItems = 'center';
            item.innerHTML = `
                <div>
                    <div style="font-weight: 700; font-size: 0.88rem; color: var(--text-charcoal);">${escapeHtml(e.description || catName)}</div>
                    <div class="text-muted" style="font-size: 0.72rem;"><span class="badge" style="background: ${meta.bg}; color: ${meta.text}; font-size: 0.65rem; padding: 2px 6px;">${catName}</span> • ${e.paymentMode}</div>
                </div>
                <div style="font-weight: 800; font-size: 0.95rem; color: #1A2E26;">
                    -${curr}${e.amount.toLocaleString()}
                </div>
            `;
            list.appendChild(item);
        });
    } else {
        list.innerHTML = '<p class="text-muted" style="text-align: center; padding: 24px;">No expenses recorded on this day.</p>';
    }
}

// --- Analytics View ---
function initAnalyticsPickers() {
    const mSelect = document.getElementById('analytics-month');
    const ySelect = document.getElementById('analytics-year');
    if (!mSelect || !ySelect) return;

    mSelect.innerHTML = '';
    const monthNames = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
    monthNames.forEach((m, idx) => {
        const opt = document.createElement('option');
        opt.value = idx + 1;
        opt.textContent = m;
        if (idx + 1 === currentAnalyticsMonth) opt.selected = true;
        mSelect.appendChild(opt);
    });

    ySelect.innerHTML = '';
    const nowY = new Date().getFullYear();
    for (let y = nowY - 3; y <= nowY + 1; y++) {
        const opt = document.createElement('option');
        opt.value = y;
        opt.textContent = y;
        if (y === currentAnalyticsYear) opt.selected = true;
        ySelect.appendChild(opt);
    }
}

async function loadAnalytics() {
    const mSelect = document.getElementById('analytics-month');
    const ySelect = document.getElementById('analytics-year');
    const month = mSelect ? parseInt(mSelect.value) : currentAnalyticsMonth;
    const year = ySelect ? parseInt(ySelect.value) : currentAnalyticsYear;

    try {
        const data = await apiFetch(`/api/analytics?month=${month}&year=${year}`);
        const curr = (currentUser && currentUser.currency) || '₹';

        // Category Doughnut Chart
        renderAnalyticsCategoryChart(data.categoryAnalysis || []);

        // Payment Mode Bar Chart
        renderAnalyticsPaymentChart(data.paymentAnalysis || {});

        // Month-over-Month Comparison
        if (data.monthComparison) {
            const c = data.monthComparison;
            document.getElementById('cmp-prev-title').textContent = `${c.previousMonthName} ${c.previousYear}`;
            document.getElementById('cmp-prev-amt').textContent = `${curr}${Math.round(c.previousSpent).toLocaleString()}`;
            document.getElementById('cmp-curr-title').textContent = `${c.currentMonthName} ${c.currentYear}`;
            document.getElementById('cmp-curr-amt').textContent = `${curr}${Math.round(c.currentSpent).toLocaleString()}`;

            const diffSign = c.difference > 0 ? '+' : '';
            document.getElementById('cmp-diff-amt').textContent = `${diffSign}${curr}${Math.round(c.difference).toLocaleString()} (${c.percentageChange > 0 ? '+' : ''}${Math.round(c.percentageChange)}%)`;

            const banner = document.getElementById('cmp-summary-banner');
            banner.textContent = c.summaryInsight;
            if (c.difference > 0) {
                banner.className = 'badge badge-peach';
            } else {
                banner.className = 'badge badge-sage';
            }
        }

        // Yearly 12-Month Bar Chart
        if (data.yearlyAnalysis) {
            renderAnalyticsYearlyChart(data.yearlyAnalysis.monthlyTotals || {});
        }

    } catch (e) {
        console.error('Error loading analytics:', e);
    }
}

function renderAnalyticsCategoryChart(catData) {
    const ctx = document.getElementById('analyticsCategoryChart');
    if (!ctx) return;

    if (analyticsCatChartInstance) analyticsCatChartInstance.destroy();

    const labels = catData.map(c => c.categoryName);
    const amounts = catData.map(c => c.amount);
    const colors = catData.map(c => {
        const meta = getCategoryMeta(c.categoryName);
        return meta.bar || c.color || '#1B6E4A';
    });

    if (catData.length === 0) {
        labels.push('No Expenses');
        amounts.push(1);
        colors.push('#E2E8F0');
    }

    analyticsCatChartInstance = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: labels,
            datasets: [{
                data: amounts,
                backgroundColor: colors,
                borderWidth: 2,
                borderColor: '#ffffff'
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { position: 'right' }
            }
        }
    });
}

function renderAnalyticsPaymentChart(payData) {
    const ctx = document.getElementById('analyticsPaymentChart');
    if (!ctx) return;

    if (analyticsPayChartInstance) analyticsPayChartInstance.destroy();

    const labels = Object.keys(payData);
    const amounts = Object.values(payData);

    analyticsPayChartInstance = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [{
                label: 'Spent (₹)',
                data: amounts,
                backgroundColor: '#A9CFE0',
                borderRadius: 6
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: {
                y: { beginAtZero: true }
            }
        }
    });
}

function renderAnalyticsYearlyChart(monthlyTotals) {
    const ctx = document.getElementById('analyticsYearlyChart');
    if (!ctx) return;

    if (analyticsYearChartInstance) analyticsYearChartInstance.destroy();

    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    const values = [];
    for (let m = 1; m <= 12; m++) {
        values.push(monthlyTotals[m] || 0);
    }

    analyticsYearChartInstance = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: months,
            datasets: [{
                label: 'Monthly Total (₹)',
                data: values,
                backgroundColor: '#C9B9E8',
                borderRadius: 6
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: {
                y: { beginAtZero: true }
            }
        }
    });
}

// --- Budget Management View ---
let currentBudgetData = null;
async function loadBudgetView() {
    try {
        const now = new Date();
        const data = await apiFetch(`/api/budget?month=${now.getMonth() + 1}&year=${now.getFullYear()}`);
        currentBudgetData = data;
        const curr = (currentUser && currentUser.currency) || '₹';

        const s = data.summary || {};
        const totalBudget = Number(s.totalBudget ?? s.monthlyBudget ?? 0);
        const totalSpent = Number(s.totalSpent ?? 0);
        const remainingBudget = Number(s.remainingBudget ?? (totalBudget - totalSpent));
        const recommendedDaily = Number(s.recommendedDaily ?? s.recommendedDailySpending ?? 0);
        const projectedMonthEnd = Number(s.projectedMonthEnd ?? s.projectedMonthEndSpending ?? totalSpent);

        const cardBudget = document.getElementById('bgt-card-budget');
        if (cardBudget) cardBudget.textContent = `${curr}${Math.round(totalBudget).toLocaleString()}`;
        const cardSpent = document.getElementById('bgt-card-spent');
        if (cardSpent) cardSpent.textContent = `${curr}${Math.round(totalSpent).toLocaleString()}`;
        const cardRem = document.getElementById('bgt-card-remaining');
        if (cardRem) cardRem.textContent = `${curr}${Math.round(remainingBudget).toLocaleString()}`;
        const cardDaily = document.getElementById('bgt-card-daily');
        if (cardDaily) cardDaily.textContent = `${curr}${Math.round(recommendedDaily).toLocaleString()}`;

        const inputTotal = document.getElementById('budget-input-total');
        if (inputTotal) inputTotal.value = totalBudget || '';

        // Projection insight banner
        const projEl = document.getElementById('bgt-projection-insight');
        if (projEl) {
            if (projectedMonthEnd > totalBudget && totalBudget > 0) {
                projEl.innerHTML = `⚠️ At your current spending pace, you will reach <strong style="color: #E88C8C;">${curr}${Math.round(projectedMonthEnd).toLocaleString()}</strong> by month-end, exceeding your budget by <strong>${curr}${Math.round(projectedMonthEnd - totalBudget).toLocaleString()}</strong>!`;
            } else {
                projEl.innerHTML = `✅ At your current spending pace, your estimated month-end spend is <strong style="color: #2D6B50;">${curr}${Math.round(projectedMonthEnd).toLocaleString()}</strong>, keeping you safely under budget!`;
            }
        }

        // Render Category Limits
        const catList = document.getElementById('budget-categories-list');
        catList.innerHTML = '';
        if (cachedCategories.length === 0) await populateCategoryDropdowns();

        const cbMap = {};
        if (data.budget && data.budget.categoryBudgets) {
            data.budget.categoryBudgets.forEach(cb => {
                cbMap[cb.categoryId] = cb.amount;
            });
        }

        cachedCategories.forEach(cat => {
            const limit = cbMap[cat.id] || 0;
            const meta = getCategoryMeta(cat.name);
            const row = document.createElement('div');
            row.className = 'card';
            row.style.padding = '12px 16px';
            row.innerHTML = `
                <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px;">
                    <div style="display: flex; align-items: center; gap: 8px;">
                        <span style="width: 10px; height: 10px; border-radius: 50%; background: ${meta.bar}; display: inline-block;"></span>
                        <strong style="font-size: 0.95rem; color: var(--text-charcoal);">${cat.name}</strong>
                    </div>
                    <div style="display: flex; align-items: center; gap: 8px;">
                        <span class="text-muted" style="font-size: 0.8rem;">Limit (${curr}):</span>
                        <input type="number" step="10" class="form-input bgt-cat-limit-input" data-cat-id="${cat.id}" value="${limit}" style="width: 120px; padding: 6px 8px;">
                    </div>
                </div>
            `;
            catList.appendChild(row);
        });

    } catch (e) {
        console.error('Error loading budget view:', e);
    }
}

async function saveOverallBudget() {
    const total = parseFloat(document.getElementById('budget-input-total').value) || 0;
    const now = new Date();
    try {
        await apiFetch('/api/budget', {
            method: 'POST',
            body: {
                month: now.getMonth() + 1,
                year: now.getFullYear(),
                totalBudget: total
            }
        });
        showToast('Monthly budget saved successfully! 🎯', 'success');
        loadBudgetView();
    } catch (err) {
        showToast('Failed to save budget: ' + err.message, 'error');
    }
}

async function saveCategoryLimits() {
    const now = new Date();
    const total = parseFloat(document.getElementById('budget-input-total').value) || 0;
    const inputs = document.querySelectorAll('.bgt-cat-limit-input');
    const categoryBudgets = [];

    inputs.forEach(inp => {
        const catId = parseInt(inp.getAttribute('data-cat-id'));
        const amt = parseFloat(inp.value) || 0;
        if (amt > 0) {
            categoryBudgets.push({ categoryId: catId, amount: amt });
        }
    });

    try {
        await apiFetch('/api/budget', {
            method: 'POST',
            body: {
                month: now.getMonth() + 1,
                year: now.getFullYear(),
                totalBudget: total,
                categoryBudgets: categoryBudgets
            }
        });
        showToast('Category limits saved successfully! 🎯', 'success');
        loadBudgetView();
    } catch (err) {
        showToast('Failed to save limits: ' + err.message, 'error');
    }
}

// --- Income & Balance View ---
async function loadIncomeView() {
    const now = new Date();
    try {
        const data = await apiFetch(`/api/income?month=${now.getMonth() + 1}&year=${now.getFullYear()}`);
        const curr = (currentUser && currentUser.currency) || '₹';

        document.getElementById('inc-total-val').textContent = `${curr}${Math.round(data.totalIncome).toLocaleString()}`;
        document.getElementById('inc-expense-val').textContent = `${curr}${Math.round(data.totalExpenses).toLocaleString()}`;

        const balEl = document.getElementById('inc-balance-val');
        balEl.textContent = `${curr}${Math.round(data.netBalance).toLocaleString()}`;
        if (data.netBalance < 0) balEl.style.color = '#E88C8C';
        else balEl.style.color = '#2D6B50';

        const list = document.getElementById('income-records-list');
        list.innerHTML = '';
        if (data.incomeList && data.incomeList.length > 0) {
            data.incomeList.forEach(inc => {
                const item = document.createElement('div');
                item.className = 'card';
                item.style.padding = '10px 14px';
                item.style.display = 'flex';
                item.style.justifyContent = 'space-between';
                item.style.alignItems = 'center';
                item.innerHTML = `
                    <div>
                        <div style="font-weight: 700; font-size: 0.9rem;">${escapeHtml(inc.source)}</div>
                        <div class="text-muted" style="font-size: 0.75rem;">${inc.incomeDate} • ${escapeHtml(inc.description || '')}</div>
                    </div>
                    <div style="display: flex; align-items: center; gap: 10px;">
                        <span style="font-weight: 800; font-size: 1rem; color: #2D6B50;">+${curr}${inc.amount.toLocaleString()}</span>
                        <button class="btn btn-danger btn-small" onclick="deleteIncome(${inc.id})">🗑️</button>
                    </div>
                `;
                list.appendChild(item);
            });
        } else {
            list.innerHTML = '<div class="text-muted" style="text-align: center; padding: 24px;">No income recorded this month.</div>';
        }

        // Set default date
        document.getElementById('inc-date').value = getTodayLocalDate();

    } catch (e) {
        console.error('Error loading income:', e);
    }
}

async function handleAddIncomeSubmit(e) {
    e.preventDefault();
    const amount = parseFloat(document.getElementById('inc-amount').value);
    const source = document.getElementById('inc-source').value;
    const incomeDate = document.getElementById('inc-date').value;
    const description = document.getElementById('inc-desc').value.trim();

    try {
        await apiFetch('/api/income', {
            method: 'POST',
            body: { amount, source, incomeDate, description }
        });
        document.getElementById('inc-amount').value = '';
        document.getElementById('inc-desc').value = '';
        loadIncomeView();
    } catch (err) {
        alert('Failed to add income: ' + err.message);
    }
}

async function deleteIncome(id) {
    if (!confirm('Are you sure you want to delete this income entry?')) return;
    try {
        await apiFetch(`/api/income?id=${id}`, { method: 'DELETE' });
        loadIncomeView();
    } catch (err) {
        alert('Failed to delete income: ' + err.message);
    }
}

// --- Recurring Subscriptions View ---
async function loadRecurringView() {
    try {
        const list = await apiFetch('/api/recurring');
        const curr = (currentUser && currentUser.currency) || '₹';

        const container = document.getElementById('recurring-list');
        container.innerHTML = '';

        if (list && list.length > 0) {
            list.forEach(r => {
                const item = document.createElement('div');
                item.className = 'card';
                item.style.padding = '12px 16px';
                item.style.display = 'flex';
                item.style.justifyContent = 'space-between';
                item.style.alignItems = 'center';
                item.innerHTML = `
                    <div>
                        <div style="font-weight: 700; font-size: 0.95rem;">${escapeHtml(r.title)}</div>
                        <div class="text-muted" style="font-size: 0.75rem;">
                            Next Due: <strong>${r.nextDueDate}</strong> • Frequency: <span class="badge badge-blue" style="font-size: 0.65rem;">${r.frequency}</span>
                        </div>
                    </div>
                    <div style="display: flex; align-items: center; gap: 8px;">
                        <span style="font-weight: 800; font-size: 1.05rem;">${curr}${r.amount.toLocaleString()}</span>
                        <button class="btn btn-primary btn-small" onclick="logRecurringNow(${r.id})" title="Log expense today">⚡ Log Now</button>
                        <button class="btn btn-danger btn-small" onclick="deleteRecurring(${r.id})" title="Cancel subscription">🗑️</button>
                    </div>
                `;
                container.appendChild(item);
            });
        } else {
            container.innerHTML = '<div class="text-muted" style="text-align: center; padding: 24px;">No recurring bills added yet.</div>';
        }

        document.getElementById('rec-date').value = getTodayLocalDate();

    } catch (e) {
        console.error('Error loading recurring:', e);
    }
}

async function handleAddRecurringSubmit(e) {
    e.preventDefault();
    const title = document.getElementById('rec-title').value.trim();
    const amount = parseFloat(document.getElementById('rec-amount').value);
    const categoryId = parseInt(document.getElementById('rec-category').value);
    const frequency = document.getElementById('rec-frequency').value;
    const paymentMode = document.getElementById('rec-payment').value;
    const nextDueDate = document.getElementById('rec-date').value;

    try {
        await apiFetch('/api/recurring', {
            method: 'POST',
            body: { title, amount, categoryId, frequency, paymentMode, nextDueDate }
        });
        document.getElementById('rec-title').value = '';
        document.getElementById('rec-amount').value = '';
        loadRecurringView();
    } catch (err) {
        alert('Failed to save recurring bill: ' + err.message);
    }
}

async function logRecurringNow(id) {
    try {
        const resp = await apiFetch('/api/recurring', {
            method: 'POST',
            body: { action: 'logNow', id }
        });
        alert('Expense recorded successfully for today!');
        loadRecurringView();
    } catch (err) {
        alert('Failed to log recurring expense: ' + err.message);
    }
}

async function deleteRecurring(id) {
    if (!confirm('Are you sure you want to cancel this recurring bill?')) return;
    try {
        await apiFetch(`/api/recurring?id=${id}`, { method: 'DELETE' });
        loadRecurringView();
    } catch (err) {
        alert('Failed to delete recurring bill: ' + err.message);
    }
}

// --- Reports & Exports View ---
async function loadReportsView() {
    const now = new Date();
    try {
        const data = await apiFetch(`/api/analytics?month=${now.getMonth() + 1}&year=${now.getFullYear()}`);
        const curr = (currentUser && currentUser.currency) || '₹';

        const monthNames = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
        document.getElementById('rep-period-title').textContent = `${monthNames[now.getMonth()]} ${now.getFullYear()}`;
        document.getElementById('rep-total-exp').textContent = `${curr}${Math.round(data.totalSpent).toLocaleString()}`;
        document.getElementById('rep-daily-avg').textContent = `${curr}${data.dailyAverage.toFixed(0)} / day`;

        if (data.categoryAnalysis && data.categoryAnalysis.length > 0) {
            document.getElementById('rep-top-cat').textContent = `${data.categoryAnalysis[0].categoryName} (${curr}${Math.round(data.categoryAnalysis[0].amount).toLocaleString()})`;
        }

        if (data.paymentAnalysis) {
            const keys = Object.keys(data.paymentAnalysis);
            if (keys.length > 0) {
                document.getElementById('rep-top-pay').textContent = keys[0];
            }
        }

        if (data.monthComparison) {
            document.getElementById('rep-prev-spent').textContent = `${curr}${Math.round(data.monthComparison.previousSpent).toLocaleString()}`;
        }
    } catch (e) {
        console.error('Error loading reports preview:', e);
    }
}

function downloadReportPdf() {
    const now = new Date();
    window.open(`/api/reports/pdf?month=${now.getMonth() + 1}&year=${now.getFullYear()}`, '_blank');
}

function downloadReportCsv() {
    const now = new Date();
    window.open(`/api/reports/csv?month=${now.getMonth() + 1}&year=${now.getFullYear()}`, '_blank');
}

// --- Settings View ---
async function loadSettingsView() {
    if (!currentUser) return;
    document.getElementById('settings-name').value = currentUser.fullName || '';
    document.getElementById('settings-currency').value = currentUser.currency || '₹';

    const isBudget = currentUser.financialMode === 'TRACK_AND_BUDGET';
    const trackCard = document.getElementById('settings-mode-track');
    const bgtCard = document.getElementById('settings-mode-budget');
    if (isBudget) {
        bgtCard.style.border = '2px solid #5FA787';
        trackCard.style.border = '1px solid var(--border-color)';
    } else {
        trackCard.style.border = '2px solid #78BFA0';
        bgtCard.style.border = '1px solid var(--border-color)';
    }

    renderSettingsCategories();
}

async function setSettingMode(newMode) {
    try {
        const resp = await apiFetch('/api/settings', {
            method: 'POST',
            body: { action: 'switchMode', mode: newMode }
        });
        if (resp && resp.user) {
            currentUser = resp.user;
            updateUserHeaderAndSidebar();
            loadSettingsView();
            alert(`Financial mode updated to ${newMode === 'TRACK_ONLY' ? 'Track Only' : 'Track + Monthly Budget'}!`);
        }
    } catch (err) {
        alert('Failed to switch mode: ' + err.message);
    }
}

async function handleUpdateProfile(e) {
    e.preventDefault();
    const fullName = document.getElementById('settings-name').value.trim();
    const currency = document.getElementById('settings-currency').value;

    try {
        const resp = await apiFetch('/api/settings', {
            method: 'POST',
            body: { action: 'updateProfile', fullName, currency }
        });
        if (resp && resp.user) {
            currentUser = resp.user;
            updateUserHeaderAndSidebar();
            alert('Profile updated successfully!');
        }
    } catch (err) {
        alert('Failed to update profile: ' + err.message);
    }
}

async function handleChangePassword(e) {
    e.preventDefault();
    const currentPassword = document.getElementById('settings-cur-pass').value;
    const newPassword = document.getElementById('settings-new-pass').value;
    const confirmPassword = document.getElementById('settings-conf-pass').value;

    if (newPassword !== confirmPassword) {
        alert('New passwords do not match!');
        return;
    }

    try {
        await apiFetch('/api/settings', {
            method: 'POST',
            body: { action: 'changePassword', currentPassword, newPassword, confirmPassword }
        });
        alert('Password changed successfully!');
        document.getElementById('settings-cur-pass').value = '';
        document.getElementById('settings-new-pass').value = '';
        document.getElementById('settings-conf-pass').value = '';
    } catch (err) {
        alert('Failed to change password: ' + err.message);
    }
}

async function renderSettingsCategories() {
    const list = document.getElementById('settings-categories-list');
    list.innerHTML = '';
    await populateCategoryDropdowns();

    cachedCategories.forEach(c => {
        const badge = document.createElement('div');
        badge.className = 'badge';
        badge.style.background = (c.color || '#78BFA0') + '22';
        badge.style.color = c.color || '#2D6B50';
        badge.style.padding = '8px 12px';
        badge.style.display = 'inline-flex';
        badge.style.alignItems = 'center';
        badge.style.gap = '8px';

        badge.innerHTML = `
            <span>${c.name}</span>
            ${!c.isDefault ? `<span onclick="deleteCustomCategory(${c.id})" style="cursor: pointer; font-weight: bold; margin-left: 4px;">✕</span>` : ''}
        `;
        list.appendChild(badge);
    });
}

async function addCustomCategory() {
    const name = document.getElementById('new-cat-name').value.trim();
    const color = document.getElementById('new-cat-color').value;

    if (!name) {
        alert('Please enter a category name.');
        return;
    }

    try {
        await apiFetch('/api/categories', {
            method: 'POST',
            body: { name, color, iconName: 'TAG' }
        });
        document.getElementById('new-cat-name').value = '';
        renderSettingsCategories();
    } catch (err) {
        alert('Failed to add category: ' + err.message);
    }
}

async function deleteCustomCategory(id) {
    if (!confirm('Are you sure you want to remove this custom category?')) return;
    try {
        await apiFetch(`/api/categories?id=${id}`, { method: 'DELETE' });
        renderSettingsCategories();
    } catch (err) {
        alert('Failed to delete category: ' + err.message);
    }
}

// --- Database Connectivity Settings (Admin Only) ---
async function loadDatabaseSettings() {
    try {
        const data = await apiFetch('/api/database');
        const badge = document.getElementById('db-status-badge');
        if (badge) {
            badge.textContent = data.statusText || 'Connected';
            badge.className = data.isMySQL ? 'badge badge-sage' : 'badge badge-blue';
        }
        const isSqlite = (data.dbType === 'sqlite' || !data.isMySQL);
        const radioSqlite = document.getElementById('db-choice-sqlite');
        const radioMysql = document.getElementById('db-choice-mysql');
        if (radioSqlite && radioMysql) {
            radioSqlite.checked = isSqlite;
            radioMysql.checked = !isSqlite;
        }
        onDbEngineChange(isSqlite ? 'sqlite' : 'mysql');
    } catch (e) {
        console.error('Failed to load database status:', e);
    }
}

function onDbEngineChange(engine) {
    const sqlitePanel = document.getElementById('db-sqlite-panel');
    const mysqlPanel = document.getElementById('db-mysql-panel');
    if (sqlitePanel && mysqlPanel) {
        if (engine === 'sqlite') {
            sqlitePanel.style.display = 'block';
            mysqlPanel.style.display = 'none';
        } else {
            sqlitePanel.style.display = 'none';
            mysqlPanel.style.display = 'block';
        }
    }
}

async function saveSqliteConfig() {
    const alertBox = document.getElementById('db-conn-alert');
    if (alertBox) alertBox.style.display = 'none';

    try {
        const resp = await apiFetch('/api/database', {
            method: 'POST',
            body: { action: 'save', dbType: 'sqlite' }
        });
        showToast('Active database set to SQLite (expensebook.db)!', 'success');
        if (alertBox) {
            alertBox.textContent = `Database configuration saved! Current engine: ${resp.statusText}`;
            alertBox.className = 'badge badge-sage';
            alertBox.style.display = 'flex';
        }
        loadDatabaseSettings();
        populateCategoryDropdowns();
        if (currentView === 'dashboard') loadDashboard();
    } catch (err) {
        showToast('Failed to save SQLite config: ' + err.message, 'error');
    }
}

async function handleTestDatabaseConfig() {
    const host = document.getElementById('db-host').value.trim();
    const port = parseInt(document.getElementById('db-port').value);
    const database = document.getElementById('db-name').value.trim();
    const user = document.getElementById('db-user').value.trim();
    const password = document.getElementById('db-pass').value;

    const resEl = document.getElementById('db-test-result');
    resEl.textContent = 'Testing connection...';
    resEl.style.color = '#71807A';

    try {
        const resp = await apiFetch('/api/database', {
            method: 'POST',
            body: { action: 'test', host, port, database, user, password }
        });
        if (resp && resp.connected) {
            resEl.textContent = '✅ Connected successfully to MySQL!';
            resEl.style.color = '#2D6B50';
        } else {
            resEl.textContent = '❌ Connection failed. Check password/service.';
            resEl.style.color = '#E88C8C';
        }
    } catch (err) {
        resEl.textContent = '❌ Error: ' + err.message;
        resEl.style.color = '#E88C8C';
    }
}

async function handleSaveDatabaseConfig(e) {
    e.preventDefault();
    const host = document.getElementById('db-host').value.trim();
    const port = parseInt(document.getElementById('db-port').value);
    const database = document.getElementById('db-name').value.trim();
    const user = document.getElementById('db-user').value.trim();
    const password = document.getElementById('db-pass').value;

    const alertBox = document.getElementById('db-conn-alert');
    if (alertBox) alertBox.style.display = 'none';

    try {
        const resp = await apiFetch('/api/database', {
            method: 'POST',
            body: { action: 'save', dbType: 'mysql', host, port, database, user, password }
        });
        showToast('MySQL configuration saved & connected!', 'success');
        if (alertBox) {
            alertBox.textContent = `Database configuration saved! Current engine: ${resp.statusText}`;
            alertBox.className = resp.isMySQL ? 'badge badge-sage' : 'badge badge-blue';
            alertBox.style.display = 'flex';
        }
        loadDatabaseSettings();
        populateCategoryDropdowns();
        if (currentView === 'dashboard') loadDashboard();
    } catch (err) {
        showToast('Failed to save database config: ' + err.message, 'error');
    }
}

// --- Admin Governance View ---
async function loadAdminView() {
    loadDatabaseSettings();
    loadAdminAiSettings();
    try {
        const data = await apiFetch('/api/admin');
        document.getElementById('admin-total-users').textContent = data.totalUsers;
        document.getElementById('admin-active-users').textContent = data.activeUsers;
        document.getElementById('admin-total-cats').textContent = data.systemCategoriesCount;

        const tbody = document.getElementById('admin-users-table');
        tbody.innerHTML = '';

        if (data.users && data.users.length > 0) {
            data.users.forEach(u => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td>#${u.id}</td>
                    <td style="font-weight: 700;">${escapeHtml(u.fullName)}</td>
                    <td>${escapeHtml(u.email)}</td>
                    <td><span class="badge ${u.role === 'ADMIN' ? 'badge-peach' : 'badge-sage'}">${u.role}</span></td>
                    <td><span class="badge badge-blue">${u.financialMode}</span></td>
                    <td>
                        <span class="badge ${u.isActive ? 'badge-sage' : 'badge-coral'}">
                            ${u.isActive ? 'Active' : 'Suspended'}
                        </span>
                    </td>
                    <td style="text-align: right;">
                        ${u.role !== 'ADMIN' ? `
                            <button class="btn btn-secondary btn-small" onclick="toggleUserStatus(${u.id}, ${!u.isActive})">
                                ${u.isActive ? 'Suspend' : 'Activate'}
                            </button>
                            <button class="btn btn-danger btn-small" onclick="deleteUserAccount(${u.id})" style="margin-left: 4px;">
                                Delete
                            </button>
                        ` : '<span class="text-muted" style="font-size: 0.8rem;">Superuser</span>'}
                    </td>
                `;
                tbody.appendChild(tr);
            });
        }
    } catch (e) {
        console.error('Error loading admin view:', e);
    }
}

async function toggleUserStatus(userId, active) {
    try {
        await apiFetch('/api/admin', {
            method: 'POST',
            body: { action: 'toggleStatus', userId, active }
        });
        loadAdminView();
    } catch (err) {
        alert('Failed to update user status: ' + err.message);
    }
}

async function deleteUserAccount(userId) {
    if (!confirm('Are you sure you want to delete this user? Their account will be permanently deactivated.')) return;
    try {
        await apiFetch('/api/admin', {
            method: 'POST',
            body: { action: 'deleteUser', userId }
        });
        loadAdminView();
    } catch (err) {
        alert('Failed to delete user: ' + err.message);
    }
}

// --- Helper Utilities ---
function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

// =========================================================
// ExPense Book — PennyWise AI Assistant Controller
// =========================================================

let aiChatHistory = [];
let userGeminiApiKey = localStorage.getItem('eb_gemini_key') || '';

// Load / Initialize AI Assistant View
async function loadAiAssistant() {
    updateAiModelBadges();
    const container = document.getElementById('ai-chat-messages');
    if (container && container.children.length === 0) {
        initAiWelcomeMessage();
    }
    setTimeout(() => {
        const input = document.getElementById('ai-user-input');
        if (input) input.focus();
        scrollAiMessagesToBottom();
    }, 100);
}

function updateAiModelBadges() {
    const badge = document.getElementById('ai-model-badge');
    const drawerBadge = document.getElementById('ai-drawer-badge');

    apiFetch('/api/ai/status').then(res => {
        if (res && res.configured) {
            const text = '🟢 Gemini 2.5 Flash';
            if (badge) {
                badge.textContent = text;
                badge.className = 'badge badge-sage';
            }
            if (drawerBadge) drawerBadge.textContent = 'Gemini 2.5 Flash';
        } else {
            const text = '🟢 PennyWise AI';
            if (badge) {
                badge.textContent = text;
                badge.className = 'badge badge-sage';
            }
            if (drawerBadge) drawerBadge.textContent = 'PennyWise AI';
        }
    }).catch(() => {
        if (badge) badge.textContent = '🟢 PennyWise AI';
    });
}

function initAiWelcomeMessage() {
    const firstName = (currentUser && currentUser.fullName) ? currentUser.fullName.split(' ')[0] : 'there';
    const welcomeHtml = `
        <p>👋 <strong>Hey ${escapeHtml(firstName)}!</strong> I'm <strong>PennyWise AI</strong>, your personal finance assistant &amp; intelligent companion.</p>
        <p>I have live visibility into your <strong>expenses</strong>, <strong>monthly budget</strong>, <strong>top spending categories</strong>, and <strong>financial trends</strong>.</p>
        <p>Feel free to ask me anything — from detailed spending breakdowns and custom budget advice, to general trivia, jokes, coding questions, or life advice! 🌟</p>
    `;
    renderAiMessage('assistant', welcomeHtml, 'Now', false);
}

function scrollAiMessagesToBottom() {
    const c1 = document.getElementById('ai-chat-messages');
    if (c1) c1.scrollTop = c1.scrollHeight;
    const c2 = document.getElementById('ai-drawer-messages');
    if (c2) c2.scrollTop = c2.scrollHeight;
}

// Markdown parser
function formatAiMarkdown(text) {
    if (!text) return '';
    let html = escapeHtml(text);

    // Headers ## and ###
    html = html.replace(/^###\s+(.*)$/gm, '<div style="font-size: 1.05rem; font-weight: 700; margin: 10px 0 4px 0; color: inherit;">$1</div>');
    html = html.replace(/^##\s+(.*)$/gm, '<div style="font-size: 1.15rem; font-weight: 800; margin: 12px 0 6px 0; color: inherit;">$1</div>');

    // Blockquotes > text (escapeHtml converted > to &gt;)
    html = html.replace(/^&gt;\s+(.*)$/gm, '<div style="border-left: 3px solid var(--accent, #0D828A); padding: 8px 12px; margin: 8px 0; background: rgba(13,130,138,0.07); border-radius: 0 8px 8px 0; font-size: 0.9em;">$1</div>');

    // Bold **text**
    html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');

    // Italic *text*
    html = html.replace(/\*(.*?)\*/g, '<em>$1</em>');

    // Inline code `code`
    html = html.replace(/`(.*?)`/g, '<code style="background: rgba(0,0,0,0.06); padding: 2px 5px; border-radius: 4px; font-size: 0.88em;">$1</code>');

    // Markdown Links [text](url)
    html = html.replace(/\[([^\]]+)\]\((https?:\/\/[^\s\)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer" style="color: var(--accent, #0D828A); text-decoration: underline; font-weight: 600;">$1</a>');

    // Numbered lists e.g. 1. text
    html = html.replace(/^(\d+)\.\s+(.*)$/gm, '<div style="margin-left: 12px; margin-bottom: 4px;"><strong>$1.</strong> $2</div>');

    // Bullet points e.g. - text or * text
    html = html.replace(/^[\*\-]\s+(.*)$/gm, '<div style="margin-left: 12px; margin-bottom: 4px;">• $1</div>');

    // Paragraphs / Newlines
    html = html.replace(/\n\n/g, '<p style="margin: 8px 0;"></p>');
    html = html.replace(/\n/g, '<br>');

    return html;
}

function renderAiMessage(role, contentHtml, timeStr = 'Now', isRawText = true) {
    const isUser = role === 'user';
    const formattedContent = isRawText ? formatAiMarkdown(contentHtml) : contentHtml;

    // Render to main chat view
    const mainContainer = document.getElementById('ai-chat-messages');
    if (mainContainer) {
        const row = document.createElement('div');
        row.className = `ai-msg-row ${isUser ? 'user' : 'assistant'}`;
        row.innerHTML = `
            ${!isUser ? '<div class="ai-msg-avatar assistant">✨</div>' : ''}
            <div class="ai-msg-bubble ${isUser ? 'user' : 'assistant'}">
                <div>${formattedContent}</div>
                <div class="ai-msg-time">${timeStr}</div>
            </div>
            ${isUser ? '<div class="ai-msg-avatar user">👤</div>' : ''}
        `;
        mainContainer.appendChild(row);
    }

    // Also sync to floating drawer
    const drawerContainer = document.getElementById('ai-drawer-messages');
    if (drawerContainer) {
        const row2 = document.createElement('div');
        row2.className = `ai-msg-row ${isUser ? 'user' : 'assistant'}`;
        row2.innerHTML = `
            ${!isUser ? '<div class="ai-msg-avatar assistant" style="width: 26px; height: 26px; font-size: 0.8rem;">✨</div>' : ''}
            <div class="ai-msg-bubble ${isUser ? 'user' : 'assistant'}" style="padding: 9px 13px; font-size: 0.84rem;">
                <div>${formattedContent}</div>
                <div class="ai-msg-time" style="font-size: 0.62rem;">${timeStr}</div>
            </div>
            ${isUser ? '<div class="ai-msg-avatar user" style="width: 26px; height: 26px; font-size: 0.8rem;">👤</div>' : ''}
        `;
        drawerContainer.appendChild(row2);
    }

    scrollAiMessagesToBottom();
}

async function sendAiUserMessage() {
    const input = document.getElementById('ai-user-input');
    if (!input) return;
    const msg = input.value.trim();
    if (!msg) return;

    input.value = '';
    input.style.height = 'auto';

    await executeAiChat(msg);
}

async function sendAiDrawerMessage() {
    const input = document.getElementById('ai-drawer-input');
    if (!input) return;
    const msg = input.value.trim();
    if (!msg) return;

    input.value = '';
    await executeAiChat(msg);
}

async function executeAiChat(message) {
    const timeNow = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    renderAiMessage('user', message, timeNow, true);

    // Show typing indicator in both views
    const t1 = document.getElementById('ai-typing-indicator');
    if (t1) t1.style.display = 'flex';
    const t2 = document.getElementById('ai-drawer-typing');
    if (t2) t2.style.display = 'flex';
    scrollAiMessagesToBottom();

    // Disable send buttons
    const btn1 = document.getElementById('ai-send-btn');
    if (btn1) btn1.disabled = true;

    try {
        const payload = {
            message: message,
            history: aiChatHistory.slice(-8) // Send last 8 turns for context
        };

        const res = await apiFetch('/api/ai/chat', {
            method: 'POST',
            body: payload
        });

        // Record history turn
        aiChatHistory.push({ role: 'user', content: message });
        aiChatHistory.push({ role: 'model', content: res.reply });

        const replyTime = res.timestamp || new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        renderAiMessage('assistant', res.reply, replyTime, true);

    } catch (err) {
        console.error('AI error:', err);
        renderAiMessage('assistant', `⚠️ **Sorry, I ran into an error:** ${escapeHtml(err.message)}`, 'Now', false);
    } finally {
        if (t1) t1.style.display = 'none';
        if (t2) t2.style.display = 'none';
        if (btn1) btn1.disabled = false;
        scrollAiMessagesToBottom();
    }
}

function handleQuickPrompt(promptText) {
    const input = document.getElementById('ai-user-input');
    if (input) {
        input.value = promptText;
        sendAiUserMessage();
    }
}

function handleAiInputKeydown(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendAiUserMessage();
    }
}

function handleAiDrawerKeydown(e) {
    if (e.key === 'Enter') {
        e.preventDefault();
        sendAiDrawerMessage();
    }
}

function clearAiChat() {
    aiChatHistory = [];
    const mainContainer = document.getElementById('ai-chat-messages');
    if (mainContainer) mainContainer.innerHTML = '';
    const drawerContainer = document.getElementById('ai-drawer-messages');
    if (drawerContainer) drawerContainer.innerHTML = '';
    initAiWelcomeMessage();
    showToast('Conversation cleared.', 'info');
}

// Floating Drawer Toggle
function toggleFloatingAi() {
    if (!currentUser || !currentToken) {
        showAuthUI();
        return;
    }
    const drawer = document.getElementById('ai-floating-drawer');
    if (!drawer) return;
    const isHidden = drawer.style.display === 'none' || !drawer.style.display;
    drawer.style.display = isHidden ? 'flex' : 'none';
    if (isHidden) {
        const drawerMessages = document.getElementById('ai-drawer-messages');
        if (drawerMessages && drawerMessages.children.length === 0) {
            initAiWelcomeMessage();
        }
        updateAiModelBadges();
        setTimeout(() => {
            const input = document.getElementById('ai-drawer-input');
            if (input) input.focus();
            scrollAiMessagesToBottom();
        }, 120);
    }
}

// --- Admin AI Assistant & Gemini API Configuration ---
async function loadAdminAiSettings() {
    try {
        const data = await apiFetch('/api/admin');
        const badge = document.getElementById('admin-ai-status-badge');
        const maskedEl = document.getElementById('admin-ai-key-masked');
        const input = document.getElementById('admin-ai-key-input');

        if (data.geminiConfigured) {
            if (badge) {
                badge.textContent = '🟢 Gemini Active';
                badge.className = 'badge badge-sage';
            }
            if (maskedEl) {
                maskedEl.textContent = `Active Key: ${data.geminiMaskedKey}`;
                maskedEl.style.color = '#2D6B50';
            }
            if (input && !input.value) {
                input.placeholder = `Active: ${data.geminiMaskedKey} (Enter new key to update)`;
            }
        } else {
            if (badge) {
                badge.textContent = '⚪ Local Engine Active';
                badge.className = 'badge badge-blue';
            }
            if (maskedEl) {
                maskedEl.textContent = 'Not configured (using smart local engine)';
                maskedEl.style.color = 'var(--text-muted)';
            }
            if (input && !input.value) {
                input.placeholder = 'Enter Gemini API key (e.g. AIzaSy...)';
            }
        }
    } catch (e) {
        console.error('Failed to load admin AI settings:', e);
    }
}

function toggleAdminAiKeyVisibility() {
    const input = document.getElementById('admin-ai-key-input');
    if (!input) return;
    input.type = input.type === 'password' ? 'text' : 'password';
}

async function handleTestAdminAiKey() {
    const input = document.getElementById('admin-ai-key-input');
    const apiKey = input ? input.value.trim() : '';
    const resEl = document.getElementById('admin-ai-test-result');
    if (!resEl) return;

    resEl.textContent = '⏳ Testing connection with Google Gemini...';
    resEl.style.color = '#71807A';

    try {
        const resp = await apiFetch('/api/admin', {
            method: 'POST',
            body: { action: 'testAiKey', apiKey: apiKey || null }
        });
        if (resp && resp.success) {
            const models = resp.sampleModels && resp.sampleModels.length > 0 ? resp.sampleModels.join(', ') : 'generateContent supported';
            resEl.textContent = `✅ API Key is Valid! Connected to Google Gemini (${resp.modelsCount} models discovered: ${models})`;
            resEl.style.color = '#2D6B50';
        } else {
            resEl.textContent = `❌ Test failed: ${resp.error || 'Unable to connect'}`;
            resEl.style.color = '#E88C8C';
        }
    } catch (err) {
        resEl.textContent = `❌ Test failed: ${err.message}`;
        resEl.style.color = '#E88C8C';
    }
}

async function handleSaveAdminAiKey(e) {
    e.preventDefault();
    const input = document.getElementById('admin-ai-key-input');
    const apiKey = input ? input.value.trim() : '';
    const alertBox = document.getElementById('admin-ai-alert');
    if (alertBox) alertBox.style.display = 'none';

    if (!apiKey) {
        showToast('Please enter a Gemini API key or click Remove Key.', 'error');
        return;
    }

    try {
        const resp = await apiFetch('/api/admin', {
            method: 'POST',
            body: { action: 'saveAiKey', apiKey }
        });
        showToast('Gemini API key saved & activated system-wide! 🎉', 'success');
        if (alertBox) {
            alertBox.textContent = `Gemini API key saved! Masked key: ${resp.maskedKey}`;
            alertBox.className = 'badge badge-sage';
            alertBox.style.display = 'flex';
        }
        if (input) input.value = '';
        loadAdminAiSettings();
        updateAiModelBadges();
    } catch (err) {
        showToast('Failed to save Gemini API key: ' + err.message, 'error');
    }
}

async function handleClearAdminAiKey() {
    if (!confirm('Are you sure you want to remove the system Gemini API key? PennyWise AI will revert to the smart local financial engine.')) {
        return;
    }
    const alertBox = document.getElementById('admin-ai-alert');
    if (alertBox) alertBox.style.display = 'none';

    try {
        await apiFetch('/api/admin', {
            method: 'POST',
            body: { action: 'saveAiKey', apiKey: '' }
        });
        showToast('Gemini API key removed. Using smart local engine.', 'info');
        const input = document.getElementById('admin-ai-key-input');
        if (input) input.value = '';
        const resEl = document.getElementById('admin-ai-test-result');
        if (resEl) resEl.textContent = '';
        loadAdminAiSettings();
        updateAiModelBadges();
    } catch (err) {
        showToast('Failed to remove Gemini API key: ' + err.message, 'error');
    }
}


// --- Direct Auth View Switcher ---
function switchAuthView(view) {
    const loginView = document.getElementById('view-login');
    const regView = document.getElementById('view-register');
    const modeView = document.getElementById('view-mode');
    const tabLogin = document.getElementById('tab-login');
    const tabReg = document.getElementById('tab-register');
    const errBox = document.getElementById('auth-error-box');

    if (errBox) errBox.style.display = 'none';
    if (modeView) modeView.style.display = 'none';

    if (view === 'login') {
        if (loginView) loginView.style.display = 'block';
        if (regView) regView.style.display = 'none';
        if (tabLogin) tabLogin.classList.add('active');
        if (tabReg) tabReg.classList.remove('active');
    } else if (view === 'register') {
        if (loginView) loginView.style.display = 'none';
        if (regView) regView.style.display = 'block';
        if (tabLogin) tabLogin.classList.remove('active');
        if (tabReg) tabReg.classList.add('active');
    } else if (view === 'mode') {
        if (loginView) loginView.style.display = 'none';
        if (regView) regView.style.display = 'none';
        if (modeView) modeView.style.display = 'block';
    }
}

function showLogin() {
    switchAuthView('login');
}

function showRegister() {
    switchAuthView('register');
}

function togglePasswordVisibility(inputId, btn) {
    const input = document.getElementById(inputId);
    if (!input) return;
    if (input.type === 'password') {
        input.type = 'text';
        btn.innerHTML = `<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path><line x1="1" y1="1" x2="23" y2="23"></line></svg>`;
    } else {
        input.type = 'password';
        btn.innerHTML = `<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg>`;
    }
}

function showAuthUI() {
    const auth = document.getElementById('auth-container');
    const app = document.getElementById('app-container');
    if (auth) auth.style.display = 'flex';
    if (app) app.style.display = 'none';

    // Strictly ensure all floating and nav items are hidden
    const fab = document.getElementById('ai-floating-fab');
    if (fab) fab.style.display = 'none';
    const drawer = document.getElementById('ai-floating-drawer');
    if (drawer) drawer.style.display = 'none';
    const bottomNav = document.getElementById('mobile-nav-bottom');
    if (bottomNav) bottomNav.style.display = 'none';

    switchAuthView('login');
}

function showAppUI() {
    const auth = document.getElementById('auth-container');
    const app = document.getElementById('app-container');
    if (auth) auth.style.display = 'none';
    if (app) app.style.display = 'flex';

    const fab = document.getElementById('ai-floating-fab');
    if (fab) fab.style.display = 'flex';

    const bottomNav = document.getElementById('mobile-nav-bottom');
    if (bottomNav && window.innerWidth <= 900) {
        bottomNav.style.display = 'flex';
    }

    updateUserHeaderAndSidebar();
    populateCategoryDropdowns();
    initAnalyticsPickers();
    navigate('dashboard');
}

async function handleLoginSubmit(e) {
    e.preventDefault();
    const email = document.getElementById('login-email').value.trim();
    const password = document.getElementById('login-pass').value;
    const errBox = document.getElementById('auth-error-box');
    const btn = document.getElementById('btn-login');

    if (errBox) errBox.style.display = 'none';
    if (btn) {
        btn.disabled = true;
        btn.textContent = 'Signing In...';
    }

    try {
        const resp = await apiFetch('/api/auth/login', {
            method: 'POST',
            body: { email, password }
        });
        if (resp && resp.token) {
            currentToken = resp.token;
            currentUser = resp.user;
            localStorage.setItem('eb_token', currentToken);
            showToast(`Welcome back, ${currentUser.fullName || 'User'}!`, 'success');
            showAppUI();
        }
    } catch (err) {
        if (errBox) {
            errBox.textContent = err.message || 'Invalid email or password.';
            errBox.style.display = 'flex';
        }
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.textContent = 'Sign In →';
        }
    }
}

function handleRegisterSubmit(e) {
    e.preventDefault();
    const name = document.getElementById('reg-name').value.trim();
    const email = document.getElementById('reg-email').value.trim();
    const password = document.getElementById('reg-pass').value;
    const confirmPassword = document.getElementById('reg-conf-pass').value;
    const errBox = document.getElementById('auth-error-box');

    if (errBox) errBox.style.display = 'none';

    if (password !== confirmPassword) {
        if (errBox) {
            errBox.textContent = 'Passwords do not match.';
            errBox.style.display = 'flex';
        }
        return;
    }

    pendingRegisterData = { fullName: name, email, password, confirmPassword };
    window.selectedOnboardingMode = 'TRACK_ONLY';
    switchAuthView('mode');
}

async function finalizePersonalization() {
    if (!pendingRegisterData) {
        switchAuthView('register');
        return;
    }

    const mode = window.selectedOnboardingMode || 'TRACK_ONLY';
    const errBox = document.getElementById('auth-error-box');

    try {
        const resp = await apiFetch('/api/auth/register', {
            method: 'POST',
            body: {
                fullName: pendingRegisterData.fullName,
                email: pendingRegisterData.email,
                password: pendingRegisterData.password,
                confirmPassword: pendingRegisterData.confirmPassword,
                mode: mode
            }
        });

        if (resp && resp.token) {
            currentToken = resp.token;
            currentUser = resp.user;
            localStorage.setItem('eb_token', currentToken);
            showToast('Account created successfully! Welcome to ExPense Book.', 'success');
            showAppUI();
        }
    } catch (err) {
        if (errBox) {
            errBox.textContent = 'Registration failed: ' + err.message;
            errBox.style.display = 'flex';
        }
        switchAuthView('register');
    }
}

function fillAdminCredentials() {
    switchAuthView('login');
    const emailEl = document.getElementById('login-email');
    const passEl = document.getElementById('login-pass');
    if (emailEl) emailEl.value = 'admin@expensebook.com';
    if (passEl) passEl.value = 'SURAJ@260203';
    showToast('Admin credentials filled!', 'info');
}

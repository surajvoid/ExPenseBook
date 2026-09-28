<p align="center">
  <img src="src/main/resources/web/logo.png" alt="ExPenseBook Logo" width="120" />
</p>

<h1 align="center">💰 ExPense Book</h1>

<p align="center">
  <strong>A Modern Full-Stack Personal Finance Management Application</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Maven-3.x-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white" alt="Maven" />
  <img src="https://img.shields.io/badge/MySQL-8.0-4479A1?style=for-the-badge&logo=mysql&logoColor=white" alt="MySQL" />
  <img src="https://img.shields.io/badge/SQLite-3.x-003B57?style=for-the-badge&logo=sqlite&logoColor=white" alt="SQLite" />
  <img src="https://img.shields.io/badge/Gemini_AI-Integrated-4285F4?style=for-the-badge&logo=google&logoColor=white" alt="Gemini AI" />
  <img src="https://img.shields.io/badge/Version-1.0.0-2D6B50?style=for-the-badge" alt="Version" />
</p>

---

## 📖 Overview

**ExPense Book** is a comprehensive personal finance management application built with Java and a modern SPA (Single Page Application) web interface. It helps users track expenses, manage budgets, monitor income, analyze spending patterns, and get AI-powered financial advice — all from a beautiful, responsive web dashboard.

The application supports two financial modes:
- **Track Only** — Simple expense logging and analysis
- **Track + Budget** — Full budget management with category limits, daily pacing, projections, and smart alerts

---

## ✨ Features

### 📊 Dashboard
- Real-time spending overview with monthly totals, daily averages, and days remaining
- Interactive daily spending trend chart (Chart.js)
- Category breakdown with color-coded progress bars
- Budget health card with progress tracking, projection insights, and alerts (Budget mode)
- Recent transactions feed

### 💳 Expense Management
- Add, edit, and delete expenses with category, amount, payment mode, date, and description
- Smart description defaults — auto-fills category name when left empty
- Filter and search transactions by category, payment mode, or keyword
- Sort by date, amount, or category

### 📅 Interactive Calendar
- Month-view calendar with daily spending indicators
- Click any day to view detailed transactions for that date
- Navigate between months with previous/next controls

### 📈 Analytics & Insights
- Category-wise spending doughnut chart
- Payment mode breakdown bar chart
- Month-over-month comparison with percentage change and summary insight
- 12-month yearly spending trend bar chart

### 💰 Budget Management (Track + Budget Mode)
- Set monthly overall budget limit
- Set per-category spending limits (envelope budgeting)
- Real-time budget tracking: spent, remaining, percentage used
- Recommended daily spend pacing
- Projected month-end spend with over-budget warnings
- Smart budget alerts (75%, 90%, 100% thresholds)

### 💵 Income & Balance Tracking
- Log income entries with source, amount, date, and description
- Net balance calculation (Income − Expenses)
- Monthly income summary

### 🔄 Recurring Bills & Subscriptions
- Add recurring expenses with title, amount, category, frequency, and next due date
- One-click "Log Now" to instantly record a recurring bill as today's expense
- Manage and cancel subscriptions

### 📄 Reports & Exports
- Monthly financial summary report
- Download reports as **PDF** (Apache PDFBox)
- Download transaction data as **CSV**

### 🤖 PennyWise AI Assistant
- **AI-powered financial advisor** embedded directly in the app
- Powered by **Google Gemini** (with dynamic model discovery and sequential fallback)
- Has **live access** to user's real financial data (expenses, budgets, categories, income)
- Answers any question enthusiastically — finance, general knowledge, coding, jokes, trivia
- **Rule-based smart fallback engine** when no API key is configured:
  - 50/30/20 budgeting rule explanations with personalized math
  - Emergency fund advice
  - Compound interest & investing guidance
  - Debt payoff strategies (Avalanche vs. Snowball)
  - Live spending breakdowns, category analysis, and savings tips
- Floating chat drawer accessible from any page
- Full-page AI Assistant view in sidebar
- Markdown rendering for rich formatted responses

### ⚙️ Settings & Customization
- Update display name and preferred currency symbol
- Switch between Track Only and Track + Budget modes anytime
- Add custom expense categories with custom colors
- Change password

### 🔐 Authentication & Security
- User registration with onboarding flow (choose financial mode)
- Secure login with session token management
- BCrypt password hashing
- Admin role with governance panel

### 👑 Admin Panel
- View all registered users with roles, modes, and status
- Suspend / activate / delete user accounts
- System category count overview
- **Database configuration** — switch between SQLite and MySQL with connection testing
- **Gemini API key management** — configure, test, save, or remove the system-wide API key

---

## 🏗️ Architecture

```
ExPenseBook/
├── pom.xml                          # Maven build configuration
├── schema.sql                       # MySQL database schema
├── db.properties                    # Database & API key configuration
├── run.bat                          # Launch script (Desktop JavaFX)
├── run_web.bat                      # Launch script (Web Server)
│
└── src/main/
    ├── java/com/expensebook/
    │   ├── Main.java                # JavaFX desktop entry point
    │   ├── model/                   # Data models (POJOs)
    │   │   ├── User.java
    │   │   ├── Expense.java
    │   │   ├── Category.java
    │   │   ├── Budget.java
    │   │   ├── CategoryBudget.java
    │   │   ├── Income.java
    │   │   ├── RecurringExpense.java
    │   │   ├── FinancialMode.java
    │   │   └── PaymentMode.java
    │   │
    │   ├── dao/                     # Data Access Objects (JDBC)
    │   │   ├── UserDAO.java
    │   │   ├── ExpenseDAO.java
    │   │   ├── CategoryDAO.java
    │   │   ├── BudgetDAO.java
    │   │   ├── IncomeDAO.java
    │   │   └── RecurringExpenseDAO.java
    │   │
    │   ├── service/                 # Business logic layer
    │   │   ├── UserService.java
    │   │   ├── ExpenseService.java
    │   │   ├── CategoryService.java
    │   │   ├── BudgetService.java
    │   │   ├── IncomeService.java
    │   │   ├── RecurringService.java
    │   │   ├── AnalysisService.java
    │   │   ├── ReportService.java
    │   │   └── GeminiAiService.java # AI engine with Gemini + rule-based fallback
    │   │
    │   ├── web/                     # Web server layer
    │   │   ├── WebApp.java          # Web server entry point
    │   │   └── WebServer.java       # HTTP handlers & REST API routes
    │   │
    │   └── util/                    # Utilities
    │       ├── DBConnection.java    # Dual-engine DB manager (MySQL + SQLite)
    │       ├── DateUtil.java
    │       ├── CurrencyUtil.java
    │       ├── PasswordUtil.java    # BCrypt wrapper
    │       ├── ValidationUtil.java
    │       ├── SessionManager.java
    │       ├── PDFReportUtil.java
    │       └── SVGIconUtil.java
    │
    └── resources/web/               # Frontend SPA
        ├── index.html               # Single Page Application (all views)
        ├── app.js                   # Complete SPA controller (~2200 lines)
        ├── style.css                # Modern sage/forest-themed stylesheet
        ├── logo.png                 # App logo
        ├── brand-banner.png         # Brand banner
        ├── brand-wordmark-dark.png  # Dark wordmark
        └── brand-wordmark-light.png # Light wordmark
```

### Layered Architecture

```
┌─────────────────────────────────────────┐
│            Frontend (SPA)               │
│   index.html + app.js + style.css       │
│   Chart.js for visualizations           │
└──────────────┬──────────────────────────┘
               │ REST API (JSON)
┌──────────────▼──────────────────────────┐
│          WebServer.java                 │
│   Java HttpServer (com.sun.net.httpserver)│
│   Routes: /api/auth, /api/expenses,     │
│   /api/dashboard, /api/ai/chat, etc.    │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│         Service Layer                   │
│   Business logic, validation,           │
│   AI chat engine (Gemini + fallback)    │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│          DAO Layer (JDBC)               │
│   Raw SQL queries with PreparedStatements│
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│         Database Engine                 │
│   MySQL 8.0 (primary)                   │
│   SQLite (portable fallback)            │
└─────────────────────────────────────────┘
```

---

## 🚀 Getting Started

### Prerequisites

| Requirement | Version | Notes |
|------------|---------|-------|
| **Java JDK** | 21+ | [Eclipse Temurin](https://adoptium.net/) recommended |
| **Apache Maven** | 3.8+ | For building and running |
| **MySQL** | 8.0+ | Optional — SQLite works out of the box |

### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/ExPenseBook.git
cd ExPenseBook
```

### 2. Database Setup

#### Option A: SQLite (Zero Configuration)
No setup needed! The app will create `expensebook.db` automatically on first run.

#### Option B: MySQL
```sql
-- Run the schema file to create the database and tables
mysql -u root -p < schema.sql
```

Then configure `db.properties`:
```properties
db.type=mysql
db.host=localhost
db.port=3306
db.name=expensebook
db.user=root
db.password=your_password
```

### 3. Build & Run

```bash
# Compile the project
mvn compile

# Start the web server
mvn exec:java -Dexec.mainClass=com.expensebook.web.WebApp
```

The application will start at:
- **Local**: http://localhost:8080
- **Network**: http://your-ip:8080 (accessible from mobile devices on the same network)

### 4. First-Time Setup

1. Open http://localhost:8080
2. Click **Create Account** to register
3. Choose your financial mode (Track Only or Track + Budget)
4. Start tracking your expenses!

### Default Admin Credentials
```
Email:    admin@expensebook.com
Password: Admin@123
```

---

## 🤖 AI Assistant Setup (Optional)

The AI Assistant works in two modes:

1. **Without API Key** — Uses the built-in smart rule-based financial engine (budgeting rules, spending analysis, savings tips)
2. **With Gemini API Key** — Full AI-powered conversations with live financial context

### Setting up Gemini AI:

1. Get a free API key from [Google AI Studio](https://aistudio.google.com/app/apikey)
2. Log in as **Admin** → Go to **Admin Panel**
3. Scroll to **🤖 AI Assistant & Gemini API Configuration**
4. Enter your API key → Click **⚡ Test API Key** → Click **💾 Save API Key**
5. The AI is now active for all users system-wide!

---

## 🔌 REST API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/auth/register` | Register new user |
| `POST` | `/api/auth/login` | Login and get session token |
| `POST` | `/api/auth/logout` | Logout current session |
| `GET`  | `/api/auth/current` | Get current authenticated user |
| `GET`  | `/api/dashboard` | Dashboard summary data |
| `GET/POST/PUT/DELETE` | `/api/expenses` | CRUD for expenses |
| `GET`  | `/api/categories` | List all categories |
| `POST/DELETE` | `/api/categories` | Manage custom categories |
| `GET`  | `/api/calendar` | Calendar view with daily spending |
| `GET`  | `/api/analytics` | Analytics data (charts, comparisons) |
| `GET/POST` | `/api/budget` | Budget management |
| `GET/POST/DELETE` | `/api/income` | Income tracking |
| `GET/POST/DELETE` | `/api/recurring` | Recurring bills management |
| `POST` | `/api/settings` | Update profile, mode, password |
| `GET`  | `/api/reports/pdf` | Download monthly PDF report |
| `GET`  | `/api/reports/csv` | Download monthly CSV export |
| `GET`  | `/api/ai/status` | Check AI configuration status |
| `POST` | `/api/ai/chat` | Send message to AI assistant |
| `GET/POST` | `/api/admin` | Admin panel operations |
| `GET/POST` | `/api/database` | Database configuration |

---

## 🛠️ Tech Stack

### Backend
| Technology | Purpose |
|-----------|---------|
| **Java 21** | Core application language |
| **Maven** | Build system & dependency management |
| **com.sun.net.httpserver** | Lightweight embedded HTTP server |
| **JDBC** | Database connectivity |
| **MySQL Connector/J 8.3** | MySQL driver |
| **SQLite JDBC 3.45** | SQLite driver (portable fallback) |
| **Gson 2.10** | JSON serialization/deserialization |
| **BCrypt (jbcrypt 0.4)** | Password hashing |
| **Apache PDFBox 2.0** | PDF report generation |
| **Google Gemini API** | AI-powered chat assistant |
| **JUnit 5** | Unit testing |

### Frontend
| Technology | Purpose |
|-----------|---------|
| **HTML5** | Single Page Application structure |
| **CSS3** | Custom sage/forest-themed design system |
| **Vanilla JavaScript** | SPA controller (~2200 lines, zero frameworks) |
| **Chart.js** | Interactive charts (line, bar, doughnut) |

### Design System
- 🌿 **Sage & Forest Green** color palette
- Modern squircle cards with subtle shadows
- Responsive layout (desktop + mobile)
- Floating AI chat drawer
- Toast notification system
- Emoji-driven category badges

---

## 📱 Responsive Design

ExPense Book is fully responsive and works on:
- 🖥️ **Desktop** — Full sidebar navigation with spacious dashboard
- 📱 **Mobile** — Collapsible sidebar, bottom navigation bar, touch-friendly UI
- 📊 **Tablet** — Adaptive grid layouts

Access from any device on your network via `http://your-ip:8080`

---

## 📂 Database Schema

The application uses 6 core tables:

| Table | Purpose |
|-------|---------|
| `users` | User accounts, roles, financial modes, currency preferences |
| `categories` | System + custom expense categories |
| `expenses` | Individual expense records |
| `income` | Income entries |
| `budgets` | Monthly budget limits |
| `category_budgets` | Per-category budget allocations |
| `recurring_expenses` | Subscription/recurring bill tracking |

See [`schema.sql`](schema.sql) for the complete database schema.

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Commit your changes: `git commit -m 'Add my feature'`
4. Push to the branch: `git push origin feature/my-feature`
5. Open a Pull Request

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

## 👨‍💻 Author

**Suraj** — Full-Stack Developer

---

<p align="center">
  <strong>Built with ❤️ using Java, Vanilla JS, and a whole lot of ☕</strong>
</p>

# AutoScientist AI 🔬

**An AI-powered data science platform that automates machine learning workflows, explains model predictions, evaluates fairness, and helps users explore datasets.**

AutoScientist AI is a full-stack application designed to simplify the data science lifecycle. It combines a React-based frontend with a Java Spring Boot backend to provide an integrated environment for data analysis, model experimentation, explainability, and reporting.

## ✨ Features

- **Automated Machine Learning (AutoML):** Streamline model experimentation and evaluation.
- **Data Analysis:** Explore datasets and generate statistical insights.
- **Explainable AI (XAI):** Help users understand model behavior and predictions.
- **Fairness Analysis:** Examine model performance across groups and identify potential disparities.
- **What-If Analysis:** Explore how changing input values may affect model outcomes.
- **Drift Monitoring:** Support analysis of changes in data distributions.
- **Ask Dataset:** Interact with dataset information through a question-and-answer interface.
- **Evidence Graph:** Connect analytical findings with supporting evidence.
- **Reports and PDF Export:** Present results in shareable report formats.
- **Workflow Navigation:** Organize analysis into structured stages.
- **Persistent Storage:** Support database-backed application data.
- **Responsive Interface:** Provide a browser-based interface for data science workflows.

## 🏗️ Technology Stack

| Component | Technologies |
|---|---|
| Backend | Java 17, Spring Boot 3 |
| Frontend | React 18, Vite |
| UI | Bootstrap 5 |
| Charts | Chart.js |
| Persistence | MySQL / H2 |
| Analytics | Java-based statistical and machine-learning engines |

## 🧩 Architecture

```text
AutoScientist AI
│
├── Frontend
│   ├── React 18
│   ├── Vite
│   ├── Bootstrap 5
│   └── Chart.js
│
├── Backend
│   ├── Java 17
│   ├── Spring Boot 3
│   ├── Statistical and ML Engines
│   └── REST APIs
│
└── Persistence
    ├── MySQL
    └── H2
```

## 🚀 Getting Started

### Prerequisites

Install the following tools:

- Java Development Kit (JDK) 17 or newer
- Node.js 22.12+ and npm (frontend build tool requirement)
- Git
- MySQL, if using the MySQL configuration

### 1. Clone the repository

```bash
git clone https://github.com/bharad1101/AutoScientist-AI.git
cd AutoScientist-AI
```

### 2. Start the backend

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

The `dev` profile uses local file-backed H2. Production must use
`SPRING_PROFILES_ACTIVE=mysql` with a real datasource; the application defaults
to `mysql` and refuses to start in production with an embedded database.

The backend is expected to run at:

```text
http://localhost:8080
```

### 3. Start the frontend

Open a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open the local frontend URL printed by Vite. The default development address is commonly:

```text
http://localhost:5173
```

## 🧪 Testing

```bash
cd backend && ./mvnw clean test
cd ../frontend && npm test -- --run
```

## Production Deployment

Backend production runs from `backend/Dockerfile` with Java 17, the `mysql`
profile, Flyway migrations, and `/api/health`. Frontend production builds from
`frontend` with `npm run build` and publishes `dist`; set
`VITE_API_BASE_URL` to the actual HTTPS backend URL ending in `/api` at build
time. The build rejects localhost URLs.

Set backend datasource credentials, `JWT_SECRET`, exact CORS origins,
`FRONTEND_URL`, and `FILE_UPLOAD_DIR` in the hosting provider's secret settings.
Set `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` and attach a durable disk at
`/app/uploads` before accepting uploaded datasets. No production hostname or
credentials are embedded in this repository. A Dockerfile and Vercel rewrite do
not prove that provider auto-deploy is configured; verify the connected GitHub
repository, branch, build settings, disk mount, and health check in the hosting
dashboards.

See the [release-readiness guide](release-readiness/FINAL_DEPLOYMENT_READINESS.md)
and [environment variable reference](release-readiness/PRODUCTION_ENVIRONMENT_VARIABLES.md)
for required settings and outstanding pre-push checks.

## 🔐 Configuration

Configure database connections and other environment-specific settings using the application's supported configuration files or environment variables.

**Security recommendations:**
- Do not commit passwords, API keys, tokens, or other secrets.
- Keep local environment files out of version control.
- Use environment-specific database credentials.
- Review the repository before making it public.

## 🗺️ Project Roadmap

Potential future improvements include:

- Expanded model selection and evaluation methods
- More interactive visualizations
- Additional explainability techniques
- Enhanced dataset validation and monitoring
- Improved documentation and deployment options

## 🤝 Contributing

Contributions, bug reports, and feature suggestions are welcome.

1. Fork the repository.
2. Create a feature branch.
3. Make and test your changes.
4. Submit a pull request describing your contribution.

## 📄 License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## 👨‍💻 Author

**Contributors:** Pola Bharadwaj and Kola Rajesh

GitHub: [@bharad1101](https://github.com/bharad1101)

---

*Built to make data science workflows more accessible, explainable, and reproducible.*

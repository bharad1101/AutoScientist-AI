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
| Backend | Java 21, Spring Boot 3 |
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
│   ├── Java 21
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

- Java Development Kit (JDK) 21
- Node.js and npm
- Git
- MySQL, if using the MySQL configuration

### 1. Clone the repository

```bash
git clone https://github.com/bharad1101/AutoScientist-AI.git
cd AutoScientist-AI
```

### 2. Start the backend

Open a terminal in the project directory and follow the backend build configuration.

For a Maven-based Spring Boot project:

```bash
cd backend
./mvnw spring-boot:run
```

If the project uses a system-installed Maven instead of the Maven wrapper, use:

```bash
mvn spring-boot:run
```

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

**Note:** Confirm the actual backend and frontend folder names and build commands in your repository before relying on these instructions.

## 🧪 Testing

Run the backend tests using the build tool configured in the project.

For Maven:

```bash
./mvnw test
```

For a Gradle project, use its Gradle wrapper and configured test task instead.

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

**Pola Bharadwaj**

GitHub: [@bharad1101](https://github.com/bharad1101)

---

*Built to make data science workflows more accessible, explainable, and reproducible.*

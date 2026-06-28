# PayOps360 - Payment Operations Management System

**PayOps360** is a comprehensive payment operations management platform designed to streamline payment processing, monitoring, and analytics across multiple payment providers.

## 📋 Project Overview

PayOps360 provides real-time payment tracking, intelligent alerting, performance analytics, and incident management capabilities for modern payment operations teams.

### Key Features

- 💳 **Multi-Provider Payment Processing** - Unified interface for multiple payment gateways
- 📊 **Real-Time Analytics** - Live dashboards and performance metrics
- 🔔 **Intelligent Alerting** - Smart notifications for payment anomalies
- 🎯 **Incident Management** - Track and resolve payment issues efficiently
- 👥 **User Management** - Role-based access control and team collaboration
- 📈 **Performance Monitoring** - Provider performance tracking and SLA monitoring
- 🔍 **Advanced Search & Filtering** - Powerful payment transaction search
- 🌐 **WebSocket Support** - Real-time updates and notifications

## 🏗️ Project Structure

```
payOps360/
├── payops360-backend/       # Spring Boot backend application
├── payops360-frontend/      # Next.js frontend application
├── docs/                    # Documentation files
└── README.md               # This file
```

## 🚀 Quick Start

### Prerequisites

- **Backend**: Java 17+, Maven 3.6+, PostgreSQL 14+
- **Frontend**: Node.js 18+, npm 9+

### Backend Setup

```bash
cd payops360-backend
mvn clean install
mvn spring-boot:run
```

Backend will run on `http://localhost:8080`

### Frontend Setup

```bash
cd payops360-frontend
npm install
npm run dev
```

Frontend will run on `http://localhost:3000`

## 📚 Documentation

Detailed documentation is available in the [`docs/`](./docs/) folder:

- **[Master Documentation](./docs/PAYOPS360_COMPLETE_MASTER_DOCUMENTATION.md)** - Complete system overview
- **[Backend Help](./docs/BACKEND_HELP.md)** - Backend setup and development guide
- **[Frontend README](./docs/FRONTEND_README.md)** - Frontend setup and development guide
- **[Implementation Phases](./docs/PHASES_1_TO_5_COMPLETE_IMPLEMENTATION.md)** - Development roadmap
- **[Quick Reference](./docs/QUICK_REFERENCE_UPDATED.md)** - API and feature quick reference

### Module Documentation

- [Payment Module](./docs/PAYMENT_MODULE_COMPLETE.md)
- [Alert System](./docs/PHASE2_ALERT_MODULE_COMPLETE.md)
- [Background Schedulers](./docs/BACKGROUND_SCHEDULERS_COMPLETE.md)
- [User Management](./docs/USER_MANAGEMENT_STATUS.md)
- [Theme Implementation](./docs/THEME_IMPLEMENTATION.md)

## 🛠️ Tech Stack

### Backend
- **Framework**: Spring Boot 3.x
- **Database**: PostgreSQL with Flyway migrations
- **Security**: Spring Security with JWT
- **WebSocket**: STOMP over WebSocket
- **API Documentation**: OpenAPI/Swagger

### Frontend
- **Framework**: Next.js 14+ (App Router)
- **Language**: TypeScript
- **Styling**: Tailwind CSS
- **State Management**: Zustand
- **UI Components**: Custom component library
- **Real-time**: WebSocket client

## 🔐 Default Credentials

For development environment:
- **Username**: `admin@payops360.com`
- **Password**: `admin123`

⚠️ **Important**: Change default credentials in production!

## 🧪 Testing

### Backend Tests
```bash
cd payops360-backend
mvn test
```

### Frontend Tests
```bash
cd payops360-frontend
npm test
```

## 📦 Build for Production

### Backend
```bash
cd payops360-backend
mvn clean package
java -jar target/payops360-*.jar
```

### Frontend
```bash
cd payops360-frontend
npm run build
npm start
```

## 🤝 Contributing

This is an MVP project. For contributions:

1. Follow the existing code structure and naming conventions
2. Write clean, documented code
3. Test your changes thoroughly
4. Update documentation as needed

## 📄 License

Proprietary - All rights reserved

## 📞 Support

For issues or questions, please refer to the documentation in the `docs/` folder.

---

**Version**: 1.0.0 (MVP)  
**Last Updated**: June 2026


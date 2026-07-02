# PayOps360 Backend

Spring Boot backend application for the PayOps360 payment operations management system.

## 🏗️ Architecture

This is a Spring Boot 3.x application following clean architecture principles with:

- **RESTful API** endpoints for all operations
- **WebSocket** support for real-time notifications
- **PostgreSQL** database with Flyway migrations
- **JWT-based** authentication and authorization
- **Role-based** access control (RBAC)

## 📁 Project Structure

```
src/
├── main/
│   ├── java/com/payOps/payops360/
│   │   ├── controller/         # REST API endpoints
│   │   ├── service/            # Business logic
│   │   ├── repository/         # Data access layer
│   │   ├── model/              # Entity models
│   │   ├── dto/                # Data transfer objects
│   │   ├── config/             # Configuration classes
│   │   ├── security/           # Security configuration
│   │   ├── websocket/          # WebSocket handlers
│   │   └── exception/          # Exception handling
│   └── resources/
│       ├── application.yml     # Application configuration
│       └── db/migration/       # Flyway database migrations
└── test/                       # Unit and integration tests
```

## 🚀 Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- PostgreSQL 14+

### Database Setup

1. Create a PostgreSQL database:
```sql
CREATE DATABASE payops360;
CREATE USER payops360_user WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE payops360 TO payops360_user;
```

2. Update `src/main/resources/application.yml` with your database credentials:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/payops360
    username: payops360_user
    password: your_password
```

### Running the Application

```bash
# Install dependencies and run
mvn clean install
mvn spring-boot:run

# Or run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The application will start on `http://localhost:8080`

## 🔧 Configuration

### Application Profiles

- **default** - Development profile
- **test** - Testing profile
- **prod** - Production profile

### Key Configuration Properties

```yaml
server:
  port: 8080

spring:
  application:
    name: PayOps360

jwt:
  secret: your-secret-key
  expiration: 86400000  # 24 hours

payment:
  providers:
    - name: stripe
      enabled: true
    - name: paypal
      enabled: true
```

## 📋 API Documentation

Once the application is running, access API documentation at:

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI Spec**: `http://localhost:8080/v3/api-docs`

## 🔐 Authentication

The API uses JWT tokens for authentication.

### Login
```bash
POST /api/auth/login
Content-Type: application/json

{
  "email": "admin@payops360.com",
  "password": "admin123"
}
```

### Use Token
```bash
GET /api/payments
Authorization: Bearer <your-jwt-token>
```

## 🗄️ Database Migrations

Flyway manages database schema versions. Migrations are located in:
`src/main/resources/db/migration/`

Migration naming convention: `V{version}__{description}.sql`

### Run Migrations
```bash
mvn flyway:migrate
```

### Check Migration Status
```bash
mvn flyway:info
```

## 🧪 Testing

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=PaymentControllerTest

# Run with coverage
mvn test jacoco:report
```

## 📦 Building for Production

```bash
# Build JAR
mvn clean package -DskipTests

# Run JAR
java -jar target/payops360-*.jar

# With specific profile
java -jar target/payops360-*.jar --spring.profiles.active=prod
```

## 🔌 WebSocket Endpoints

WebSocket connection: `ws://localhost:8080/ws`

### Topics
- `/topic/notifications` - General notifications
- `/topic/payments` - Payment updates
- `/topic/alerts` - Alert notifications
- `/user/queue/private` - User-specific messages

## 📊 Key Modules

### Payment Processing
- Payment creation and tracking
- Multi-provider support
- Transaction lifecycle management
- Payment reconciliation

### Alert System
- Rule-based alerting
- Threshold monitoring
- Anomaly detection
- Notification dispatch

### Analytics
- Real-time metrics
- Provider performance tracking
- Payment lifecycle analytics
- Custom reporting

### User Management
- User CRUD operations
- Role and permission management
- Authentication and authorization
- Session management

## 🛠️ Development

### Code Style
- Follow Java naming conventions
- Use Lombok for boilerplate reduction
- Write JavaDoc for public APIs
- Keep methods focused and small

### Adding a New Feature

1. Create entity in `model/` package
2. Create repository in `repository/` package
3. Implement service in `service/` package
4. Create controller in `controller/` package
5. Add database migration in `db/migration/`
6. Write tests

## 📝 Logging

Logs are configured in `application.yml`:

```yaml
logging:
  level:
    com.payOps.payops360: DEBUG
    org.springframework: INFO
```

## 🐛 Troubleshooting

### Common Issues

1. **Database connection failed**
   - Check PostgreSQL is running
   - Verify credentials in application.yml

2. **Port already in use**
   - Change port in application.yml
   - Or kill process using port 8080

3. **Migration failed**
   - Check migration SQL syntax
   - Verify database permissions

## 📚 Additional Resources

See the `docs/` folder in the project root for detailed documentation.

---

**Backend Version**: 1.0.0  
**Spring Boot Version**: 3.x  
**Java Version**: 17+


# PayOps360 - Complete Setup & Testing Guide

## 🎯 Database Setup

### Database Name: `payops360`

### Step 1: Create Database

Open PostgreSQL (pgAdmin or command line) and run:

```sql
-- Create database
CREATE DATABASE payops360
WITH 
OWNER = postgres
ENCODING = 'UTF8'
LC_COLLATE = 'en_US.UTF-8'
LC_CTYPE = 'en_US.UTF-8'
TEMPLATE = template0;

-- Verify
\c payops360
```

### Step 2: Verify Connection

Update `payops360-backend/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/payops360
    username: postgres
    password: postgres  # Change to your PostgreSQL password
```

---

## 🚀 Backend Setup & Start

### Step 1: Navigate to Backend

```powershell
cd C:\Users\sufyan.abdulghani\Downloads\MVP\payOps360\payops360-backend
```

### Step 2: Clean and Build

```powershell
.\mvnw clean install -DskipTests
```

### Step 3: Start Backend

```powershell
.\mvnw spring-boot:run
```

**Expected Output:**
```
Started PayOps360Application in X seconds
Flyway migrations completed successfully
```

### Step 4: Verify Backend is Running

Open browser: `http://localhost:8080/actuator/health`

Expected: `{"status":"UP"}`

### Step 5: Check Swagger Documentation

Open browser: `http://localhost:8080/swagger-ui.html`

You should see all API endpoints including:
- **Authentication**: `/api/v1/auth/*`
- **Payments**: `/api/v1/payments/*`
- **Alerts**: `/api/v1/alerts/*`
- etc.

---

## 🎨 Frontend Setup & Start

### Step 1: Navigate to Frontend

```powershell
cd C:\Users\sufyan.abdulghani\Downloads\MVP\payOps360\payops360-frontend
```

### Step 2: Install Dependencies (if not done)

```powershell
npm install
```

### Step 3: Create Environment File

Create `.env.local`:

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1
```

### Step 4: Start Frontend

```powershell
npm run dev
```

**Expected Output:**
```
▲ Next.js 16.2.9
- Local: http://localhost:3000
- Ready in Xms
```

### Step 5: Open Application

Open browser: `http://localhost:3000`

---

## ✅ Testing the Complete Flow

### Test 1: Register New User

1. Go to `http://localhost:3000/register`
2. Fill in the form:
   - First Name: John
   - Last Name: Doe
   - Email: john.doe@test.com
   - Password: Test@123
3. Click "Register"
4. **Expected**: Redirect to login page with success message

### Test 2: Login with Test User

Use one of the seeded users:

**Option A - Admin User:**
- Email: `admin@payops360.com`
- Password: `Admin@123`

**Option B - Operations Manager:**
- Email: `ops@payops360.com`
- Password: `Ops@123`

**Option C - Analyst:**
- Email: `analyst@payops360.com`
- Password: `Analyst@123`

**Expected:**
- JWT token stored in localStorage
- Redirect to `/dashboard`
- User info displayed in header

### Test 3: Access Dashboard

After login, you should see:
- Dashboard with metrics
- Navigation menu working
- User profile dropdown
- All links functional

### Test 4: Test Protected Routes

1. Try accessing `/dashboard/payments` - should load
2. Logout
3. Try accessing `/dashboard` directly - should redirect to login

### Test 5: Test API Calls

Open browser DevTools (F12) → Network tab:

1. Login - Check request/response
   - Request: `POST /api/v1/auth/login`
   - Response: `{ success: true, data: { accessToken, refreshToken, user } }`

2. Get current user - Check Authorization header
   - Request: `GET /api/v1/auth/me`
   - Headers: `Authorization: Bearer <token>`

### Test 6: Test Logout

1. Click user menu → Logout
2. **Expected**: 
   - localStorage cleared
   - Redirect to login page
   - Cannot access protected routes

---

## 🔍 Verification Checklist

### Backend Verification

- [ ] Database `payops360` created
- [ ] Backend starts without errors
- [ ] Flyway migrations ran (16 migrations)
- [ ] Swagger UI accessible at `http://localhost:8080/swagger-ui.html`
- [ ] Health endpoint returns UP
- [ ] Can see all /api/v1/auth/* endpoints in Swagger

### Frontend Verification

- [ ] Frontend runs on port 3000
- [ ] Register page loads
- [ ] Login page loads
- [ ] Dashboard requires authentication
- [ ] Browser shows no console errors

### Integration Verification

- [ ] Registration creates user in database
```sql
SELECT * FROM users WHERE email = 'john.doe@test.com';
```

- [ ] Login returns JWT token
- [ ] Token is sent with subsequent requests (check DevTools Network tab)
- [ ] `/auth/me` returns current user data
- [ ] Protected routes redirect to login when not authenticated
- [ ] Logout clears tokens

---

## 🐛 Troubleshooting

### Backend Won't Start

**Error**: `Could not connect to database`
- **Fix**: Check PostgreSQL is running
- **Fix**: Verify database name is `payops360`
- **Fix**: Check username/password in application.yml

**Error**: `Flyway migration failed`
- **Fix**: Drop and recreate database
```sql
DROP DATABASE IF EXISTS payops360;
CREATE DATABASE payops360;
```

### Frontend API Errors

**Error**: `Network Error` or `CORS error`
- **Fix**: Verify backend is running on port 8080
- **Fix**: Check `.env.local` has correct API URL
- **Fix**: Restart both frontend and backend

**Error**: `401 Unauthorized`
- **Fix**: Token might be expired, logout and login again
- **Fix**: Check Authorization header in DevTools

### Login Fails

**Error**: `Invalid email or password`
- **Fix**: Use correct test credentials:
  - admin@payops360.com / Admin@123
  - ops@payops360.com / Ops@123

**Error**: `Account is temporarily locked`
- **Fix**: Wait 30 minutes or manually unlock in database:
```sql
UPDATE users SET failed_login_attempts = 0, locked_until = NULL WHERE email = 'your@email.com';
```

---

## 📊 Database Verification

### Check Migrations

```sql
\c payops360
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
```

Should show 16 migrations (V1 to V16).

### Check Users

```sql
SELECT id, email, first_name, last_name, status, created_at 
FROM users;
```

Should show 4 default users (admin, ops, analyst, support).

### Check User Roles

```sql
SELECT u.email, ur.role 
FROM users u 
JOIN user_roles ur ON u.id = ur.user_id;
```

---

## 🔐 Test Credentials

| User | Email | Password | Role |
|------|-------|----------|------|
| Admin | admin@payops360.com | Admin@123 | ADMIN |
| Operations | ops@payops360.com | Ops@123 | OPERATIONS_MANAGER |
| Analyst | analyst@payops360.com | Analyst@123 | ANALYST |
| Support | support@payops360.com | Support@123 | SUPPORT |

---

## 📝 Next Steps After Testing

### 1. Test All Features

- [ ] Payments module
- [ ] Alerts module
- [ ] Incidents module
- [ ] Analytics module
- [ ] Provider management

### 2. Performance Testing

- [ ] API response times < 200ms
- [ ] Dashboard loads < 2 seconds
- [ ] No memory leaks in browser

### 3. Security Testing

- [ ] Cannot access API without token
- [ ] Token expires after 24 hours
- [ ] Passwords are hashed in database
- [ ] SQL injection prevention
- [ ] XSS protection

### 4. User Experience

- [ ] Smooth registration flow
- [ ] Clear error messages
- [ ] Loading states work
- [ ] Responsive design
- [ ] No broken links

---

## 🎉 Success Criteria

Your application is working correctly if:

✅ Backend starts without errors
✅ Frontend connects to backend
✅ You can register a new user
✅ You can login with test credentials
✅ Dashboard loads with data
✅ Navigation works
✅ Logout clears session
✅ Protected routes redirect to login
✅ API calls show in Network tab with JWT tokens
✅ Database has user records

---

## 📞 Common Commands Reference

### Backend

```powershell
# Clean build
.\mvnw clean install

# Run
.\mvnw spring-boot:run

# Run tests
.\mvnw test

# Package JAR
.\mvnw package
```

### Frontend

```powershell
# Install dependencies
npm install

# Development mode
npm run dev

# Build for production
npm run build

# Start production
npm start

# Lint
npm run lint
```

### Database

```sql
-- Connect to database
\c payops360

-- List tables
\dt

-- Check users
SELECT * FROM users;

-- Reset a user's password (BCrypt hash for "Test@123")
UPDATE users 
SET password_hash = '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5c4jN6LV5nL0u'
WHERE email = 'admin@payops360.com';

-- Clear all data (DANGEROUS!)
TRUNCATE users CASCADE;
```

---

**Ready to test! Start with creating the database, then backend, then frontend. Follow the checklist step by step.** 🚀


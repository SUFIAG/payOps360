# PayOps360 Frontend

Next.js frontend application for the PayOps360 payment operations management system.

## 🏗️ Architecture

Modern Next.js 14+ application using:

- **App Router** - Next.js App Router architecture
- **TypeScript** - Type-safe development
- **Tailwind CSS** - Utility-first styling
- **Zustand** - Lightweight state management
- **WebSocket** - Real-time updates
- **Server Components** - React Server Components where applicable

## 📁 Project Structure

```
payops360-frontend/
├── app/                        # Next.js App Router
│   ├── (auth)/                # Authentication pages (login, register)
│   ├── (dashboard)/           # Protected dashboard routes
│   │   ├── dashboard/         # Main dashboard
│   │   │   ├── alerts/        # Alerts management
│   │   │   ├── analytics/     # Analytics and reports
│   │   │   ├── incidents/     # Incident tracking
│   │   │   ├── payments/      # Payment management
│   │   │   ├── providers/     # Provider configuration
│   │   │   └── settings/      # User settings
│   │   └── layout.tsx         # Dashboard layout
│   ├── layout.tsx             # Root layout
│   ├── page.tsx               # Home page
│   ├── providers.tsx          # App providers wrapper
│   └── globals.css            # Global styles
├── components/                 # React components
│   ├── auth/                  # Authentication components
│   ├── notifications/         # Notification components
│   └── ui/                    # Reusable UI components
├── lib/                        # Utilities and libraries
│   ├── api/                   # API client and endpoints
│   ├── hooks/                 # Custom React hooks
│   ├── store/                 # State management (Zustand)
│   ├── websocket/             # WebSocket client
│   └── utils.ts               # Utility functions
├── types/                      # TypeScript type definitions
│   └── index.ts               # Shared types
└── public/                     # Static assets
```

## 🚀 Getting Started

### Prerequisites

- Node.js 18+ 
- npm 9+ or yarn 1.22+

### Installation

```bash
# Install dependencies
npm install

# Or with yarn
yarn install
```

### Environment Setup

Create a `.env.local` file in the root directory:

```env
# API Configuration
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_WS_URL=ws://localhost:8080/ws

# App Configuration
NEXT_PUBLIC_APP_NAME=PayOps360
NEXT_PUBLIC_APP_VERSION=1.0.0
```

### Development

```bash
# Run development server
npm run dev

# Run on different port
npm run dev -- -p 3001
```

The application will be available at `http://localhost:3000`

### Building

```bash
# Create production build
npm run build

# Start production server
npm start

# Export static site (if applicable)
npm run build && npm run export
```

## 🎨 Styling

### Tailwind CSS

This project uses Tailwind CSS for styling. Configuration is in `tailwind.config.ts`.

```typescript
// Example component with Tailwind
export default function Button() {
  return (
    <button className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700">
      Click Me
    </button>
  )
}
```

### Theme

The application supports light/dark themes. Theme configuration is in `app/globals.css`.

## 🔐 Authentication

### Protected Routes

Protected routes are wrapped with the `ProtectedRoute` component:

```typescript
import ProtectedRoute from '@/components/auth/ProtectedRoute'

export default function DashboardPage() {
  return (
    <ProtectedRoute>
      <div>Protected content</div>
    </ProtectedRoute>
  )
}
```

### Auth Store

Authentication state is managed with Zustand in `lib/store/authStore.ts`:

```typescript
import { useAuthStore } from '@/lib/store/authStore'

function Component() {
  const { user, login, logout } = useAuthStore()
  
  // Use auth state and methods
}
```

## 📡 API Integration

### API Client

The API client is configured in `lib/api/client.ts`:

```typescript
import { apiClient } from '@/lib/api/client'

// Make API calls
const payments = await apiClient.get('/payments')
const payment = await apiClient.post('/payments', data)
```

### API Endpoints

Endpoint functions are organized in `lib/api/`:

```typescript
import { authApi } from '@/lib/api/auth'

// Use typed API functions
const result = await authApi.login(email, password)
```

## 🔌 WebSocket

### WebSocket Hook

Use the `useWebSocket` hook for real-time updates:

```typescript
import { useWebSocket } from '@/lib/hooks/useWebSocket'

function Component() {
  const { isConnected, sendMessage } = useWebSocket({
    url: 'ws://localhost:8080/ws',
    onMessage: (message) => {
      console.log('Received:', message)
    }
  })
  
  return <div>Connected: {isConnected ? 'Yes' : 'No'}</div>
}
}
```

## 🧩 Components

### UI Components

Reusable UI components are in `components/ui/`:

- `button.tsx` - Button component
- `card.tsx` - Card container
- `input.tsx` - Form input
- `badge.tsx` - Status badge
- `date-range-picker.tsx` - Date range selector

Example usage:

```typescript
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'

export default function Example() {
  return (
    <Card>
      <h2>Title</h2>
      <Button onClick={() => {}}>Click</Button>
    </Card>
  )
}
```

## 📊 State Management

### Zustand Stores

- `authStore.ts` - Authentication state
- `notification-store.ts` - Notifications state

Creating a new store:

```typescript
import { create } from 'zustand'

interface MyStore {
  count: number
  increment: () => void
}

export const useMyStore = create<MyStore>((set) => ({
  count: 0,
  increment: () => set((state) => ({ count: state.count + 1 })),
}))
```

## 🧪 Testing

```bash
# Run tests (when configured)
npm test

# Run tests in watch mode
npm test -- --watch

# Run tests with coverage
npm test -- --coverage
```

## 📝 Code Style

### TypeScript

- Use TypeScript for all files
- Define types in `types/index.ts` or co-located with components
- Avoid `any` type - use `unknown` or proper types

### Component Structure

```typescript
'use client' // Only if client component needed

import { FC } from 'react'

interface ComponentProps {
  title: string
  count?: number
}

export const Component: FC<ComponentProps> = ({ title, count = 0 }) => {
  return (
    <div>
      <h1>{title}</h1>
      <p>Count: {count}</p>
    </div>
  )
}
```

### Naming Conventions

- **Components**: PascalCase (`UserProfile.tsx`)
- **Utilities**: camelCase (`formatDate.ts`)
- **Hooks**: camelCase with `use` prefix (`useAuth.ts`)
- **Types**: PascalCase (`User`, `PaymentStatus`)
- **Constants**: UPPER_SNAKE_CASE (`API_BASE_URL`)

## 📦 Key Dependencies

```json
{
  "next": "^14.x",
  "react": "^18.x",
  "typescript": "^5.x",
  "tailwindcss": "^3.x",
  "zustand": "^4.x",
  "axios": "^1.x"
}
```

## 🔧 Configuration Files

- `next.config.ts` - Next.js configuration
- `tailwind.config.ts` - Tailwind CSS configuration
- `tsconfig.json` - TypeScript configuration
- `eslint.config.mjs` - ESLint configuration
- `postcss.config.mjs` - PostCSS configuration

## 🚀 Deployment

### Vercel (Recommended)

```bash
# Install Vercel CLI
npm i -g vercel

# Deploy
vercel
```

### Docker

```bash
# Build Docker image
docker build -t payops360-frontend .

# Run container
docker run -p 3000:3000 payops360-frontend
```

### Static Export

```bash
# Build static export
npm run build

# Output in 'out' directory
```

## 🐛 Troubleshooting

### Common Issues

1. **Module not found**
   ```bash
   rm -rf node_modules package-lock.json
   npm install
   ```

2. **TypeScript errors**
   ```bash
   npm run type-check
   ```

3. **Port already in use**
   ```bash
   npm run dev -- -p 3001
   ```

4. **Environment variables not loading**
   - Ensure `.env.local` exists
   - Restart dev server after changes
   - Prefix public vars with `NEXT_PUBLIC_`

## 📚 Resources

- [Next.js Documentation](https://nextjs.org/docs)
- [Tailwind CSS](https://tailwindcss.com/docs)
- [TypeScript](https://www.typescriptlang.org/docs)
- [Zustand](https://github.com/pmndrs/zustand)

## 📚 Additional Documentation

See the `docs/` folder in the project root for detailed system documentation.

---

**Frontend Version**: 1.0.0  
**Next.js Version**: 14+  
**Node Version**: 18+


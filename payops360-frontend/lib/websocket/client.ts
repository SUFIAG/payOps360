import { io, Socket } from 'socket.io-client';

type WebSocketEvent =
  | 'payment:created'
  | 'payment:updated'
  | 'payment:failed'
  | 'alert:created'
  | 'alert:updated'
  | 'incident:created'
  | 'incident:updated'
  | 'provider:health_changed'
  | 'metrics:updated';

type EventCallback = (data: any) => void;

class WebSocketClient {
  private socket: Socket | null = null;
  private listeners: Map<WebSocketEvent, Set<EventCallback>> = new Map();
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private reconnectDelay = 2000;

  connect(token: string) {
    if (this.socket?.connected) {
      console.log('WebSocket already connected');
      return;
    }

    const wsUrl = process.env.NEXT_PUBLIC_WS_URL || 'http://localhost:8080';

    this.socket = io(wsUrl, {
      auth: { token },
      transports: ['websocket', 'polling'],
      reconnection: true,
      reconnectionDelay: this.reconnectDelay,
      reconnectionAttempts: this.maxReconnectAttempts,
    });

    this.setupEventHandlers();
    console.log('WebSocket connecting to:', wsUrl);
  }

  private setupEventHandlers() {
    if (!this.socket) return;

    this.socket.on('connect', () => {
      console.log('WebSocket connected');
      this.reconnectAttempts = 0;
    });

    this.socket.on('disconnect', (reason) => {
      console.log('WebSocket disconnected:', reason);
    });

    this.socket.on('connect_error', (error) => {
      console.error('WebSocket connection error:', error);
      this.reconnectAttempts++;

      if (this.reconnectAttempts >= this.maxReconnectAttempts) {
        console.error('Max reconnection attempts reached');
        this.disconnect();
      }
    });

    // Setup listeners for all event types
    const eventTypes: WebSocketEvent[] = [
      'payment:created',
      'payment:updated',
      'payment:failed',
      'alert:created',
      'alert:updated',
      'incident:created',
      'incident:updated',
      'provider:health_changed',
      'metrics:updated',
    ];

    eventTypes.forEach((event) => {
      this.socket?.on(event, (data) => {
        console.log(`WebSocket event received: ${event}`, data);
        this.notifyListeners(event, data);
      });
    });
  }

  on(event: WebSocketEvent, callback: EventCallback) {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, new Set());
    }
    this.listeners.get(event)?.add(callback);

    // Return unsubscribe function
    return () => {
      this.listeners.get(event)?.delete(callback);
    };
  }

  off(event: WebSocketEvent, callback: EventCallback) {
    this.listeners.get(event)?.delete(callback);
  }

  private notifyListeners(event: WebSocketEvent, data: any) {
    const callbacks = this.listeners.get(event);
    if (callbacks) {
      callbacks.forEach((callback) => {
        try {
          callback(data);
        } catch (error) {
          console.error(`Error in WebSocket listener for ${event}:`, error);
        }
      });
    }
  }

  emit(event: string, data: any) {
    if (this.socket?.connected) {
      this.socket.emit(event, data);
    } else {
      console.warn('Cannot emit event, WebSocket not connected');
    }
  }

  disconnect() {
    if (this.socket) {
      this.socket.removeAllListeners();
      this.socket.disconnect();
      this.socket = null;
      this.listeners.clear();
      console.log('WebSocket disconnected');
    }
  }

  isConnected(): boolean {
    return this.socket?.connected ?? false;
  }

  getConnectionStatus(): 'connected' | 'disconnected' | 'connecting' {
    if (!this.socket) return 'disconnected';
    if (this.socket.connected) return 'connected';
    return 'connecting';
  }
}

// Singleton instance
export const wsClient = new WebSocketClient();


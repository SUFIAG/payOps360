import { useEffect, useRef } from 'react';
import { wsClient } from '@/lib/websocket/client';

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

/**
 * Hook to subscribe to WebSocket events
 * @param event - The event to listen to
 * @param callback - Function to call when event is received
 */
export function useWebSocket(event: WebSocketEvent, callback: EventCallback) {
  const callbackRef = useRef(callback);

  // Update callback ref whenever it changes
  useEffect(() => {
    callbackRef.current = callback;
  }, [callback]);

  useEffect(() => {
    // Wrapper to use the latest callback
    const handler = (data: any) => {
      callbackRef.current(data);
    };

    // Subscribe to event
    const unsubscribe = wsClient.on(event, handler);

    // Cleanup
    return () => {
      unsubscribe();
    };
  }, [event]);
}

/**
 * Hook to get WebSocket connection status
 */
export function useWebSocketStatus() {
  const [status, setStatus] = React.useState<'connected' | 'disconnected' | 'connecting'>(
    wsClient.getConnectionStatus()
  );

  useEffect(() => {
    const interval = setInterval(() => {
      setStatus(wsClient.getConnectionStatus());
    }, 1000);

    return () => clearInterval(interval);
  }, []);

  return status;
}

/**
 * Hook to emit WebSocket events
 */
export function useWebSocketEmit() {
  return (event: string, data: any) => {
    wsClient.emit(event, data);
  };
}

import React from 'react';


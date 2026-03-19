type MessageHandler = (data: unknown) => void;

const MAX_RECONNECT_DELAY = 30000;
const BASE_DELAY = 1000;

export class ConnectWebSocket {
  private ws: WebSocket | null = null;
  private url: string;
  private onMessage: MessageHandler;
  private onOpen: (() => void) | null;
  private onClose: (() => void) | null;
  private reconnectAttempt = 0;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private closed = false;

  constructor(opts: {
    url: string;
    onMessage: MessageHandler;
    onOpen?: () => void;
    onClose?: () => void;
  }) {
    this.url = opts.url;
    this.onMessage = opts.onMessage;
    this.onOpen = opts.onOpen ?? null;
    this.onClose = opts.onClose ?? null;
  }

  connect(): void {
    this.closed = false;
    this.createSocket();
  }

  send(data: unknown): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data));
    }
  }

  close(): void {
    this.closed = true;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }

  get isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }

  private createSocket(): void {
    try {
      this.ws = new WebSocket(this.url);
    } catch {
      this.scheduleReconnect();
      return;
    }

    this.ws.onopen = () => {
      this.reconnectAttempt = 0;
      this.onOpen?.();
    };

    this.ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data as string);
        this.onMessage(data);
      } catch { /* ignore */ }
    };

    this.ws.onclose = () => {
      this.onClose?.();
      if (!this.closed) {
        this.scheduleReconnect();
      }
    };

    this.ws.onerror = () => {
      // onclose will fire after onerror
    };
  }

  private scheduleReconnect(): void {
    const delay = Math.min(BASE_DELAY * Math.pow(2, this.reconnectAttempt), MAX_RECONNECT_DELAY);
    this.reconnectAttempt++;
    this.reconnectTimer = setTimeout(() => {
      if (!this.closed) {
        this.createSocket();
      }
    }, delay);
  }
}

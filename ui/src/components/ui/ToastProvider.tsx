"use client";

import {
  createContext,
  useCallback,
  useContext,
  useRef,
  useState,
} from "react";

// App-wide transient toast. One message at a time — a new toast replaces the
// current one and resets the timer. Rendered as a fixed pill above the player
// bar so it overlays any page or modal. An optional `onClick` makes the toast
// tappable (e.g. a new-notification ping that opens the inbox).
interface ToastContextValue {
  showToast: (message: string, onClick?: () => void) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast must be used within a ToastProvider");
  return ctx;
}

interface ToastState {
  message: string;
  onClick?: () => void;
}

export default function ToastProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const [toast, setToast] = useState<ToastState | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const showToast = useCallback((message: string, onClick?: () => void) => {
    setToast({ message, onClick });
    if (timer.current) clearTimeout(timer.current);
    // Tappable toasts linger a touch longer so they can actually be tapped.
    timer.current = setTimeout(() => setToast(null), onClick ? 4000 : 2000);
  }, []);

  const dismiss = () => {
    if (timer.current) clearTimeout(timer.current);
    setToast(null);
  };

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      {toast && (
        <div className="pointer-events-none fixed inset-x-0 bottom-24 z-[120] flex justify-center px-4">
          {toast.onClick ? (
            <button
              onClick={() => {
                toast.onClick?.();
                dismiss();
              }}
              className="pointer-events-auto rounded-full bg-white/90 px-4 py-2 text-sm font-semibold text-black shadow-lg transition hover:bg-white"
            >
              {toast.message}
            </button>
          ) : (
            <div className="rounded-full bg-white/90 px-4 py-2 text-sm font-semibold text-black shadow-lg">
              {toast.message}
            </div>
          )}
        </div>
      )}
    </ToastContext.Provider>
  );
}

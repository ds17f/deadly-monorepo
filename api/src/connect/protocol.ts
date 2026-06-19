// Wire-protocol constants for Connect, kept dependency-free so they can be
// imported by both the route handler and tests without pulling in the auth
// stack (which has import-time side effects).

// First protocolVersion that understands the 4005 "Connect disabled" close code.
export const CONNECT_DISABLED_PROTOCOL = 2;

/**
 * Close code to send a client when Connect is globally disabled. Legacy clients
 * (proto < 2) only treat 4003 as terminal, so they MUST get 4003 or they'll
 * retry-storm. New clients (proto >= 2) understand 4005 ("Connect disabled").
 */
export function connectDisabledCloseCode(protocolVersion: number): number {
  return protocolVersion >= CONNECT_DISABLED_PROTOCOL ? 4005 : 4003;
}

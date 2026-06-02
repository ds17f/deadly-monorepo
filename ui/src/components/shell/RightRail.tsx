"use client";

/**
 * Shell-level right pane (the third pane) with two layers.
 *
 * The app shell owns three sibling panes: library · content · right. The
 * right pane has two sources of content:
 *
 *   page layer  — route-specific (liner notes on a show, get-the-app on home).
 *                 A page declares it via <RightRailSlot>…</RightRailSlot>.
 *   player layer — transient player content (the queue, the device picker)
 *                 that the docked player pushes via useRightRailOverride().
 *
 * The player layer wins when present (Spotify-style: opening the queue swaps
 * the right column), falling back to the page layer otherwise.
 *
 * Split into separate set/read contexts so the setters are stable (no render
 * loop): writers get a memoized setState; the shell reads the resolved node.
 */

import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";

const PageNodeContext = createContext<ReactNode>(null);
const SetPageNodeContext = createContext<(n: ReactNode) => void>(() => {});
const PlayerNodeContext = createContext<ReactNode>(null);
const SetPlayerNodeContext = createContext<(n: ReactNode) => void>(() => {});

export function RightRailProvider({ children }: { children: ReactNode }) {
  const [pageNode, setPageNode] = useState<ReactNode>(null);
  const [playerNode, setPlayerNode] = useState<ReactNode>(null);
  return (
    <SetPageNodeContext.Provider value={setPageNode}>
      <SetPlayerNodeContext.Provider value={setPlayerNode}>
        <PageNodeContext.Provider value={pageNode}>
          <PlayerNodeContext.Provider value={playerNode}>
            {children}
          </PlayerNodeContext.Provider>
        </PageNodeContext.Provider>
      </SetPlayerNodeContext.Provider>
    </SetPageNodeContext.Provider>
  );
}

// Read by the shell. The player's override wins over the page's content.
export function useRightRailNode(): ReactNode {
  const page = useContext(PageNodeContext);
  const player = useContext(PlayerNodeContext);
  return player ?? page;
}

// Rendered by a page to fill the shell's right pane. Renders nothing inline.
export function RightRailSlot({ children }: { children: ReactNode }) {
  const setNode = useContext(SetPageNodeContext);
  useEffect(() => {
    setNode(children);
    return () => setNode(null);
  }, [children, setNode]);
  return null;
}

// Used by the docked player to overlay transient content (queue / devices)
// onto the right pane. Returns a stable setter; pass null to release it.
export function useRightRailOverride(): (n: ReactNode) => void {
  return useContext(SetPlayerNodeContext);
}

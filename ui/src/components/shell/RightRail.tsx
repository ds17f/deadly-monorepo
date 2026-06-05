"use client";

/**
 * Shell-level right pane (the third pane) with two layers.
 *
 * The app shell owns three sibling panes: library · content · right. The
 * right pane has two sources of content:
 *
 *   page layer  — route-specific (liner notes on a show, discovery on home).
 *                 A page declares it via <RightRailSlot>…</RightRailSlot>.
 *   player layer — transient player content (the queue, the device picker)
 *                 that the docked player pushes via useRightRailOverride().
 *
 * The player layer wins when present (Spotify-style: opening the queue swaps
 * the right column), falling back to the page layer otherwise.
 *
 * A page slot also declares a MOBILE PLACEMENT: where its content stacks
 * relative to the main content on narrow viewports. Show pages keep the
 * default ("below" — liner notes flow under the show). Home opts into
 * "above" so the discovery feed leads, matching the mobile app home.
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

export type MobilePlacement = "above" | "below";

const PageNodeContext = createContext<ReactNode>(null);
const SetPageNodeContext = createContext<(n: ReactNode) => void>(() => {});
const PlayerNodeContext = createContext<ReactNode>(null);
const SetPlayerNodeContext = createContext<(n: ReactNode) => void>(() => {});
const PlacementContext = createContext<MobilePlacement>("below");
const SetPlacementContext = createContext<(p: MobilePlacement) => void>(() => {});

export function RightRailProvider({ children }: { children: ReactNode }) {
  const [pageNode, setPageNode] = useState<ReactNode>(null);
  const [playerNode, setPlayerNode] = useState<ReactNode>(null);
  const [placement, setPlacement] = useState<MobilePlacement>("below");
  return (
    <SetPageNodeContext.Provider value={setPageNode}>
      <SetPlayerNodeContext.Provider value={setPlayerNode}>
        <SetPlacementContext.Provider value={setPlacement}>
          <PageNodeContext.Provider value={pageNode}>
            <PlayerNodeContext.Provider value={playerNode}>
              <PlacementContext.Provider value={placement}>
                {children}
              </PlacementContext.Provider>
            </PlayerNodeContext.Provider>
          </PageNodeContext.Provider>
        </SetPlacementContext.Provider>
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

// Read by the shell to decide mobile stacking order of the right pane.
export function useRightRailPlacement(): MobilePlacement {
  return useContext(PlacementContext);
}

// Rendered by a page to fill the shell's right pane. Renders nothing inline.
// `mobilePlacement` controls where it stacks on narrow viewports.
export function RightRailSlot({
  children,
  mobilePlacement = "below",
}: {
  children: ReactNode;
  mobilePlacement?: MobilePlacement;
}) {
  const setNode = useContext(SetPageNodeContext);
  const setPlacement = useContext(SetPlacementContext);
  useEffect(() => {
    setNode(children);
    setPlacement(mobilePlacement);
    return () => {
      setNode(null);
      setPlacement("below");
    };
  }, [children, mobilePlacement, setNode, setPlacement]);
  return null;
}

// Used by the docked player to overlay transient content (queue / devices)
// onto the right pane. Returns a stable setter; pass null to release it.
export function useRightRailOverride(): (n: ReactNode) => void {
  return useContext(SetPlayerNodeContext);
}

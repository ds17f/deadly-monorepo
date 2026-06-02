"use client";

/**
 * Shell-level right pane (the third pane) with a route-driven slot.
 *
 * The app shell owns three sibling panes: library · content · right. The
 * right pane's content is route-specific (liner notes on a show, get-the-app
 * on home), which a global layout can't know on its own. So a page declares
 * its right-pane content by rendering <RightRailSlot>…</RightRailSlot>, and
 * the shell renders whatever the active route registered.
 *
 * Split into two contexts so the setter is stable (no render loop): pages
 * write via SetNodeContext; the shell reads via NodeContext.
 */

import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";

const NodeContext = createContext<ReactNode>(null);
const SetNodeContext = createContext<(n: ReactNode) => void>(() => {});

export function RightRailProvider({ children }: { children: ReactNode }) {
  const [node, setNode] = useState<ReactNode>(null);
  return (
    <SetNodeContext.Provider value={setNode}>
      <NodeContext.Provider value={node}>{children}</NodeContext.Provider>
    </SetNodeContext.Provider>
  );
}

export function useRightRailNode(): ReactNode {
  return useContext(NodeContext);
}

// Rendered by a page to fill the shell's right pane. Renders nothing inline.
export function RightRailSlot({ children }: { children: ReactNode }) {
  const setNode = useContext(SetNodeContext);
  useEffect(() => {
    setNode(children);
    return () => setNode(null);
  }, [children, setNode]);
  return null;
}

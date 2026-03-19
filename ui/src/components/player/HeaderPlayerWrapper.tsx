"use client";

import dynamic from "next/dynamic";

const HeaderPlayer = dynamic(() => import("./HeaderPlayer"), { ssr: false });

export default function HeaderPlayerWrapper() {
  return <HeaderPlayer />;
}

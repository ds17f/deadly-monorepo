"use client";

import type { YearBucket } from "@/types/homepage";

export default function YearTimeline({
  yearData,
  selectedYear,
  onSelectYear,
}: {
  yearData: YearBucket[];
  selectedYear: number | null;
  onSelectYear: (year: number | null) => void;
}) {
  if (yearData.length === 0) return null;
  const maxCount = Math.max(...yearData.map((y) => y.count));

  return (
    <section className="mb-12">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-lg font-bold text-deadly-title">Shows by Year</h2>
        {selectedYear !== null && (
          <button
            onClick={() => onSelectYear(null)}
            className="text-sm text-deadly-heading hover:text-white"
          >
            Clear filter
          </button>
        )}
      </div>
      <div className="flex items-end gap-[2px] overflow-x-auto pb-2 scrollbar-none">
        {yearData.map((bucket) => {
          const heightPct = (bucket.count / maxCount) * 100;
          const isSelected = selectedYear === bucket.year;
          return (
            <button
              key={bucket.year}
              onClick={() =>
                onSelectYear(isSelected ? null : bucket.year)
              }
              className="group flex shrink-0 flex-col items-center"
              title={`${bucket.year}: ${bucket.count} shows`}
            >
              <div className="mb-1 text-[10px] text-white/0 group-hover:text-white/50">
                {bucket.count}
              </div>
              <div
                className={`w-4 rounded-t transition-colors md:w-5 ${
                  isSelected
                    ? "bg-deadly-red"
                    : "bg-white/20 hover:bg-white/40"
                }`}
                style={{ height: `${Math.max(heightPct, 4)}px`, maxHeight: "120px" }}
              />
              <div
                className={`mt-1 text-[10px] ${
                  isSelected ? "font-bold text-deadly-red" : "text-white/40"
                } ${bucket.year % 5 === 0 ? "" : "hidden md:block"}`}
              >
                {String(bucket.year).slice(2)}
              </div>
            </button>
          );
        })}
      </div>
    </section>
  );
}

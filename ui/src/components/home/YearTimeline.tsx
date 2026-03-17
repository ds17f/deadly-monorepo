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
    <section className="mb-8">
      <div className="flex items-center justify-between">
        <h3 className="mb-2 text-sm font-bold uppercase tracking-wider text-deadly-title/80">
          Shows by Year
          <span className="ml-2 inline-block h-px w-16 align-middle bg-white/20" />
        </h3>
        {selectedYear !== null && (
          <button
            onClick={() => onSelectYear(null)}
            className="text-sm text-deadly-heading hover:text-white"
          >
            Clear
          </button>
        )}
      </div>
      <div className="flex items-end gap-[2px] overflow-x-auto pb-2">
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
                } ${bucket.year % 5 === 0 || bucket.year === yearData[0].year || bucket.year === yearData[yearData.length - 1].year ? "" : "hidden md:block"}`}
              >
                {bucket.year === yearData[0].year || bucket.year === yearData[yearData.length - 1].year
                  ? String(bucket.year)
                  : String(bucket.year).slice(2)}
              </div>
            </button>
          );
        })}
      </div>
    </section>
  );
}

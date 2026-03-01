"use client";

import { SpecimenTag } from "@/shared/types/specimen";
import { cn } from "@/lib/utils";

interface SpecimenTagGroupProps {
  tags: SpecimenTag[];
  className?: string;
  variant?: "default" | "outline" | "ghost";
}

/**
 * SpecimenTagGroup
 * Renders a collection of tags grouped by dimension or as a flat list.
 * Styled with the Neo-Brutalist/Terminal theme.
 */
export function SpecimenTagGroup({
  tags,
  className,
  variant = "default"
}: SpecimenTagGroupProps) {
  if (!tags || tags.length === 0) return null;

  return (
    <div className={cn("flex flex-wrap gap-1.5", className)}>
      {tags.map((tag) => (
        <span
          key={`${tag.dimensionKey}-${tag.tagKey}`}
          className={cn(
            "inline-flex items-center px-2 py-0.5 text-[10px] font-mono tracking-tight border transition-colors",
            // Variant Styling
            variant === "default" && "bg-primary/10 border-primary/20 text-primary hover:bg-primary/20",
            variant === "outline" && "bg-transparent border-border text-muted-foreground hover:border-primary/50 hover:text-foreground",
            variant === "ghost" && "bg-black/20 border-transparent text-muted-foreground hover:text-foreground"
          )}
          style={{
            // Apply color if available in uiMeta
            color: tag.uiMeta?.color,
            borderColor: tag.uiMeta?.color ? `${tag.uiMeta.color}33` : undefined,
            backgroundColor: tag.uiMeta?.color ? `${tag.uiMeta.color}11` : undefined,
          }}
          title={tag.uiMeta?.tooltip || tag.name}
        >
          {tag.uiMeta?.icon && <span className="mr-1 opacity-80">{tag.uiMeta.icon}</span>}
          {tag.name}
        </span>
      ))}
    </div>
  );
}

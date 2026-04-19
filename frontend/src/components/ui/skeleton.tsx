import { cn } from "@/lib/utils";

function Skeleton({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn("animate-pulse rounded-[20px] bg-surface-subtle-strong", className)}
      {...props}
    />
  );
}

export { Skeleton };

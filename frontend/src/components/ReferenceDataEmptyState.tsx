import { EmptyState } from "@/components/app/EmptyState";

type ReferenceDataEmptyStateProps = {
  title: string;
  description: string;
  tone?: "default" | "warning";
};

export function ReferenceDataEmptyState({
  title,
  description,
  tone = "default",
}: ReferenceDataEmptyStateProps) {
  return <EmptyState description={description} title={title} tone={tone} />;
}

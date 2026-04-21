import { useRef, useState, type FormEvent } from "react";
import { Upload, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { MaterialSummary, MaterialVersionUploadInput } from "@/types";

type MaterialVersionUploadFormProps = {
  material: MaterialSummary;
  isUploading: boolean;
  onCancel: () => void;
  onUploadVersion: (materialId: string, input: MaterialVersionUploadInput) => Promise<unknown>;
};

export function MaterialVersionUploadForm({
  material,
  isUploading,
  onCancel,
  onUploadVersion,
}: MaterialVersionUploadFormProps) {
  const [title, setTitle] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!file) {
      return;
    }

    await onUploadVersion(material.id, {
      file,
      ...(title.trim() ? { title: title.trim() } : {}),
    });
    setTitle("");
    setFile(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
    onCancel();
  };

  return (
    <form
      className="space-y-4 rounded-2xl border border-border bg-background/70 p-4"
      onSubmit={handleSubmit}
    >
      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.3fr)]">
        <div className="space-y-2">
          <Label htmlFor={`version-upload-title-${material.id}`}>Название</Label>
          <Input
            disabled={isUploading}
            id={`version-upload-title-${material.id}`}
            placeholder={material.title}
            value={title}
            onChange={(event) => setTitle(event.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor={`version-upload-file-${material.id}`}>Файл новой версии</Label>
          <Input
            ref={fileInputRef}
            disabled={isUploading}
            id={`version-upload-file-${material.id}`}
            required
            type="file"
            onChange={(event) => setFile(event.target.files?.[0] ?? null)}
          />
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <Button disabled={!file || isUploading} size="sm" type="submit">
          <Upload className="h-4 w-4" />
          {isUploading ? "Загружаем..." : "Отправить новую версию"}
        </Button>
        <Button disabled={isUploading} size="sm" type="button" variant="secondary" onClick={onCancel}>
          <X className="h-4 w-4" />
          Отмена
        </Button>
      </div>
    </form>
  );
}
